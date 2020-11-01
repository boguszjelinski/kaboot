package no.kabina.kaboot.scheduler;

import java.util.UUID;

import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.cabs.CabRepository;
import no.kabina.kaboot.orders.TaxiOrder;
import no.kabina.kaboot.orders.TaxiOrderRepository;
import no.kabina.kaboot.routes.RouteRepository;
import org.jobrunr.jobs.annotations.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SchedulerService {

  Logger logger = LoggerFactory.getLogger(SchedulerService.class);

  TaxiOrderRepository taxiOrderRepository;
  CabRepository cabRepository;
  RouteRepository routeRepository;

  public SchedulerService(TaxiOrderRepository taxiOrderRepository, CabRepository cabRepository,
                          RouteRepository routeRepository) {
    this.taxiOrderRepository = taxiOrderRepository;
    this.cabRepository = cabRepository;
    this.routeRepository = routeRepository;
  }

  @Job(name = "Taxi scheduler", retries = 2)
  public void findPlan() {
    UUID uuid = UUID.randomUUID();

    // create demand for the solver
    TaxiOrder[] tempDemand = null;
    taxiOrderRepository.findByStatus(TaxiOrder.OrderStatus.RECEIVED).toArray(tempDemand);

    if (tempDemand.length == 0) {
      return; // don't solve anything
    }
    Cab[] tempSupply = null;
    cabRepository.findByStatus(Cab.CabStatus.FREE).toArray(tempSupply);
    logger.info("Initial Count of demand="+ tempDemand.length +", supply="+ tempSupply.length);

    int[][] cost = new int[0][0];
    // SOLVER
    if (tempSupply.length > 0) {

      Pool[] pl = findPool(t, f, temp_demand);
      temp_demand = analyzePool(pl, temp_demand); // reduce temp_demand

      cost = calculate_cost(temp_demand, temp_supply);
      if (cost.length > max_model_size) max_model_size = cost.length;
      if (cost.length > MAX_NON_LCM) { // too big to send to solver, it has to be cut by LCM
        long start_lcm = System.currentTimeMillis();
        // LCM
        LcmPair[] pairs = LCM(cost);
        total_LCM_used++;
        long end_lcm = System.currentTimeMillis();
        int temp_lcm_time = (int)((end_lcm - start_lcm) / 1000F);
        if (temp_lcm_time > max_LCM_time) max_LCM_time = temp_lcm_time;
        if (pairs.length == 0) {
          System.out.println ("critical -> a big model but LCM hasn't helped");
        }
        f_solv.write("LCM n_pairs="+ pairs.length);
        TempModel tempModel = analyzePairs(t, pairs, f, temp_demand, temp_supply); // also produce input for the solver
        temp_supply = tempModel.supply;
        temp_demand = tempModel.demand;

        if (LCM_min_val == big_cost) // no input for the solver;
          continue;

        cost = calculate_cost(temp_demand, temp_supply);
        f_solv.write(". Sent to solver: demand="+ temp_demand.length +", supply="+ temp_supply.length+ ". ");
      }
      if (cost.length > max_solver_size) max_solver_size = cost.length;
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
      }
    }
    int[] x = readSolversResult(cost.length);
    analyzeSolution(x, cost, t, f, f_solv, temp_demand, temp_supply);
    // now we could check if a customer in pool has got a cab,
    // if not - the other one should get a chance
    f.flush();
  }
}
