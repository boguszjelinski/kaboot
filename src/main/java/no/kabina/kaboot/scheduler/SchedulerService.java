package no.kabina.kaboot.scheduler;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.cabs.CabRepository;
import no.kabina.kaboot.orders.TaxiOrder;
import no.kabina.kaboot.orders.TaxiOrderRepository;
import no.kabina.kaboot.routes.Leg;
import no.kabina.kaboot.routes.LegRepository;
import no.kabina.kaboot.routes.Route;
import no.kabina.kaboot.routes.RouteRepository;
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

  @Value("${kaboot.consts.max-non-lcm}")
  private int MAX_SOLVER_SIZE; // how big can a solver model be; 0 = no solver at all

  @Value("${kaboot.scheduler.online}")
  private boolean isOnline;

  private TaxiOrderRepository taxiOrderRepository;
  private CabRepository cabRepository;
  private RouteRepository routeRepository;
  private LegRepository legRepository;


  public static int kpi_max_model_size = 0;
  public static int kpi_total_LCM_used = 0;
  public static int kpi_max_LCM_time = 0;
  public static int maxSolverTime = 0;

  public SchedulerService(TaxiOrderRepository taxiOrderRepository, CabRepository cabRepository,
                          RouteRepository routeRepository, LegRepository legRepository) {
    this.taxiOrderRepository = taxiOrderRepository;
    this.cabRepository = cabRepository;
    this.routeRepository = routeRepository;
    this.legRepository = legRepository;
  }

  //@Job(name = "Taxi scheduler", retries = 2)
  public void findPlan() {
    try {
      int[][] cost; //= new int[0][0];
      //UUID uuid = UUID.randomUUID();  // TODO: to mark cabs and customers as assigned to this instance of sheduler

      // create demand for the solver
      TaxiOrder[] tempDemand =
          taxiOrderRepository.findByStatus(TaxiOrder.OrderStatus.RECEIVED).toArray(new TaxiOrder[0]);

      if (tempDemand.length == 0) {
        return; // don't solve anything
      }
      Cab[] tempSupply = cabRepository.findByStatus(Cab.CabStatus.FREE).toArray(new Cab[0]);
      logger.info("Initial Count of demand={}, supply={}", tempDemand.length, tempSupply.length);

      if (tempSupply.length > 0 && tempDemand.length > 0 && isOnline) {
        // TODO: big models and
        PoolElement[] pl = PoolUtil.findPool(tempDemand, 4);
        // reduce tempDemand - 2nd+ passengers will not be sent to LCM or solver
        logger.info("Pool size: {}", pl.length);
        tempDemand = PoolUtil.findFirstLegInPool(pl, tempDemand);
        logger.info("Demand after pooling: {}", tempDemand.length);
        cost = LcmUtil.calculateCost(tempDemand, tempSupply);
        if (cost.length > kpi_max_model_size) {
          kpi_max_model_size = cost.length;
        }
        if (tempDemand.length > MAX_SOLVER_SIZE && tempSupply.length > MAX_SOLVER_SIZE) { // too big to send to solver, it has to be cut by LCM
          // both sides has to be bigger,
          long start_lcm = System.currentTimeMillis();
          // =========== LCM ==========
          LcmOutput out = LcmUtil.lcm(cost, Math.min(tempDemand.length, tempSupply.length) - MAX_SOLVER_SIZE);
          List<LcmPair> pairs = out.pairs;
          logger.info("LCM pairs: {}", pairs.size());
          kpi_total_LCM_used++;
          long end_lcm = System.currentTimeMillis();
          int temp_lcm_time = (int) ((end_lcm - start_lcm) / 1000F);
          if (temp_lcm_time > kpi_max_LCM_time)
            kpi_max_LCM_time = temp_lcm_time;
          if (pairs.size() == 0) {
            logger.warn("critical -> a big model but LCM hasn't helped");
          }
          logger.info("LCM n_pairs={}", pairs.size());
          // go thru LCM response (which are indexes in tempDemand and tempSupply)
          for (LcmPair pair : pairs) {
            assignCustomerToCab(tempDemand[pair.clnt], tempSupply[pair.cab], pl);
          }
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
        //if (cost.length > max_solver_size) max_solver_size = cost.length;
        if (cost.length > MAX_SOLVER_SIZE) { // still too big to send to solver, it has to be cut hard
          if (tempSupply.length > MAX_SOLVER_SIZE) { // some cabs will not get passengers, they have to wait for new ones
            tempSupply = GcmUtil.reduceSupply(cost, tempSupply, MAX_SOLVER_SIZE);
          }
          if (tempDemand.length > MAX_SOLVER_SIZE) {
            tempDemand = GcmUtil.reduceDemand(cost, tempDemand, MAX_SOLVER_SIZE);
          }
          cost = LcmUtil.calculateCost(tempDemand, tempSupply); // it writes input file for solver
        }
        runSolver();
        int[] x = readSolversResult(cost.length);
        if (x.length != cost.length * cost.length) {
          logger.warn("Solver returned wrong data set");
        } else {
          assignCustomers(x, cost, tempDemand, tempSupply, pl);
        }
        // now we could check if a customer in pool has got a cab,
        // if not - the other one should get a chance
      }
    } catch (Exception e) {
      logger.info(e.getMessage() + ": " + e.getCause());
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
      if (tempSolverTime > maxSolverTime) {
        maxSolverTime = tempSolverTime;
      }
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

  // solver returns a very simple output, it has to be compared with data which helped create its input
  private void assignCustomers(int[] x, int[][] cost, TaxiOrder[] tmpDemand, Cab[] tmpSupply, PoolElement[] pool) {
    int nn = cost.length;
    for (int s = 0; s < tmpSupply.length; s++) {
      for (int c = 0; c < tmpDemand.length; c++) {
        if (x[nn * s + c] == 1 && cost[s][c] < LcmUtil.bigCost) { // not a fake assignment (to balance the model)
          assignCustomerToCab(tmpDemand[c], tmpSupply[s], pool);
        }
      }
    }
  }

  @Transactional  // TODO: it isn't transactional
  private void assignCustomerToCab(TaxiOrder order, Cab cab, PoolElement[] pool) {
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
    boolean found = false;
    // legs & routes are assigned to customers in Pool
    // if not assigned to a Pool we have to create a single-task route here
    for (PoolElement e : pool) { // PoolElement contains TaxiOrder IDs (primary keys)
      if (e.cust[0].id.equals(order.id)) { // yeap, this id is in a pool
        found = true;
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
        break;
      }
    }
    if (!found) {  // lone trip
      leg = new Leg(order.fromStand, order.toStand, legId, Route.RouteStatus.ASSIGNED);
      leg = saveLeg(leg, route);
      updateOrder(leg, order, cab, route);
    }
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
}
