package no.kabina.kaboot.scheduler;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.cabs.CabRepository;
import no.kabina.kaboot.orders.TaxiOrder;
import no.kabina.kaboot.orders.TaxiOrderRepository;
import no.kabina.kaboot.routes.Leg;
import no.kabina.kaboot.routes.LegRepository;
import no.kabina.kaboot.routes.Route;
import no.kabina.kaboot.routes.RouteRepository;
import no.kabina.kaboot.stats.StatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SchedulerService {

  private Logger logger = LoggerFactory.getLogger(SchedulerService.class);

  //final String SOLVER_CMD = "C:\\Python\\Python37\\python solver.py";
  final String SOLVER_CMD = "runpy.bat";
  final String SOLVER_OUT_FILE = "solv_out.txt";

  @Value("${kaboot.consts.max-pool4}")
  private int MAX_4POOL; // TODO: it is not that simple, we need a model here: size, max wait, ...

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
      statSrvc.updateIntVal("avg_pickup_time", statSrvc.countAvgPickupTime()); // TODO: in the future move it where it is counted once after simulation
      // create demand for the solver
      TaxiOrder[] tempDemand =
          taxiOrderRepository.findByStatus(TaxiOrder.OrderStatus.RECEIVED).toArray(new TaxiOrder[0]);
      tempDemand = expireRequests(tempDemand); // mark "refused" if waited too long

      if (tempDemand.length == 0) {
        logger.info("No suitable demand");
        return; // don't solve anything
      }

      Cab[] tempSupply = cabRepository.findByStatus(Cab.CabStatus.FREE).toArray(new Cab[0]);
      if (tempSupply.length == 0) {
        logger.info("No cabs available");
        return; // don't solve anything
      }
      logger.info("Initial count of demand={}, supply={}", tempDemand.length, tempSupply.length);

      tempDemand = LcmUtil.getRidOfDistantCustomers(tempDemand, tempSupply);
      if (tempDemand.length == 0) {
        logger.info("No suitable demand, too distant");
        return; // don't solve anything
      }
      tempSupply = LcmUtil.getRidOfDistantCabs(tempDemand, tempSupply);
      if (tempSupply.length == 0) {
        logger.info("No cabs available, too distant");
        return; // don't solve anything
      }

      if (tempSupply.length > 0 && tempDemand.length > 0 && isOnline) {

        PoolElement[] pl = null;
        // TODO: 4,3 & 2 - all of them should be run, as "4" can drop a lot of customers
        long start_pool = System.currentTimeMillis();
        if (tempDemand.length < MAX_4POOL) {
          pl = PoolUtil.findPool(tempDemand, 4); // four passengers: size^4 combinations (full search)
        } else {
          pl = PoolUtil.findPool(tempDemand, 3);
        }
        long end_pool = System.currentTimeMillis();
        int temp_pool_time = (int) ((end_pool - start_pool) / 1000F);

        statSrvc.updateMaxIntVal("max_pool_time", temp_pool_time);

        // reduce tempDemand - 2nd+ passengers will not be sent to LCM or solver
        logger.info("Pool size: {}", pl.length);
        tempDemand = PoolUtil.findFirstLegInPool(pl, tempDemand);
        logger.info("Demand after pooling: {}", tempDemand.length);
        cost = LcmUtil.calculateCost(tempDemand, tempSupply);

        statSrvc.updateMaxIntVal("max_model_size", cost.length);

        if (tempDemand.length > MAX_SOLVER_SIZE && tempSupply.length > MAX_SOLVER_SIZE) { // too big to send to solver, it has to be cut by LCM
          // ----- LCM block ----
          // both sides has to be bigger,
          long start_lcm = System.currentTimeMillis();
          // =========== LCM ==========
          statSrvc.updateMaxIntVal("max_lcm_size", cost.length);
          LcmOutput out = LcmUtil.lcm(cost, Math.min(tempDemand.length, tempSupply.length) - MAX_SOLVER_SIZE);
          List<LcmPair> pairs = out.pairs;
          logger.info("LCM pairs: {}", pairs.size());

          statSrvc.incrementIntVal("total_lcm_used");
          long end_lcm = System.currentTimeMillis();
          int temp_lcm_time = (int) ((end_lcm - start_lcm) / 1000F);

          statSrvc.updateMaxIntVal("max_lcm_time", temp_lcm_time);

          if (pairs.size() == 0) {
            logger.warn("critical -> a big model but LCM hasn't helped");
          }
          logger.info("LCM n_pairs={}", pairs.size());
          // go thru LCM response (which are indexes in tempDemand and tempSupply)
          int sum = 0;
          for (LcmPair pair : pairs) {
            sum += assignCustomerToCab(tempDemand[pair.clnt], tempSupply[pair.cab], pl);
          }
          logger.info("Customers assigned by LCM: {}", sum);
          TempModel tempModel = PoolUtil.analyzeLcmAndPool(pairs, pl, tempDemand, tempSupply); // also produce input for the solver
            // to be sent to solver
          tempSupply = tempModel.supply;
          tempDemand = tempModel.demand;
          logger.info("After LCM: demand={}, supply={}", tempDemand.length, tempSupply.length);
          // do we need this check, really
          if (out.minVal == LcmUtil.bigCost) { // no input for the solver; probably only when MAX_SOLVER_SIZE=0
            return;
          }
          cost = LcmUtil.calculateCost(tempDemand, tempSupply);
        }

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
        runSolver();
        int[] x = readSolversResult(cost.length);
        if (x.length != cost.length * cost.length) {
          logger.warn("Solver returned wrong data set");
        } else {
          int assgnd = assignCustomers(x, cost, tempDemand, tempSupply, pl);
          logger.info("Customers assigned by solver: {}", assgnd);
        }
        // now we could check if a customer in pool has got a cab,
        // if not - the other one should get a chance
      }
    } catch (Exception e) {
      logger.warn("{} {}", e.getMessage(), e.getCause());
    }
  }

  private void runSolver() {
    try {
      // TODO: rm out file first
      Process p = Runtime.getRuntime().exec(SOLVER_CMD);
      long startSolver = System.currentTimeMillis();
      p.waitFor();
      long endSolver = System.currentTimeMillis();
      int tempSolverTime = (int) ((endSolver - startSolver) / 1000F);
      statSrvc.updateMaxIntVal("max_solver_time", tempSolverTime);
    } catch (InterruptedException | IOException e) {
      logger.warn("Exception while running solver: {}", e.getMessage());
    }
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
          updateOrder(leg, e.cust[c], cab, route);
        }
        leg = null;
        // save drop-off phase
        if (e.cust[c].fromStand != e.cust[c + 1].toStand) {
          leg = new Leg(e.cust[c].fromStand, e.cust[c + 1].toStand, legId++, Route.RouteStatus.ASSIGNED);
          leg = saveLeg(leg, route);
        }
        updateOrder(leg, e.cust[c], cab, route);
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
    updateOrder(leg, order, cab, route);
    return 1; // one customer
  }

  private Leg saveLeg(Leg l, Route r) {
    if (l != null) {
      l.setRoute(r);
      return legRepository.save(l);
    }
    return null;
  }

  private void updateOrder(Leg l, TaxiOrder o, Cab c, Route r) {
    if (l != null) {
      o.setLeg(l);
    }
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
