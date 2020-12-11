package no.kabina.kaboot.scheduler;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.cabs.CabRepository;
import no.kabina.kaboot.orders.TaxiOrder;
import no.kabina.kaboot.orders.TaxiOrderRepository;
import no.kabina.kaboot.routes.Leg;
import no.kabina.kaboot.routes.LegRepository;
import no.kabina.kaboot.routes.Route;
import no.kabina.kaboot.routes.RouteRepository;
import no.kabina.kaboot.stats.StatService;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SchedulerService {

  private final Logger logger = LoggerFactory.getLogger(SchedulerService.class);

  //final String SOLVER_CMD = "C:\\Python\\Python37\\python solver.py";
  final String SOLVER_CMD = "runpy.bat";
  final String SOLVER_OUT_FILE = "solv_out.txt";

  @Value("${kaboot.consts.max-pool4}")
  private int MAX_4POOL; // TODO: it is not that simple, we need a model here: size, max wait, ...

  @Value("${kaboot.consts.max-pool3}")
  private int MAX_3POOL;

  @Value("${kaboot.consts.max-non-lcm}")
  private int MAX_SOLVER_SIZE; // how big can a solver model be; 0 = no solver at all

  @Value("${kaboot.scheduler.online}")
  private boolean isOnline;

  private final TaxiOrderRepository taxiOrderRepository;
  private final CabRepository cabRepository;
  private final RouteRepository routeRepository;
  private final LegRepository legRepository;
  private final StatService statSrvc;

  public SchedulerService(TaxiOrderRepository taxiOrderRepository, CabRepository cabRepository,
                          RouteRepository routeRepository, LegRepository legRepository,
                          StatService statService) {
    this.taxiOrderRepository = taxiOrderRepository;
    this.cabRepository = cabRepository;
    this.routeRepository = routeRepository;
    this.legRepository = legRepository;
    this.statSrvc = statService;
  }

  //@Job(name = "Taxi scheduler", retries = 2)
  public void findPlan() {
    try {
      int[][] cost; //= new int[0][0];
      //UUID uuid = UUID.randomUUID();  // TODO: to mark cabs and customers as assigned to this instance of sheduler
      // first update some statistics
      updateAvgStats();
      long startSheduler = System.currentTimeMillis();
      // create demand for the solver
      TempModel tmpModel = prepareData();
      if (tmpModel == null) {
        return;
      }
      if (tmpModel.supply.length > 0 && tmpModel.demand.length > 0 && isOnline) {

        PoolElement[] pl = generatePool(tmpModel.demand);
        tmpModel.demand = PoolUtil.findFirstLegInPoolOrLone(pl, tmpModel.demand); // only the first leg will be sent to solver
        logger.info("Demand after pooling: {}", tmpModel.demand.length);
        cost = LcmUtil.calculateCost(tmpModel.demand, tmpModel.supply);

        statSrvc.updateMaxIntVal("max_model_size", cost.length);
        if (tmpModel.demand.length > MAX_SOLVER_SIZE && tmpModel.supply.length > MAX_SOLVER_SIZE) { // too big to send to solver, it has to be cut by LCM
          // both sides has to be bigger,
          TempModel tempModel = runLcm(tmpModel.supply, tmpModel.demand, cost, pl);
          if (tempModel == null) {
            return;
          }
            // to be sent to solver
          logger.info("After LCM: demand={}, supply={}", tmpModel.demand.length, tmpModel.supply.length);
          cost = LcmUtil.calculateCost(tmpModel.demand, tmpModel.supply);
        }
        runSolver(tmpModel.supply, tmpModel.demand, cost, pl);
        updateStats("sheduler_time", startSheduler);
      }
    } catch (Exception e) {
      logger.warn("{} {}", e.getMessage(), e.getCause());
    }
  }

  /**
   * Takes values stored in internal lists and stores in DB
   */
  private void updateAvgStats() {
    statSrvc.updateIntVal("avg_order_assign_time", statSrvc.countAverage("avg_order_assign_time")); // TODO: in the future move it where it is counted once after simulation
    statSrvc.updateIntVal("avg_order_pickup_time", statSrvc.countAverage("avg_order_pickup_time"));
    statSrvc.updateIntVal("avg_order_complet_time", statSrvc.countAverage("avg_order_complete_time"));
    statSrvc.updateIntVal("avg_pool_time", statSrvc.countAverage("avg_pool_time"));
    statSrvc.updateIntVal("avg_solver_time", statSrvc.countAverage("avg_solver_time"));
    statSrvc.updateIntVal("avg_sheduler_time", statSrvc.countAverage("avg_sheduler_time"));
  }

  private void runSolver(Cab[] tempSupply, TaxiOrder[] tempDemand, int[][] cost, PoolElement[] pl) {
    if (cost.length > MAX_SOLVER_SIZE) { // still too big to send to solver, it has to be cut hard
      if (tempSupply.length > MAX_SOLVER_SIZE) { // some cabs will not get passengers, they have to wait for new ones
        tempSupply = GcmUtil.reduceSupply(cost, tempSupply, MAX_SOLVER_SIZE);
      }
      if (tempDemand.length > MAX_SOLVER_SIZE) {
        tempDemand = GcmUtil.reduceDemand(cost, tempDemand, MAX_SOLVER_SIZE);
      }
      cost = LcmUtil.calculateCost(tempDemand, tempSupply); // it writes input file for solver
    }
    statSrvc.updateMaxIntVal("max_solver_size", cost.length);
    logger.info("Runnnig solver: demand={}, supply={}", tempDemand.length, tempSupply.length);
    runExternalSolver();
    int[] x = readSolversResult(cost.length);
    if (x.length != cost.length * cost.length) {
      logger.warn("Solver returned wrong data set");
      // TODO: LCM should be called here
    } else {
      int assgnd = assignCustomers(x, cost, tempDemand, tempSupply, pl);
      logger.info("Customers assigned by solver: {}", assgnd);
    }
    // now we could check if a customer in pool has got a cab,
    // if not - the other one should get a chance
  }

  private void runExternalSolver() {
    try {
      // TODO: rm out file first
      Process p = Runtime.getRuntime().exec(SOLVER_CMD);
      long startSolver = System.currentTimeMillis();
      p.waitFor();
      updateStats("solver_time", startSolver);
    } catch (InterruptedException | IOException e) {
      logger.warn("Exception while running solver: {}", e.getMessage());
    }
  }

  private void updateStats(String key, long startTime) {
    long endTime = System.currentTimeMillis();
    int totalTime = (int) ((endTime - startTime) / 1000F);

    statSrvc.addAverageElement("avg_" + key, (long) totalTime);
    statSrvc.updateMaxIntVal("max_" + key, totalTime);
  }

  private int[] readSolversResult(int nn) {
    int[] x = new int[nn * nn];
    try (BufferedReader reader = new BufferedReader(new FileReader(SOLVER_OUT_FILE))) {
      String line = null;
      for (int i = 0; i < nn * nn; i++) {
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

  private TempModel prepareData() {
    // create demand for the solver
    TaxiOrder[] tempDemand =
            taxiOrderRepository.findByStatus(TaxiOrder.OrderStatus.RECEIVED).toArray(new TaxiOrder[0]);
    tempDemand = expireRequests(tempDemand); // mark "refused" if waited too long

    if (tempDemand.length == 0) {
      logger.info("No suitable demand");
      return null; // don't solve anything
    }

    Cab[] tempSupply = cabRepository.findByStatus(Cab.CabStatus.FREE).toArray(new Cab[0]);
    if (tempSupply.length == 0) {
      logger.info("No cabs available");
      return null; // don't solve anything
    }
    logger.info("Initial count of demand={}, supply={}", tempDemand.length, tempSupply.length);

    tempDemand = LcmUtil.getRidOfDistantCustomers(tempDemand, tempSupply);
    if (tempDemand.length == 0) {
      logger.info("No suitable demand, too distant");
      return null; // don't solve anything
    }
    tempSupply = LcmUtil.getRidOfDistantCabs(tempDemand, tempSupply);
    if (tempSupply.length == 0) {
      logger.info("No cabs available, too distant");
      return null; // don't solve anything
    }
    return new TempModel(tempSupply, tempDemand);
  }

  /**
   * check if pools are possible - with 4, 3 and 2 passengers.
   * Some customers have very strict expectations, do not want to lose time and share their trip with any
   * @param demand
   * @return
   */
  private PoolElement[] generatePool(TaxiOrder[] demand) {
    final long startPool = System.currentTimeMillis();
    if (demand == null || demand.length < 2) {
      return new PoolElement[0];
    }
    // with 4 passengers
    PoolElement[] ret;
    PoolElement[] pl4 = null;
    if (demand.length < MAX_4POOL) {
      final long startPool4 = System.currentTimeMillis();
      pl4 = PoolUtil.findPool(demand, 4); // four passengers: size^4 combinations (full search)
      updateStats("pool4_time", startPool4);
    }
    // with 3 passengers
    TaxiOrder[] demand3 = PoolUtil.findCustomersWithoutPool(pl4, demand);
    if (demand3 != null && demand3.length > 0) { // there is still an opportunity
      PoolElement[] pl3;
      if (demand3.length < MAX_3POOL) { // not too big for three customers, let's find out!
        final long startPool3 = System.currentTimeMillis();
        pl3 = PoolUtil.findPool(demand3, 3);
        updateStats("pool3_time", startPool3);
        if (pl3.length == 0) {
          pl3 = pl4;
        } else {
          pl3 = ArrayUtils.addAll(pl3, pl4);
        }
      } else {
        pl3 = pl4;
      }
      // with 2 passengers (this runs fast and no max is needed
      TaxiOrder[] demand2 = PoolUtil.findCustomersWithoutPool(pl3, demand3);
      if (demand2 != null && demand2.length > 0) {
        PoolElement[] pl2 = PoolUtil.findPool(demand2, 2);
        if (pl2.length == 0) {
          ret = pl3;
        } else {
          ret = ArrayUtils.addAll(pl2, pl3);
        }
      } else {
        ret = pl3;
      }
    } else {
      ret = pl4;
    }
    updateStats("pool_time", startPool);
    // reduce tempDemand - 2nd+ passengers will not be sent to LCM or solver
    logger.info("Pool size: {}", ret == null ? 0 : ret.length);
    return ret;
  }

  private TempModel runLcm(Cab[] supply, TaxiOrder[] demand, int[][] cost, PoolElement[] pl) {

    final long startLcm = System.currentTimeMillis();
    // =========== LCM ==========
    statSrvc.updateMaxIntVal("max_lcm_size", cost.length);
    LcmOutput out = LcmUtil.lcm(cost, Math.min(demand.length, supply.length) - MAX_SOLVER_SIZE);

    // do we need this check, really
    if (out.minVal == LcmUtil.bigCost) { // no input for the solver; probably only when MAX_SOLVER_SIZE=0
      logger.info("out.minVal == LcmUtil.bigCost");
      return null;
    }

    List<LcmPair> pairs = out.pairs;
    logger.info("LCM pairs: {}", pairs.size());

    statSrvc.incrementIntVal("total_lcm_used");
    long endLcm = System.currentTimeMillis();
    int totalLcmtime = (int) ((endLcm - startLcm) / 1000F);

    statSrvc.updateMaxIntVal("max_lcm_time", totalLcmtime);

    if (pairs.isEmpty()) {
      logger.warn("critical -> a big model but LCM hasn't helped");
    }
    logger.info("LCM n_pairs={}", pairs.size());
    // go thru LCM response (which are indexes in tempDemand and tempSupply)
    int sum = 0;
    for (LcmPair pair : pairs) {
      sum += assignCustomerToCab(demand[pair.clnt], supply[pair.cab], pl);
    }
    logger.info("Customers assigned by LCM: {}", sum);

    return PoolUtil.analyzeLcmAndPool(pairs, pl, demand, supply); // also produce input for the solver
  }

  /**
   *
   * @param x
   * @param cost
   * @param tmpDemand
   * @param tmpSupply
   * @param pool
   * @return number of assigned customers
   */
  // solver returns a very simple output, it has to be compared with data which helped create its input
  private int assignCustomers(int[] x, int[][] cost, TaxiOrder[] tmpDemand, Cab[] tmpSupply, PoolElement[] pool) {
    int nn = cost.length;
    int count = 0;

    for (int s = 0; s < tmpSupply.length; s++) {
      for (int c = 0; c < tmpDemand.length; c++) {
        if (x[nn * s + c] == 1 && cost[s][c] < LcmUtil.bigCost) { // not a fake assignment (to balance the model)
          count += assignCustomerToCab(tmpDemand[c], tmpSupply[s], pool);
        }
      }
    }
    return count;
  }

  /**
   *
   * @param order
   * @param cab
   * @param pool
   * @return  number of assigned customers
   */
  @Transactional  // TODO: it isn't transactional
  private int assignCustomerToCab(TaxiOrder order, Cab cab, PoolElement[] pool) {
    // update CAB
    cab.setStatus(Cab.CabStatus.ASSIGNED);
    cabRepository.save(cab);
    Route route = new Route(Route.RouteStatus.ASSIGNED);
    route.setCab(cab);
    routeRepository.save(route);
    Leg leg = null;
    int legId = 0;
    if (cab.getLocation() != order.fromStand) { // cab has to move to pickup the first customer
      leg = new Leg(cab.getLocation(), order.fromStand, legId++, Route.RouteStatus.ASSIGNED);
      leg.setRoute(route);
      legRepository.save(leg);
      statSrvc.addToIntVal("total_pickup_distance", Math.abs(cab.getLocation() - order.fromStand));
    }
    // legs & routes are assigned to customers in Pool
    // if not assigned to a Pool we have to create a single-task route here
    for (PoolElement e : pool) { // PoolElement contains TaxiOrder IDs (primary keys)
      if (e.cust[0].id.equals(order.id)) { // yeap, this id is in a pool
        // checking number
        // save pick-up phase
        int c = 0;
        for (; c < e.numbOfCust - 1; c++) {
          leg = null;
          if (e.cust[c].fromStand != e.cust[c + 1].fromStand) { // there is movement
            leg = new Leg(e.cust[c].fromStand, e.cust[c + 1].fromStand, legId++, Route.RouteStatus.ASSIGNED);
            saveLeg(leg, route);
          }
          assignOrder(leg, e.cust[c], cab, route);
        }
        leg = null;
        // save drop-off phase
        if (e.cust[c].fromStand != e.cust[c + 1].toStand) {
          leg = new Leg(e.cust[c].fromStand, e.cust[c + 1].toStand, legId++, Route.RouteStatus.ASSIGNED);
          leg = saveLeg(leg, route);
        }
        assignOrder(leg, e.cust[c], cab, route);
        //
        for (c++; c < 2 * e.numbOfCust - 1; c++) {
          if (e.cust[c].toStand != e.cust[c + 1].toStand) {
            leg = new Leg(e.cust[c].toStand, e.cust[c + 1].toStand, legId++, Route.RouteStatus.ASSIGNED);
            saveLeg(leg, route);
          }
          // here we don't update TaxiOrder
        }
        return e.numbOfCust;
      }
    }
    // Pool not found
    leg = new Leg(order.fromStand, order.toStand, legId, Route.RouteStatus.ASSIGNED);
    leg = saveLeg(leg, route);
    assignOrder(leg, order, cab, route);
    return 1; // one customer
  }

  private Leg saveLeg(Leg l, Route r) {
    if (l != null) {
      l.setRoute(r);
      return legRepository.save(l);
    }
    return null;
  }

  private void assignOrder(Leg l, TaxiOrder o, Cab c, Route r) {
    // check if it is not cancelled
    Optional<TaxiOrder> curr = taxiOrderRepository.findById(o.id);
    if (curr.isEmpty()) {
      logger.warn("Attempt to assign a non existing order");
      return;
    }
    if (curr.get().getStatus() == TaxiOrder.OrderStatus.CANCELLED) { // customer cancelled while sheduler was working
      return; // TODO: the whole route should be adjusted, a stand maybe ommited
    }
    // TODO: maybe 'curr' should be used later on, not 'o', to imbrace some more changes
    if (l != null) {
      o.setLeg(l);
    }
    Duration duration = Duration.between(o.getRcvdTime(), LocalDateTime.now());
    statSrvc.addAverageElement("avg_order_assign_time", duration.getSeconds());

    o.setStatus(TaxiOrder.OrderStatus.ASSIGNED);
    o.setCab(c);
    o.setRoute(r);
    // TODO: order.eta to be set
    taxiOrderRepository.save(o);
  }

  /** refusing orders that have waited too long due to lack of available cabs
   *
   * @param demand
   * @return array of orders that are still valid
   */
  private TaxiOrder[] expireRequests (TaxiOrder[] demand) {
    List<TaxiOrder> newDemand = new ArrayList<>();

    LocalDateTime time = LocalDateTime.now();
    for (TaxiOrder o : demand) {
      long minutes = Duration.between(time, o.getRcvdTime()).getSeconds()/60;
      if (minutes > o.getMaxWait()) { // TODO: maybe scheduler should have its own, global MAX WAIT
        logger.info("Customer={} refused, max wait exceeded", o.id);
        o.setStatus(TaxiOrder.OrderStatus.REFUSED);
        taxiOrderRepository.save(o);
      } else {
        newDemand.add(o);
      }
    }
    return newDemand.toArray(new TaxiOrder[0]);
  }
}
