package no.kabina.kaboot.scheduler;

import java.util.List;
import java.util.UUID;

import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.cabs.CabRepository;
import no.kabina.kaboot.orders.TaxiOrder;
import no.kabina.kaboot.orders.TaxiOrderRepository;
import no.kabina.kaboot.routes.Leg;
import no.kabina.kaboot.routes.Route;
import no.kabina.kaboot.routes.RouteRepository;
import no.kabina.kaboot.routes.TaskRepository;
import org.jobrunr.jobs.annotations.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SchedulerService {

  private Logger logger = LoggerFactory.getLogger(SchedulerService.class);

  @Value("${kaboot.consts.max-non-lcm}")
  public final int MAX_NON_LCM = 0; // how big can a solver model be; 0 = no solver at all

  @Value("${kaboot.scheduler.online}")
  public final boolean isOnline = false;

  private TaxiOrderRepository taxiOrderRepository;
  private CabRepository cabRepository;
  private RouteRepository routeRepository;
  private TaskRepository taskRepository;


  public static int kpi_max_model_size = 0;
  public static int kpi_total_LCM_used = 0;
  public static int kpi_max_LCM_time = 0;

  public SchedulerService(TaxiOrderRepository taxiOrderRepository, CabRepository cabRepository,
                          RouteRepository routeRepository, TaskRepository taskRepository) {
    this.taxiOrderRepository = taxiOrderRepository;
    this.cabRepository = cabRepository;
    this.routeRepository = routeRepository;
    this.taskRepository = taskRepository;
  }

  //@Job(name = "Taxi scheduler", retries = 2)
  public void findPlan() {
      try {
          int[][] cost = new int[0][0];
          UUID uuid = UUID.randomUUID();  // TODO: to mark cabs and customers as assigned to this instance of sheduler

          // create demand for the solver
          TaxiOrder[] tempDemand =
              taxiOrderRepository.findByStatus(TaxiOrder.OrderStatus.RECEIVED).toArray(new TaxiOrder[0]);

          if (tempDemand.length == 0) {
              return; // don't solve anything
          }
          Cab[] tempSupply = cabRepository.findByStatus(Cab.CabStatus.FREE).toArray(new Cab[0]);
          logger.info("Initial Count of demand=" + tempDemand.length + ", supply=" + tempSupply.length);

          if (isOnline) {

              PoolElement[] pl = PoolUtil.findPool(tempDemand, 4);
              // reduce tempDemand - 2nd+ passengers will not be sent to LCM or solver
              logger.info("Pool size: " + pl.length);
              tempDemand = PoolUtil.findFirstLegInPool(pl, tempDemand);
              cost = LcmUtil.calculate_cost(tempDemand, tempSupply);
              if (cost.length > kpi_max_model_size)
                  kpi_max_model_size = cost.length;
              if (cost.length > MAX_NON_LCM) { // too big to send to solver, it has to be cut by LCM
                  long start_lcm = System.currentTimeMillis();
                  // LCM
                  List<LcmPair> pairs = LcmUtil.lcm(cost);
                  logger.info("Number of LCM pairs: " + pairs.size());
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

        /*TempModel tempModel = analyzeLcmAndPool(pairs, pl, tempDemand, tempSupply); // also produce input for the solver
        // to be sent to solver
        tempSupply = tempModel.supply;
        tempDemand = tempModel.demand;
         */

                  // do we need this check, really
        /*if (LCM_min_val == big_cost) { // no input for the solver;
          return;
        }
        */

                  //cost = calculate_cost(temp_demand, temp_supply);
                  //logger.info(". Sent to solver: demand={}, supply={}", tempDemand.length, tempSupply.length);
              }
      /*if (cost.length > max_solver_size) max_solver_size = cost.length;
      Process p = Runtime.getRuntime().exec(SOLVER_CMD);
      try {
        long start_solver = System.currentTimeMillis();
        p.waitFor();
        long end_solver = System.currentTimeMillis();
        int temp_solver_time = (int)((end_solver - start_solver) / 1000F);
        if (temp_solver_time > max_solver_time) max_solver_time = temp_solver_time;
      } catch (InterruptedException e) {
        e.printStackTrace();
        System.exit(0);
      }*/
          }
/*    int[] x = readSolversResult(cost.length);
    analyzeSolution(x, cost, t, f, f_solv, temp_demand, temp_supply);

 */
          // now we could check if a customer in pool has got a cab,
          // if not - the other one should get a chance
      } catch (Exception e) {
          int a=0;
      }
  }

  @Transactional
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
      taskRepository.save(leg);
    }
    PoolElement elem = null;
    boolean found = false;
    // tasks & routes are assigned to customers in Pool
    // if not assigned to a Pool we have to create a single-task route here
    for (PoolElement e : pool) { // PoolElement contains TaxiOrder IDs (primary keys)
      if (e.cust[0].id == order.id) { // yeap, this id is in a pool
        found = true;
        // checking number
        // save pick-up phase
        int c = 0;
        for (; c < e.numbOfCust - 1; c++) {
          if (e.cust[c].fromStand != e.cust[c + 1].fromStand) { // there is movement
            leg = new Leg(e.cust[c].fromStand, e.cust[c + 1].fromStand, legId++, Route.RouteStatus.ASSIGNED);
          }
          saveLeg(e.cust[c], leg, route, cab); // TODO: analyze if a null leg is OK
        }
        // save drop-off phase
        if (e.cust[c].fromStand != e.cust[c + 1].toStand) {
          leg = new Leg(e.cust[c].fromStand, e.cust[c + 1].toStand, legId++, Route.RouteStatus.ASSIGNED);
        }
        saveLeg(e.cust[c], leg, route, cab);
        for (; c < 2 * e.numbOfCust - 1; c++) {
          if (e.cust[c].toStand != e.cust[c + 1].toStand) {
            leg = new Leg(e.cust[c].toStand, e.cust[c + 1].toStand, legId++, Route.RouteStatus.ASSIGNED);
          }
          saveLeg(e.cust[c], leg, route, cab);
        }
        break;
      }
    }
    if (!found) {  // lone trip
      leg = new Leg(order.fromStand, order.toStand, legId++, Route.RouteStatus.ASSIGNED);
      saveLeg(order, leg, route, cab);
    }
  }

  private void saveLeg(TaxiOrder o, Leg l, Route r, Cab c) {
    if (l != null) {
      l.setRoute(r);
      taskRepository.save(l);
      o.setTask(l);
    }
    o.setStatus(TaxiOrder.OrderStatus.ASSIGNED);
    o.setCab(c);
    o.setRoute(r);
    // TODO: order.eta to be set
    taxiOrderRepository.save(o);
  }
}
