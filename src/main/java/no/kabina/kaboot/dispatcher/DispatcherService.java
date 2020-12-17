package no.kabina.kaboot.dispatcher;

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

@Component
public class DispatcherService {

  private final Logger logger = LoggerFactory.getLogger(DispatcherService.class);

  public static final String SOLVER_CMD = "runpy.bat"; // C:\\Python\\Python37\\python solver.py
  public static final String SOLVER_OUT_FILE = "solv_out.txt";
  public static final String AVG_ORDER_ASSIGN_TIME = "avg_order_assign_time";
  public static final String AVG_ORDER_PICKUP_TIME = "avg_order_pickup_time";
  public static final String AVG_ORDER_COMPLETE_TIME = "avg_order_complete_time";
  public static final String AVG_LCM_TIME = "avg_lcm_time";
  public static final String AVG_LCM_SIZE = "avg_lcm_size";
  public static final String AVG_MODEL_SIZE = "avg_model_size";
  //public static final String AVG_POOL_SIZE = "avg_pool_size";
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

  @Value("${kaboot.scheduler.online}")
  private boolean isOnline;

  private final TaxiOrderRepository taxiOrderRepository;
  private final CabRepository cabRepository;
  private final RouteRepository routeRepository;
  private final LegRepository legRepository;
  private final StatService statSrvc;

  public DispatcherService(TaxiOrderRepository taxiOrderRepository, CabRepository cabRepository,
                           RouteRepository routeRepository, LegRepository legRepository,
                           StatService statService) {
    this.taxiOrderRepository = taxiOrderRepository;
    this.cabRepository = cabRepository;
    this.routeRepository = routeRepository;
    this.legRepository = legRepository;
    this.statSrvc = statService;
  }

  /** 1) get the data from DB
  *   2) find a (sub)optimal plan
  *   3) write this plan to DB
  */
  //@Job(name = "Taxi scheduler", retries = 2)
  public void findPlan() {
    int[][] cost;
    //UUID uuid = UUID.randomUUID();  // TASK: to mark cabs and customers as assigned to this instance of sheduler
    // first update some statistics
    updateAvgStats();
    long startSheduler = System.currentTimeMillis();

    // create demand for the solver
    TempModel tmpModel = prepareData(); // get current demand and supply available
    if (tmpModel == null) {
      return; // no suitable demand
    }
    TaxiOrder[] demand = tmpModel.getDemand();
    Cab[] supply = tmpModel.getSupply();

    if (supply.length > 0 && demand.length > 0 && isOnline) {

      PoolElement[] pl = generatePool(demand);
      demand = PoolUtil.findFirstLegInPoolOrLone(pl, demand); // only the first leg will be sent to LCM or solver
      logger.info("Demand after pooling: {}", demand.length);
      // now build a balanced cost matrix for solver
      cost = LcmUtil.calculateCost(demand, supply);

      statSrvc.updateMaxAndAvgStats("model_size", cost.length);
      if (demand.length > maxSolverSize && supply.length > maxSolverSize) { // too big to send to solver, it has to be cut by LCM
        // both sides has to be bigger, if one is smaller than we will just reverse-LCM (GCM) on the bigger side
        TempModel tempModel = runLcm(supply, demand, cost, pl);
        demand = tempModel.getDemand();
        supply = tempModel.getSupply();
        // to be sent to solver
        logger.info("After LCM: demand={}, supply={}", demand.length, supply.length);
        cost = LcmUtil.calculateCost(demand, supply);
      }
      runSolver(supply, demand, cost, pl);
      statSrvc.updateMaxAndAvgTime("sheduler_time", startSheduler);
    }
  }

