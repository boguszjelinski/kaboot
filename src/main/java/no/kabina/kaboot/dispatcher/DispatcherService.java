/*
 * Copyright 2020 Bogusz Jelinski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.kabina.kaboot.dispatcher;

import java.io.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.cabs.CabRepository;
import no.kabina.kaboot.orders.TaxiOrder;
import no.kabina.kaboot.orders.TaxiOrderRepository;
import no.kabina.kaboot.routes.Leg;
import no.kabina.kaboot.routes.LegRepository;
import no.kabina.kaboot.routes.Route;
import no.kabina.kaboot.routes.Route.RouteStatus;
import no.kabina.kaboot.routes.RouteRepository;
import no.kabina.kaboot.stats.StatService;
import no.kabina.kaboot.stops.StopRepository;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DispatcherService {

  private final Logger logger = LoggerFactory.getLogger(DispatcherService.class);

  public static final String AVG_ORDER_ASSIGN_TIME = "avg_order_assign_time";
  public static final String AVG_ORDER_PICKUP_TIME = "avg_order_pickup_time";
  public static final String AVG_ORDER_COMPLETE_TIME = "avg_order_complete_time";
  public static final String AVG_LCM_TIME = "avg_lcm_time";
  public static final String AVG_LCM_SIZE = "avg_lcm_size";
  public static final String AVG_MODEL_SIZE = "avg_model_size";
  public static final String AVG_SOLVER_SIZE = "avg_solver_size";
  public static final String AVG_POOL_TIME = "avg_pool_time";
  public static final String AVG_POOL3_TIME = "avg_pool3_time";
  public static final String AVG_POOL4_TIME = "avg_pool4_time";
  public static final String AVG_SOLVER_TIME = "avg_solver_time";
  public static final String AVG_SHEDULER_TIME = "avg_sheduler_time";

  @Value("${kaboot.consts.max-pool4}")
  private int max4Pool; // TASK: it is not that simple, we need a model here: size, max wait, ...

  @Value("${kaboot.consts.max-pool3}")
  private int max3Pool;

  @Value("${kaboot.consts.max-non-lcm}")
  private int maxSolverSize; // how big can a solver model be; 0 = no solver at all

  @Value("${kaboot.consts.max-munkres}")
  private int maxMunkresSize; // how big can a solver model be; 0 = no solver at all

  @Value("${kaboot.consts.max-stand}")
  private int maxNumbStands;

  @Value("${kaboot.solver.cmd}")
  private String solverCmd;

  @Value("${kaboot.solver.output}")
  private String solverOutput;

  @Value("${kaboot.munkres.cmd}")
  private String munkresCmd;

  @Value("${kaboot.munkres.input}")
  private String munkresInput;

  @Value("${kaboot.munkres.output}")
  private String munkresOutput;

  @Value("${kaboot.solver.input}")
  private String solverInput;

  @Value("${kaboot.scheduler.online}")
  private boolean isOnline;

  @Value("${kaboot.scheduler.at-time-lag}")
  private int atTimeLag;

  @Value("${kaboot.scheduler.extend-margin}")
  private float extendMargin;

  @Value("${kaboot.scheduler.max-legs}")
  private long maxLegs;

  @Value("${kaboot.scheduler.max-angle}")
  private int maxAngle;

  @Value("${kaboot.extern-pool.in-use}")
  private boolean useExternPool;

  private final TaxiOrderRepository taxiOrderRepository;
  private final CabRepository cabRepository;
  private final RouteRepository routeRepository;
  private final LegRepository legRepository;
  private final StatService statSrvc;
  private final DistanceService distanceService;
  private final LcmUtil lcmUtil;
  private DynaPool dynaPool;
  private final ExternPool externPool;
  private final DynaPoolAsync asyncUtil;

  /** constructor.
   */
  public DispatcherService(TaxiOrderRepository taxiOrderRepository, CabRepository cabRepository,
                           RouteRepository routeRepository, LegRepository legRepository,
                           StatService statService, DistanceService distanceService,
                           LcmUtil lcmUtil, StopRepository stopRepository, ExternPool externPool,
                           DynaPoolAsync asyncUtil) {
    this.taxiOrderRepository = taxiOrderRepository;
    this.cabRepository = cabRepository;
    this.routeRepository = routeRepository;
    this.legRepository = legRepository;
    this.statSrvc = statService;
    this.distanceService = distanceService;
    this.lcmUtil = lcmUtil;
    this.externPool = externPool;
    this.asyncUtil = asyncUtil;
    if (distanceService.getDistances() == null) {
      distanceService.initDistance(stopRepository);
    }
  }

  void setDynaPool(DynaPool pool) { // for testing
    this.dynaPool = pool;
  }

  public void runPlan() {
    findPlan(false);
  }

  /** 1) get the data from DB.
  *   2) find a (sub)optimal plan
  *   3) write this plan to DB
  */
  //@Job(name = "Taxi scheduler", retries = 2)
  public void findPlan(boolean forceRun) {
    // UUID uuid = UUID.randomUUID()
    // TASK: to mark cabs and customers as assigned to this instance of sheduler
    // first update some statistics

    if (dynaPool == null) {
      this.dynaPool = new DynaPool(distanceService, asyncUtil, maxAngle);
    }
    if (!forceRun && !isOnline) { // userfull to run RestAPI on separate host
      logger.info("Scheduler will not be run");
      return;
    }
    logger.debug("Scheduler executed");
    updateAvgStats();
    long startSheduler = System.currentTimeMillis();

    // create demand for the solver
    TempModel tmpModel = prepareData(); // get current demand and supply available
    if (tmpModel == null) {
      return; // no suitable demand
    }
    TaxiOrder[] demand = tmpModel.getDemand();
    Cab[] supply = tmpModel.getSupply();

    if (supply.length > 0 && demand.length > 0) {
      dispatch(startSheduler, demand, supply);
    }
  }

  private void dispatch(long startSheduler, TaxiOrder[] demand, Cab[] supply) {

    logger.info("Start dispatching demand:{} supply:{}", demand.length, supply.length);
    // try to assign to existing routes
    int lenBefore = demand.length;
    demand = findMatchingRoutes(demand);
    int lenAfter = demand.length;
    if (lenBefore != lenAfter) {
      logger.info("Route matcher found allocated {} requests", lenBefore - lenAfter);
    }
    PoolElement[] pl;
    if (useExternPool) {
      externPool.setDispatcherService(this);
      pl = externPool.findPool(demand, supply, true);
    } else {
      pl = generatePool(demand, supply, true);
    }
    if (pl != null && pl.length > 0) {
      demand = PoolUtil.removePoolFromDemand(pl, demand); // findFirstLegInPoolOrLone
      logger.info("Demand after pooling: {}", demand.length);
      supply = PoolUtil.trimSupply(supply);
      // supply vector may have nulls (= cabs allocated by pool finder)
    }
    logger.info("Demand after pooling: {}", demand.length);
    // now build a balanced cost matrix for solver
    int[][] cost = lcmUtil.calculateCost(munkresInput, solverOutput, demand, supply);
    statSrvc.updateMaxAndAvgStats("model_size", Math.max(demand.length, supply.length));
    logger.info("Before LCM: demand={}, supply={}", demand.length, supply.length);
    // n*m model
    // IF min(n,m) > max_non_lcm THEN reduce to max_non_lcm with LCM (max_non_lcm is max_munkres, although one dimension of munkres can be bigger)
    // then
    // IF max(n,m) > max_munkres THEN reduce the bigger side to max_munkres with MAXMIN (previously called GCM)
    // matrix max_non_lcm * max_munkres is sent to munkres
    if (demand.length > maxSolverSize && supply.length > maxSolverSize) {
      // too big to send to solver, it has to be cut by LCM
      // both sides have to be bigger, if one is smaller then
      // we will just MAXMIN on the bigger side
      TempModel tempModel = runLcm(supply, demand, cost);
      demand = tempModel.getDemand();
      supply = tempModel.getSupply();
      // to be sent to solver
      logger.info("After LCM: demand={}, supply={}", demand.length, supply.length);
      cost = lcmUtil.calculateCost(solverInput, solverOutput, demand, supply);
    }
    runSolver(supply, demand, cost);
    statSrvc.updateMaxAndAvgTime("sheduler_time", startSheduler);
  }

  /**
   * Takes values stored in internal lists and stores in DB.
   */
  private void updateAvgStats() {
    statSrvc.updateIntVal(AVG_ORDER_ASSIGN_TIME, statSrvc.countAverage(AVG_ORDER_ASSIGN_TIME));
    // TASK: in the future move it where it is counted once after simulation
    statSrvc.updateIntVal(AVG_ORDER_PICKUP_TIME, statSrvc.countAverage(AVG_ORDER_PICKUP_TIME));
    statSrvc.updateIntVal(AVG_ORDER_COMPLETE_TIME, statSrvc.countAverage(AVG_ORDER_COMPLETE_TIME));
    statSrvc.updateIntVal(AVG_POOL_TIME, statSrvc.countAverage(AVG_POOL_TIME));
    statSrvc.updateIntVal(AVG_SOLVER_TIME, statSrvc.countAverage(AVG_SOLVER_TIME));
    statSrvc.updateIntVal(AVG_SHEDULER_TIME, statSrvc.countAverage(AVG_SHEDULER_TIME));
    statSrvc.updateIntVal(AVG_LCM_TIME, statSrvc.countAverage(AVG_LCM_TIME));
    statSrvc.updateIntVal(AVG_LCM_SIZE, statSrvc.countAverage(AVG_LCM_SIZE));
    statSrvc.updateIntVal(AVG_MODEL_SIZE, statSrvc.countAverage(AVG_MODEL_SIZE));
    statSrvc.updateIntVal(AVG_SOLVER_SIZE, statSrvc.countAverage(AVG_SOLVER_SIZE));
    statSrvc.updateIntVal(AVG_POOL3_TIME, statSrvc.countAverage(AVG_POOL3_TIME));
    statSrvc.updateIntVal(AVG_POOL4_TIME, statSrvc.countAverage(AVG_POOL4_TIME));
  }

  /**
   * run linear solver

   * @param tempSupply cabs
   * @param tempDemand orders
   * @param cost matrix
   */
  public void runSolver(Cab[] tempSupply, TaxiOrder[] tempDemand, int[][] cost) {
    // we assume that one of the dimension is reduced by LCM to max_non_lcm
    // and max_non_lcm < max_munkres
    if (tempSupply.length > maxMunkresSize) {
      // some cabs will not get passengers, they have to wait for new ones
      tempSupply = GcmUtil.reduceSupply(cost, tempSupply, maxMunkresSize);
      // recalculate cost matrix again
      // it writes input file for solver
      cost = lcmUtil.calculateCost(munkresInput, munkresOutput, tempDemand, tempSupply);
    }
    if (tempDemand.length > maxMunkresSize) {
      tempDemand = GcmUtil.reduceDemand(cost, tempDemand, maxMunkresSize);
      cost = lcmUtil.calculateCost(munkresInput, munkresOutput, tempDemand, tempSupply);
    }
    statSrvc.updateMaxAndAvgStats("solver_size", Math.max(tempDemand.length, tempSupply.length));
    logger.info("Runnnig solver: demand={}, supply={}", tempDemand.length, tempSupply.length);

    runExternalMunkres();
    // read results from a file
    int[] x = readMunkresResult(tempDemand.length * tempSupply.length, munkresOutput);
    logger.info("Read vector from solver, length: {}", x.length);
    if (x.length != tempDemand.length * tempSupply.length) {
      logger.warn("Munkres returned wrong data set");
      // TASK: LCM should be called here !!!
    } else {
      int assgnd = assignCustomers(x, cost, tempDemand, tempSupply);
      logger.info("Customers assigned by solver: {}", assgnd);
    }
    // now we could check if a customer in pool has got a cab,
    // if not - the other one should get a chance
  }

  public void runExternalSolver() {
    try {
      // TASK: rm out file first
      Process p = Runtime.getRuntime().exec(solverCmd);
      long startSolver = System.currentTimeMillis();
      p.waitFor();
      statSrvc.updateMaxAndAvgTime("solver_time", startSolver);
    } catch (IOException e) {
      logger.warn("IOException while running solver: {}", e.getMessage());
    } catch (Exception e) {
      logger.warn("Exception while running solver: {}", e.getMessage());
    }
  }

  public void runExternalMunkres() {
    try {
      Process p = Runtime.getRuntime().exec(munkresCmd);
      p.waitFor();
    } catch (IOException e) {
      logger.warn("IOException while running munkres: {}", e.getMessage());
    } catch (Exception e) {
      logger.warn("Exception while running munkres: {}", e.getMessage());
    }
  }

  /**
   *  read solver output from a file - generated by a Python script.

   * @param n size of cost the model
   * @return vector with binary data - assignments
   */
  public int[] readSolversResult(int n) {
    int[] x = new int[n * n];
    try (BufferedReader reader = new BufferedReader(new FileReader(solverOutput))) {
      String line;
      for (int i = 0; i < n * n; i++) {
        line = reader.readLine();
        if (line == null) {
          logger.warn("wrong output from solver");
          return new int[0];
        }
        x[i] = Integer.parseInt(line);
      }
    } catch (IOException e) {
      logger.warn("missing output from solver");
      return new int[0];
    }
    return x;
  }

  public static int[] readMunkresResult(int n, String munkresOutputFile) {
    int[] x = new int[n];
    try (BufferedReader reader = new BufferedReader(new FileReader(munkresOutputFile))) {
      String line;
      for (int i = 0; i < n; i++) {
        line = reader.readLine();
        if (line == null) {
          return new int[0];
        }
        x[i] = Integer.parseInt(line);
      }
    } catch (IOException e) {
      return new int[0];
    }
    return x;
  }

  public static void writeMunkresInput(int[][] cost, int numbDemand, int numbSupply, String munkresInput) {
    try (FileWriter fr = new FileWriter(new File(munkresInput))) {
      fr.write(numbDemand + " " + numbSupply + "\n");
      for (int c = 0; c < numbSupply; c++) {
        for (int d = 0; d < numbDemand; d++) {
          fr.write(cost[c][d] + ", ");
        }
        fr.write("\n");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   *  assignes orders to existing routes.

   * @param demand to be checked
   * @return demand that was not matched, which have to allocated to new routes
   */
  private TaxiOrder[] findMatchingRoutes(TaxiOrder[] demand) {
    List<Leg> legs = legRepository.findByStatusOrderByRouteAscPlaceAsc(RouteStatus.ASSIGNED);
    if (legs == null || legs.isEmpty() || demand == null || demand.length == 0) {
      return demand;
    }
    logger.debug("findMatchingRoutes START, orders count={} legs count={}",
                                                                    demand.length, legs.size());
    List<TaxiOrder> ret = new ArrayList<>();
    for (TaxiOrder taxiOrder : demand) {
      if (tryToExtendRoute(taxiOrder, legs) == -1) { // if not matched or extended
        ret.add(taxiOrder);
      }
    }
    logger.debug("findMatchingRoutes STOP, rest orders count={}", ret.size());
    return ret.toArray(new TaxiOrder[0]);
  }

  private Cab getCab(Long id) {
    Optional<Route> r = routeRepository.findById(id);
    if (r.isPresent()) {
      Cab cab = r.get().getCab();
      if (cab == null) {
        logger.info("Cab is still null in Route {}", id);
      }
      return cab;
    }
    return null;
  }

  private class LegIndicesWithDistance {
    LegIndicesWithDistance(int idxFrom, int idxTo, int distance) {
      this.idxFrom = idxFrom;
      this.idxTo = idxTo;
      this.distance = distance;
    }
    public int idxFrom;
    public int idxTo;
    public int distance;
  }

  /**
   *  assignes orders to existing routes.

   * @param demand to be checked
   * @param legs to be checked
   * @return from
   */
  public int tryToExtendRoute(TaxiOrder demand, List<Leg> legs) {
    List<LegIndicesWithDistance> feasible = new ArrayList<>();
    int i = 1;
    int initialDistance = 0;
    while (i < legs.size()) {
      // not from 0 as each leg we are looking for must have a predecessor
      // routes from the same stand which have NOT started will surely be seen by passengers,
      // they can get aboard
      // TASK: MAX WAIT check
      Leg leg = legs.get(i);
      long legCount = legs.stream().filter(
                            l -> leg.getRoute().getId().equals(l.getRoute().getId())).count();
      boolean notTooLong = legCount <= maxLegs;
      if (leg.getStatus() == RouteStatus.ASSIGNED || leg.getStatus() == RouteStatus.ACCEPTED) {
        initialDistance += leg.getDistance();
      }
      if (demand.fromStand != leg.getToStand() // direct hit in the next leg
          // previous leg is from the same route
          && legs.get(i - 1).getRoute().getId().equals(leg.getRoute().getId())
          // the previous leg cannot be completed TASK!! in the future consider other statuses here
          && legs.get(i - 1).getStatus() != RouteStatus.COMPLETED
          && (demand.fromStand == leg.getFromStand() // direct hit
            || (notTooLong
                  && distanceService.distance[leg.getFromStand()][demand.fromStand]
                    + distanceService.distance[demand.fromStand][leg.getToStand()]
                     < leg.getDistance() * extendMargin
                  && PoolUtil.bearingDiff(distanceService.bearing[leg.getFromStand()],
                                           distanceService.bearing[demand.fromStand]) < maxAngle
                  && PoolUtil.bearingDiff(distanceService.bearing[demand.fromStand],
                                           distanceService.bearing[leg.getToStand()]) < maxAngle
                  ) // 5% TASK - global config, wait at stop?
          )
      // we want the previous leg to be active
      // to give some time for both parties to get the assignment
      ) {
        // OK, so we found the first 'pickup' leg, either direct hit or can be extended
        boolean toFound = false;
        int distanceInPool = 0;
        // we have found "from", now let's find "to"
        int k = i; // "to might be in the same leg as "from", we have to start from 'i'
        for (; k < legs.size(); k++) {
          if (i != k) { // 'i' countet already
            distanceInPool += legs.get(k).getDistance();
          }
          if (!legs.get(k).getRoute().getId().equals(leg.getRoute().getId())) {
            initialDistance = 0; // new route
            // won't find; this leg is the first leg in the next route and won't be checked as i++
            break;
          }
          if (demand.toStand == legs.get(k).getToStand()) { // direct hit
            toFound = true;
            break;
          }
          if (notTooLong
              && distanceService.distance[legs.get(k).getFromStand()][demand.toStand]
                                + distanceService.distance[demand.toStand][legs.get(k).getToStand()]
                    < legs.get(k).getDistance() * extendMargin
              && PoolUtil.bearingDiff(distanceService.bearing[legs.get(k).getFromStand()],
                                       distanceService.bearing[demand.toStand]) < maxAngle
              && PoolUtil.bearingDiff(distanceService.bearing[demand.toStand],
                                       distanceService.bearing[legs.get(k).getToStand()]) < maxAngle
              ) {
            // passenger is dropped before "getToStand", but the whole distance is counted above
            distanceInPool -= distanceService.distance[demand.toStand][legs.get(k).getToStand()];
            toFound = true;
            break;
          }
        }
        if (toFound && demand.getMaxWait() >= initialDistance
            // TASK: maybe distance*maxloss is a performance bug,
            // distanceWithLoss should be stored and used
            && (1.0 + demand.getMaxLoss() / 100.0) * demand.getDistance() >= distanceInPool) {
          feasible.add(new LegIndicesWithDistance(i, k, initialDistance + distanceInPool));
        }
        i = k;
      }
      i++;
    }
    // TASK: sjekk if demand.from == last leg.toStand - this might be feasible
    if (feasible.isEmpty()) {
      return -1;
    }
    feasible.sort(
            (LegIndicesWithDistance t1, LegIndicesWithDistance t2) -> t1.distance - t2.distance);
    // TASK: MAX LOSS check
    modifyLeg(demand, legs, feasible.get(0));
    return feasible.get(0).idxFrom; // first has shortest distance
  }

  private void modifyLeg(TaxiOrder demand, List<Leg> legs, LegIndicesWithDistance idxs) {
    // pickup phase
    Leg fromLeg = legs.get(idxs.idxFrom);
    Route route = fromLeg.getRoute();
    logger.info("Order {} assigned to existing route: {}", demand.getId(), route.getId());
    Cab cab;
    try {
      cab = route.getCab();
    } catch (Exception e) {
      logger.info("Rereading Cab from Route {}", route.getId());
      cab = getCab(route.getId());
    }
    if (demand.fromStand == fromLeg.getFromStand()) { // direct hit, we don't modify that leg
      // TASK: eta should be calculated
      assignOrder(fromLeg, demand, cab, route, 0, "matchRoute IN");
    } else { // one leg more
      int place = fromLeg.getPlace() + 1;
      logger.info("new, extended IN leg, route {}, place {}", route.getId(), place);
      Leg newLeg = new Leg(demand.fromStand,
                           fromLeg.getToStand(),
                           place++,
                           Route.RouteStatus.ASSIGNED,
                           distanceService.distance[demand.fromStand][fromLeg.getToStand()]);
      newLeg.setRoute(route);
      legRepository.save(newLeg);
      // legs will be used for another order, the list must be updated
      legs.add(idxs.idxFrom + 1, newLeg);
      idxs.idxTo++;
      assignOrder(newLeg, demand, cab, route, 0, "extendRoute IN");
      // modify existing IN leg so that it goes to a new waypoint in-between
      fromLeg.setToStand(demand.fromStand);
      fromLeg.setDistance(distanceService.getDistances()[fromLeg.getFromStand()][demand.fromStand]);
      // 'place' will stay unchanged
      legRepository.save(fromLeg);

      // now "place" in route for next legs has to be incremented
      int i = idxs.idxFrom + 2; // +1+1 as we added one leg and we have to update the rest
      while (i < legs.size() && legs.get(i).getRoute().getId().equals(route.getId())) {
        logger.debug("IN: increment place of leg {} route {}, from {} to {}",
                    legs.get(i).getId(), route.getId(), legs.get(i).getPlace(), place);
        legs.get(i).setPlace(place++);
        legRepository.save(legs.get(i));
        i++;
      }
    }
    // drop-off phase
    Leg toLeg = legs.get(idxs.idxTo);
    if (demand.toStand != toLeg.getToStand()) { // one leg more, ignore situation with ==
      int place = toLeg.getPlace() + 1;
      logger.info("new, extended OUT leg, route {}, place {}", route.getId(), place);
      Leg newLeg = new Leg(demand.toStand,
              toLeg.getToStand(), // stop where the original leg ended
              place++,
              Route.RouteStatus.ASSIGNED,
              distanceService.distance[demand.toStand][toLeg.getToStand()]);
      newLeg.setRoute(route);
      legRepository.save(newLeg);
      legs.add(idxs.idxTo + 1, newLeg);
      // modify existing leg so that it goes to a new waypoint in-between
      toLeg.setToStand(demand.toStand);
      toLeg.setDistance(distanceService.getDistances()[toLeg.getFromStand()][demand.toStand]);
      // place will stay unchanged
      legRepository.save(toLeg);
      // now "place" in route for next legs has to be incremented
      int i = idxs.idxTo + 2;
      while (i < legs.size() && legs.get(i).getRoute().getId().equals(route.getId())) {
        logger.debug("OUT: increment place of leg {} route {}, from {} to {}",
                legs.get(i).getId(), route.getId(), legs.get(i).getPlace(), place);
        legs.get(i).setPlace(place++);
        legRepository.save(legs.get(i));
        i++;
      }
    }
  }

  private TempModel prepareData() {
    // read demand for the solver from DB
    LocalDateTime plusMins = LocalDateTime.now().plusMinutes(atTimeLag);

    TaxiOrder[] tempDemand = taxiOrderRepository.findByStatusAndTime(
                            TaxiOrder.OrderStatus.RECEIVED, plusMins).toArray(new TaxiOrder[0]);
    tempDemand = expireRequests(tempDemand); // mark "refused" if waited too long

    if (tempDemand.length == 0) {
      logger.info("No suitable demand");
      return null; // don't solve anything
    }
    // collect available cabs from DB
    // TASK: not only FREE, but these which will end up close enough
    Cab[] tempSupply = cabRepository.findByStatus(Cab.CabStatus.FREE).toArray(new Cab[0]);
    if (tempSupply.length == 0) {
      logger.info("No cabs available");
      return null; // don't solve anything
    }
    logger.info("Initial count of demand={}, supply={}", tempDemand.length, tempSupply.length);

    tempDemand = lcmUtil.getRidOfDistantCustomers(tempDemand, tempSupply);
    if (tempDemand.length == 0) {
      logger.info("No suitable demand, too distant");
      return null; // don't solve anything
    }
    tempSupply = lcmUtil.getRidOfDistantCabs(tempDemand, tempSupply);
    if (tempSupply.length == 0) {
      logger.info("No cabs available, too distant");
      return null; // don't solve anything
    }
    return new TempModel(tempSupply, tempDemand);
  }

  /**
   * check if pools are possible - with 4, 3 and 2 passengers.
   * Some customers have very strict expectations,
   * do not want to lose time and share their trip with any.

   * @param demand orders
   * @param supply cabs
   * @return pool elements
   */
  public PoolElement[] generatePool(TaxiOrder[] demand, Cab[] supply, boolean updateDb) {
    final long startPool = System.currentTimeMillis();
    logger.debug("generatePool demand:{} supply:{}", demand.length, supply.length);
    if (demand.length < 2) {
      // you can't have a pool with 1 order
      return new PoolElement[0];
    }
    // try to put 4 passengers into one cab
    PoolElement[] pl4 = null;

    if (demand.length < max4Pool) { // pool4 takes a lot of time, it cannot analyze big data sets
      final long startPool4 = System.currentTimeMillis();
      // four passengers: size^4 combinations (full search)
      pl4 = findPool(demand, supply, 4, updateDb);
      statSrvc.updateMaxAndAvgTime("pool4_time", startPool4);
      logger.debug("findPool4 returned pool.length:{}", pl4.length);
    }
    PoolElement[] ret = getPoolWith3and2(demand, supply, pl4, updateDb);
    statSrvc.updateMaxAndAvgTime("pool_time", startPool);
    logger.info("Pool size: {}", ret == null ? 0 : ret.length);
    return ret;
  }

  /** DynaPool v2.

   * @param dem orders
   * @param supply cabs
   * @param inPool how many passengers
   * @param updateDb if not tests
   * @return array
   */
  public PoolElement[] findPool(TaxiOrder[] dem, Cab[] supply, int inPool, boolean updateDb) {
    if (inPool > DynaPool.MAX_IN_POOL) {
      // TASK log
      return new PoolElement[0];
    }
    dynaPool.setDemand(dem);
    dynaPool.initMem(inPool);
    dynaPool.dive(0, inPool, 1);
    List<PoolElement> poolList = dynaPool.getList(inPool);
    logger.debug("Pool{} internal list size: {}", inPool, poolList.size());
    return removeDuplicatesV2(poolList.toArray(new PoolElement[0]), supply, inPool, updateDb);
  }

  /** sort pools by cost and remove duplicates.

   * @param arr  pools
   * @param supply cabs
   * @param inPool how many passengers
   * @param updateDb if not tests
   * @return array
   */
  public PoolElement[] removeDuplicatesV2(PoolElement[] arr, Cab[] supply, int inPool,
                                          boolean updateDb) {
    if (arr == null || supply == null || supply.length == 0) {
      return new PoolElement[0];
    }
    Arrays.sort(arr);
    // removing duplicates
    List<PoolElement> ret = new ArrayList<>();
    int tempCount = 0;
    int tempCount2 = 0;
    for (int i = 0; i < arr.length; i++) {
      if (arr[i].getCost() == -1) { // this -1 marker is set below
        continue;
      }
      // find nearest cab to first pickup and check if WAIT and LOSS constraints met - allocate
      int cabIdx = PoolUtil.findNearestCab(distanceService, supply, arr[i].getCust()[0]); // LCM
      if (cabIdx == -1) { // no more cabs
        markPoolsAsDead(arr, i);
        break;
      }
      tempCount++;
      Cab cab = supply[cabIdx];
      int distCab = distanceService.distance[cab.getLocation()]
                                            [arr[i].getCust()[0].getFromStand()];
      if (distCab == 0 // constraints inside pool are checked while "diving"
              || PoolUtil.constraintsMet(distanceService, arr[i], distCab)) {
        if (distCab != 0) {
          tempCount2++; // if constraintMet gave true?
        }
        ret.add(arr[i]);
        // allocate
        assignAndRemove(arr, supply, inPool, updateDb, i, cabIdx, cab);
      } else { // constraints not met, mark as unusable
        arr[i].setCost(-1);
      }
    }
    // just collect non-duplicated pool plans
    logger.debug("tempCount: {} tempCount2: {}", tempCount, tempCount2);
    return ret.toArray(new PoolElement[0]);
  }

  private void markPoolsAsDead(PoolElement[] arr, int i) {
    for (int j = i + 1; j < arr.length; j++) {
      arr[j].setCost(-1);
    }
  }

  private void assignAndRemove(PoolElement[] arr, Cab[] supply, int inPool, boolean updateDb,
                               int i, int cabIdx, Cab cab) {
    if (updateDb) {
      assignPoolToCab(cab, arr[i]);
    }
    // remove the cab from list so that it cannot be allocated twice
    supply[cabIdx] = null;
    // remove any further duplicates
    for (int j = i + 1; j < arr.length; j++) {
      if (arr[j].getCost() != -1 // not invalidated; this check is for performance reasons
              && PoolUtil.isFoundV2(arr, i, j, inPool)) {
        arr[j].setCost(-1); // duplicated; we remove an element with greater costs
        // (list is pre-sorted)
      }
    }
  }

  /** assign pool.

   * @param cab cab
   * @param pool pool
   */
  public void assignPoolToCab(Cab cab, PoolElement pool) {
    // update CAB
    int eta = 0; // expected time of arrival
    cab.setStatus(Cab.CabStatus.ASSIGNED);
    cabRepository.save(cab);
    Route route = new Route(Route.RouteStatus.ASSIGNED);
    route.setCab(cab);
    routeRepository.save(route);
    Leg leg;
    int legId = 0;
    TaxiOrder order = pool.getCust()[0];
    if (cab.getLocation() != order.fromStand) { // cab has to move to pickup the first customer
      eta = distanceService.distance[cab.getLocation()][order.fromStand];
      leg = new Leg(cab.getLocation(), order.fromStand, legId++, Route.RouteStatus.ASSIGNED, eta);
      leg.setRoute(route);
      legRepository.save(leg);
      statSrvc.addToIntVal("total_pickup_distance", Math.abs(cab.getLocation() - order.fromStand));
    }
    // legs & routes are assigned to customers in Pool
    // if not assigned to a Pool we have to create a single-task route here
    assignOrdersAndSaveLegsV2(cab, route, legId, pool, eta);
  }


  private PoolElement[] getPoolWith3and2(TaxiOrder[] demand, Cab[] supply, PoolElement[] pl4,
                                         boolean updateDb) {
    PoolElement[] ret;
    TaxiOrder[] demand3 = PoolUtil.findCustomersWithoutPoolV2(pl4, demand);
    // TASK: [i]=null would be better than such routines
    if (demand3 != null && demand3.length > 0) { // there is still an opportunity
      PoolElement[] pl3;
      if (demand3.length < max3Pool) { // not too big for three customers, let's find out!
        pl3 = getPoolWith3(supply, updateDb, demand3);
        if (pl3 == null || pl3.length == 0) {
          pl3 = pl4;
        } else {
          demand3 = PoolUtil.findCustomersWithoutPoolV2(pl3, demand3); // for getPoolWith2
          pl3 = ArrayUtils.addAll(pl3, pl4); // merge both
        }
      } else {
        pl3 = pl4;
      }
      // with 2 passengers (this runs fast and no max is needed
      ret = getPoolWith2(demand3, supply, pl3, updateDb);
      logger.debug("Pool2: used demand={} pool size={}",
                          demand3.length, ret == null ? 0 : ret.length);
    } else {
      ret = pl4;
    }
    return ret;
  }

  private PoolElement[] getPoolWith3(Cab[] supply, boolean updateDb, TaxiOrder[] demand3) {
    PoolElement[] pl3;
    final long startPool3 = System.currentTimeMillis();
    pl3 = findPool(demand3, supply, 3, updateDb);
    logger.debug("Pool3: used demand={} pool size={}",
                                                  demand3.length, pl3 == null ? 0 : pl3.length);
    statSrvc.updateMaxAndAvgTime("pool3_time", startPool3);
    return pl3;
  }

  private PoolElement[] getPoolWith2(TaxiOrder[] demand3, Cab[] supply, PoolElement[] pl3,
                                     boolean updateDb) {
    PoolElement[] ret;
    TaxiOrder[] demand2 = PoolUtil.findCustomersWithoutPoolV2(pl3, demand3);
    if (demand2 != null && demand2.length > 0) {
      PoolElement[] pl2 = findPool(demand2, supply, 2, updateDb);
      if (pl2.length == 0) {
        ret = pl3;
      } else {
        ret = ArrayUtils.addAll(pl2, pl3);
      }
    } else {
      ret = pl3;
    }
    return ret;
  }

  /** LCM.

   * @param supply cabs
   * @param demand orders
   * @param cost distances
   * @return  returns demand and supply for the solver, not assigned by LCM
   */
  public TempModel runLcm(Cab[] supply, TaxiOrder[] demand, int[][] cost) {

    final long startLcm = System.currentTimeMillis();

    statSrvc.updateMaxAndAvgStats("lcm_size", cost.length);
    LcmOutput out = LcmUtil.lcm(cost,
                        Math.min(demand.length, supply.length) - maxSolverSize);
    if (out == null) {
      return new TempModel(supply, demand);
    }

    List<LcmPair> pairs = out.getPairs();
    logger.info("LCM pairs: {}", pairs.size());

    statSrvc.incrementIntVal("total_lcm_used");
    long endLcm = System.currentTimeMillis();
    int totalLcmtime = (int) ((endLcm - startLcm) / 1000F);

    statSrvc.updateMaxAndAvgStats("lcm_time", totalLcmtime);

    if (pairs.isEmpty()) {
      logger.warn("critical -> a big model but LCM hasn't helped");
      return new TempModel(supply, demand);
    }
    logger.info("LCM n_pairs={}", pairs.size());
    // go thru LCM response (which are indexes in tempDemand and tempSupply)
    int sum = 0;
    for (LcmPair pair : pairs) {
      sum += assignCustomerToCab(demand[pair.getClnt()], supply[pair.getCab()]);
    }
    logger.info("Number of customers assigned by LCM: {}", sum);
    if (out.getMinVal() == LcmUtil.BIG_COST) {
      // no input for the solver; probably only when MAX_SOLVER_SIZE=0
      logger.info("No input for solver, out.minVal == LcmUtil.bigCost"); // should we return here?
      // TASK it does not sound reasonable to run solver under such circumstances
    }
    // also produce input for the solver
    return new TempModel(LcmUtil.supplyForSolver(pairs, supply),
                         LcmUtil.demandForSolver(pairs, demand));
  }

  /** assign.

   * @param x return from solver
   * @param cost solver's cost matrix
   * @param tmpDemand orders
   * @param tmpSupply cabs
   * @return number of assigned customers
   */
  // solver returns a very simple output,
  // it has to be compared with data which helped create its input
  private int assignCustomers(int[] x, int[][] cost, TaxiOrder[] tmpDemand, Cab[] tmpSupply) {
    int count = 0;

    for (int s = 0; s < tmpSupply.length; s++) {
      for (int c = 0; c < tmpDemand.length; c++) {
        if (x[tmpDemand.length * s + c] == 1) {
          // not a fake assignment (to balance the model)
          count += assignCustomerToCab(tmpDemand[c], tmpSupply[s]);
        }
      }
    }
    return count;
  }

  /**
   * save this particular assignment to the DB (cab, route, leg, taxi_order).

   * @param order customer request
   * @param cab cab
   * @return  number of assigned customers
   */
  // TASK: it isn't transactional
  private int assignCustomerToCab(TaxiOrder order, Cab cab) {
    // update CAB
    int eta = 0; // expected time of arrival
    cab.setStatus(Cab.CabStatus.ASSIGNED);
    cabRepository.save(cab);
    Route route = new Route(Route.RouteStatus.ASSIGNED);
    route.setCab(cab);
    routeRepository.save(route);
    Leg leg;
    int legId = 0;
    if (cab.getLocation() != order.fromStand) { // cab has to move to pickup the first customer
      eta = distanceService.distance[cab.getLocation()][order.fromStand];
      leg = new Leg(cab.getLocation(), order.fromStand, legId++, Route.RouteStatus.ASSIGNED, eta);
      leg.setRoute(route);
      legRepository.save(leg);
      statSrvc.addToIntVal("total_pickup_distance", Math.abs(cab.getLocation() - order.fromStand));
    }
    leg = new Leg(order.fromStand, order.toStand, legId, Route.RouteStatus.ASSIGNED,
                  distanceService.distance[order.fromStand][order.fromStand]);
    leg = saveLeg(leg, route);
    order.setInPool(false);
    assignOrder(leg, order, cab, route, eta, "assignCustomerToCab");
    return 1; // one customer
  }

  private void logPool2(Cab cab, Route route, PoolElement e) {
    StringBuilder stops = new StringBuilder();
    for (int i = 0; i < e.getNumbOfCust() * 2; i++) {
      if (e.custActions[i] == 'i') {
        stops.append(e.getCust()[i].getId()).append("(from=").append(e.getCust()[i].getFromStand())
                .append("), ");
      } else {
        stops.append(e.getCust()[i].getId()).append("(to=").append(e.getCust()[i].getFromStand())
                .append("), ");
      }
    }
    logger.info("Pool legs: cab_id={}, route_id={}, order_id(from/to)={}",
                cab.getId(), route.getId(), stops);
  }

  private void assignOrdersAndSaveLegsV2(Cab cab, Route route, int legId, PoolElement e, int eta) {
    logPool2(cab, route, e);
    Leg leg;
    int c = 0;
    for (; c < e.getNumbOfCust() + e.getNumbOfCust() - 1; c++) {
      leg = null;
      int stand1 = e.custActions[c] == 'i' ? e.getCust()[c].fromStand : e.getCust()[c].toStand;
      int stand2 = e.custActions[c + 1] == 'i'
                                      ? e.getCust()[c + 1].fromStand : e.getCust()[c + 1].toStand;
      if (stand1 != stand2) { // there is movement
        leg = new Leg(stand1, stand2, legId++, Route.RouteStatus.ASSIGNED,
                      distanceService.distance[stand1][stand2]);
        saveLeg(leg, route);
      }
      if (e.custActions[c] == 'i') {
        e.getCust()[c].setInPool(true);
        assignOrder(leg, e.getCust()[c], cab, route, eta, "assignOrdersAndSaveLegs1");
      }
      if (stand1 != stand2) {
        eta += distanceService.distance[stand1][stand2];
      }
    }
  }

  private Leg saveLeg(Leg l, Route r) {
    if (l != null) {
      l.setRoute(r);
      return legRepository.save(l);
    }
    return null;
  }

  private void assignOrder(Leg l, TaxiOrder o, Cab c, Route r, int eta, String calledBy) {
    if (c == null || c.getId() == null) {
      logger.warn("Assigning order_id={}, cab is null", o.id);
    }
    // check if it is not cancelled
    Optional<TaxiOrder> curr = taxiOrderRepository.findById(o.id);
    if (curr.isEmpty()) {
      logger.warn("Attempt to assign a non existing order");
      return;
    }
    if (curr.get().getStatus() == TaxiOrder.OrderStatus.CANCELLED) {
      // customer cancelled while sheduler was working
      return; // TASK: the whole route should be adjusted, a stand maybe ommited
    }
    // TASK: maybe 'curr' should be used later on, not 'o', to imbrace some more changes
    if (l != null) {
      o.setLeg(l);
    }
    o.setEta(eta);
    Duration duration = Duration.between(o.getReceived(), LocalDateTime.now());
    statSrvc.addAverageElement(AVG_ORDER_ASSIGN_TIME, duration.getSeconds());

    o.setStatus(TaxiOrder.OrderStatus.ASSIGNED);
    o.setCab(c);
    o.setRoute(r);
    // TASK: order.eta to be set
    logger.info("Assigning order_id={} to cab {}, route {}, routine {}",
                o.id, o.getCab().getId(), o.getRoute().getId(), calledBy);
    taxiOrderRepository.save(o);
  }

  /** refusing orders that have waited too long due to lack of available cabs.

   * @param demand orders
   * @return array of orders that are still valid
   */
  private TaxiOrder[] expireRequests(TaxiOrder[] demand) {
    List<TaxiOrder> newDemand = new ArrayList<>();

    LocalDateTime now = LocalDateTime.now();
    for (TaxiOrder o : demand) {
      long minutesRcvd = Duration.between(o.getReceived(), now).getSeconds() / 60;
      long minutesAt = 0;
      if (o.getAtTime() != null) {
        minutesAt = Duration.between(o.getAtTime(), now).getSeconds() / 60;
      }
      if ((o.getAtTime() == null && minutesRcvd > o.getMaxWait())
          || (o.getAtTime() != null && minutesAt > o.getMaxWait())) {
        // TASK: maybe scheduler should have its own, global MAX WAIT
        logger.info("order_id={} refused, max wait exceeded", o.id);
        o.setStatus(TaxiOrder.OrderStatus.REFUSED);
        taxiOrderRepository.save(o);
      } else {
        newDemand.add(o);
      }
    }
    return newDemand.toArray(new TaxiOrder[0]);
  }
}