  /**
   * Takes values stored in internal lists and stores in DB
   */
  private void updateAvgStats() {
    statSrvc.updateIntVal(AVG_ORDER_ASSIGN_TIME, statSrvc.countAverage(AVG_ORDER_ASSIGN_TIME)); // TASK: in the future move it where it is counted once after simulation
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
   * run linear solver (python script for now)
   * @param tempSupply cabs
   * @param tempDemand orders
   * @param cost matrix
   * @param pl pool - some more customer orders here
   */
  public void runSolver(Cab[] tempSupply, TaxiOrder[] tempDemand, int[][] cost, PoolElement[] pl) {
    if (cost.length > maxSolverSize) {
      // still too big to send to solver, it has to be cut hard
      if (tempSupply.length > maxSolverSize) {
        // some cabs will not get passengers, they have to wait for new ones
        tempSupply = GcmUtil.reduceSupply(cost, tempSupply, maxSolverSize);
      }
      if (tempDemand.length > maxSolverSize) {
        tempDemand = GcmUtil.reduceDemand(cost, tempDemand, maxSolverSize);
      }
      // recalculate cost matrix again
      cost = LcmUtil.calculateCost(tempDemand, tempSupply); // it writes input file for solver
    }
    statSrvc.updateMaxAndAvgStats("solver_size", cost.length);
    logger.info("Runnnig solver: demand={}, supply={}", tempDemand.length, tempSupply.length);
    runExternalSolver();
    // read results from a file
    int[] x = readSolversResult(cost.length);
    if (x.length != cost.length * cost.length) {
      logger.warn("Solver returned wrong data set");
      // TASK: LCM should be called here !!!
    } else {
      int assgnd = assignCustomers(x, cost, tempDemand, tempSupply, pl);
      logger.info("Customers assigned by solver: {}", assgnd);
    }
    // now we could check if a customer in pool has got a cab,
    // if not - the other one should get a chance
  }

  private void runExternalSolver() {
    try {
      // TASK: rm out file first
      Process p = Runtime.getRuntime().exec(SOLVER_CMD);
      long startSolver = System.currentTimeMillis();
      p.waitFor();
      statSrvc.updateMaxAndAvgTime("solver_time", startSolver);
    } catch (IOException e) {
      logger.warn("IOException while running solver: {}", e.getMessage());
    } catch (Exception e) {
      logger.warn("Exception while running solver: {}", e.getMessage());
    }
  }

  /**
   *  read solver output from a file - generated by a Python script
   * @param n size of cost the model
   * @return vector with binary data - assignments
   */
  public int[] readSolversResult(int n) {
    int[] x = new int[n * n];
    try (BufferedReader reader = new BufferedReader(new FileReader(SOLVER_OUT_FILE))) {
      String line = null;
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

  private TempModel prepareData() {
    // read demand for the solver from DB
    TaxiOrder[] tempDemand =
            taxiOrderRepository.findByStatus(TaxiOrder.OrderStatus.RECEIVED).toArray(new TaxiOrder[0]);
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
  public PoolElement[] generatePool(TaxiOrder[] demand) {
    final long startPool = System.currentTimeMillis();
    if (demand == null || demand.length < 2) {
      // you can't have a pool with 1 order
      return new PoolElement[0];
    }
    // try to put 4 passengers into one cab
    PoolElement[] pl4 = null;
    PoolUtil util = new PoolUtil();

    if (demand.length < max4Pool) { // pool4 takes a lot of time, it cannot analyze big data sets
      final long startPool4 = System.currentTimeMillis();
      pl4 = util.checkPool(demand, 4); // four passengers: size^4 combinations (full search)
      statSrvc.updateMaxAndAvgTime("pool4_time", startPool4);
    }
    // with 3 & 2 passengers, add plans with 4 passengers
    PoolElement[] ret = getPoolWith3and2(util, demand, pl4);
    statSrvc.updateMaxAndAvgTime("pool_time", startPool);
    // reduce tempDemand - 2nd+ passengers will not be sent to LCM or solver
    logger.info("Pool size: {}", ret == null ? 0 : ret.length);
    return ret;
  }

  private PoolElement[] getPoolWith3and2(PoolUtil util, TaxiOrder[] demand, PoolElement[] pl4) {
    PoolElement[] ret;
    TaxiOrder[] demand3 = PoolUtil.findCustomersWithoutPool(pl4, demand);
    if (demand3 != null && demand3.length > 0) { // there is still an opportunity
      PoolElement[] pl3;
      if (demand3.length < max3Pool) { // not too big for three customers, let's find out!
        final long startPool3 = System.currentTimeMillis();
        pl3 = util.checkPool(demand3, 3);
        statSrvc.updateMaxAndAvgTime("pool3_time", startPool3);
        if (pl3.length == 0) {
          pl3 = pl4;
        } else {
          pl3 = ArrayUtils.addAll(pl3, pl4); // merge both
        }
      } else {
        pl3 = pl4;
      }
      // with 2 passengers (this runs fast and no max is needed
      ret = getPoolWith2(util, demand3, pl3);
    } else {
      ret = pl4;
    }
    return ret;
  }

  private PoolElement[] getPoolWith2(PoolUtil util, TaxiOrder[] demand3, PoolElement[] pl3) {
    PoolElement[] ret;
    TaxiOrder[] demand2 = PoolUtil.findCustomersWithoutPool(pl3, demand3);
    if (demand2 != null && demand2.length > 0) {
      PoolElement[] pl2 = util.checkPool(demand2, 2);
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

  /**
   *
   * @param supply
   * @param demand
   * @param cost
   * @param pl
   * @return  returns demand and supply for the solver, not assigned by LCM
   */
  public TempModel runLcm(Cab[] supply, TaxiOrder[] demand, int[][] cost, PoolElement[] pl) {

    final long startLcm = System.currentTimeMillis();

    statSrvc.updateMaxAndAvgStats("lcm_size", cost.length);
    LcmOutput out = LcmUtil.lcm(cost, Math.min(demand.length, supply.length) - maxSolverSize);
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
      sum += assignCustomerToCab(demand[pair.getClnt()], supply[pair.getCab()], pl);
    }
    logger.info("Number of customers assigned by LCM: {}", sum);
    if (out.getMinVal() == LcmUtil.BIG_COST) { // no input for the solver; probably only when MAX_SOLVER_SIZE=0
      logger.info("No input for solver, out.minVal == LcmUtil.bigCost"); // should we return here?
      // TASK it does not sound reasonable to run solver under such circumstances
    }
    // also produce input for the solver
    return new TempModel(LcmUtil.supplyForSolver(pairs, supply), LcmUtil.demandForSolver(pairs, demand));
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
        if (x[nn * s + c] == 1 && cost[s][c] < LcmUtil.BIG_COST) { // not a fake assignment (to balance the model)
          count += assignCustomerToCab(tmpDemand[c], tmpSupply[s], pool);
        }
      }
    }
    return count;
  }

  /**
   * save this particular assignment to the DB (cab, route, leg, taxi_order)
   * @param order customer request
   * @param cab cab
   * @param pool the whole pool
   * @return  number of assigned customers
   */
  private int assignCustomerToCab(TaxiOrder order, Cab cab, PoolElement[] pool) { // TASK: it isn't transactional
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
      if (e.getCust()[0].id.equals(order.id)) { // yeap, this id is in a pool
        // checking number
        // save pick-up phase
        assignOrdersAndSaveLegs(cab, route, legId, e);
        return e.getNumbOfCust();
      }
    }
    // Pool not found
    leg = new Leg(order.fromStand, order.toStand, legId, Route.RouteStatus.ASSIGNED);
    leg = saveLeg(leg, route);
    assignOrder(leg, order, cab, route);
    return 1; // one customer
  }

  private void assignOrdersAndSaveLegs(Cab cab, Route route, int legId, PoolElement e) {
    Leg leg;
    int c = 0;
    for (; c < e.getNumbOfCust() - 1; c++) {
      leg = null;
      if (e.getCust()[c].fromStand != e.getCust()[c + 1].fromStand) { // there is movement
        leg = new Leg(e.getCust()[c].fromStand, e.getCust()[c + 1].fromStand, legId++, Route.RouteStatus.ASSIGNED);
        saveLeg(leg, route);
      }
      assignOrder(leg, e.getCust()[c], cab, route);
    }
    leg = null;
    // save drop-off phase
    if (e.getCust()[c].fromStand != e.getCust()[c + 1].toStand) {
      leg = new Leg(e.getCust()[c].fromStand, e.getCust()[c + 1].toStand, legId++, Route.RouteStatus.ASSIGNED);
      leg = saveLeg(leg, route);
    }
    assignOrder(leg, e.getCust()[c], cab, route);
    //
    for (c++; c < 2 * e.getNumbOfCust() - 1; c++) {
      if (e.getCust()[c].toStand != e.getCust()[c + 1].toStand) {
        leg = new Leg(e.getCust()[c].toStand, e.getCust()[c + 1].toStand, legId++, Route.RouteStatus.ASSIGNED);
        saveLeg(leg, route);
      }
      // here we don't update TaxiOrder
    }
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
      return; // TASK: the whole route should be adjusted, a stand maybe ommited
    }
    // TASK: maybe 'curr' should be used later on, not 'o', to imbrace some more changes
    if (l != null) {
      o.setLeg(l);
    }
    Duration duration = Duration.between(o.getRcvdTime(), LocalDateTime.now());
    statSrvc.addAverageElement(AVG_ORDER_ASSIGN_TIME, duration.getSeconds());

    o.setStatus(TaxiOrder.OrderStatus.ASSIGNED);
    o.setCab(c);
    o.setRoute(r);
    // TASK: order.eta to be set
    taxiOrderRepository.save(o);
  }

  /** refusing orders that have waited too long due to lack of available cabs
   *
   * @param demand
   * @return array of orders that are still valid
   */
  private TaxiOrder[] expireRequests(TaxiOrder[] demand) {
    List<TaxiOrder> newDemand = new ArrayList<>();

    LocalDateTime time = LocalDateTime.now();
    for (TaxiOrder o : demand) {
      long minutes = Duration.between(time, o.getRcvdTime()).getSeconds()/60;
      if (minutes > o.getMaxWait()) { // TASK: maybe scheduler should have its own, global MAX WAIT
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
