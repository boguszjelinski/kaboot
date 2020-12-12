package no.kabina.kaboot.scheduler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LcmUtil {

  private static final Logger logger = LoggerFactory.getLogger(LcmUtil.class);

  private static final String SOLVER_COST_FILE = "cost.txt";
  public static final int SCHEDULING_DURATION = 3; // scheduler runs once a minute
  // + it takes one minute to compute + 1min to distribute the information
  public static final int BIG_COST = 250000;

  private LcmUtil() {} // hiding public
  
  /** Low Cost Method aka "greedy" - looks for lowest values in the matrix
  *
  * @param cost cost
  */
  public static LcmOutput lcm(int[][] cost, int howMany) { // 0 = no solver now
    int lcmMinVal = BIG_COST;
    int n = cost.length;
    int[][] costLcm = Arrays.stream(cost).map(int[]::clone).toArray(int[][]::new);
    List<LcmPair> pairs = new ArrayList<>();
    int counter = 0;
    boolean stop = false;
    for (int i = 0; i < n && !stop; i++) { // we need to repeat the search (cut off rows/columns) 'n' times
      lcmMinVal = BIG_COST;
      int smin = -1;
      int dmin = -1;
      int s;
      int d;
      for (s = 0; s < n; s++) {
        for (d = 0; d < n; d++) {
          if (costLcm[s][d] < lcmMinVal) {
            lcmMinVal = costLcm[s][d];
            smin = s;
            dmin = d;
          }
        }
      }
      if (lcmMinVal == BIG_COST) {
        break; // no more interesting stuff there, quit LCM
      }
      // assigning cab and the client
      pairs.add(new LcmPair(smin, dmin));

      // removing the column from further search by assigning big cost
      removeColsAndRows(n, costLcm, smin, dmin);
      counter++;
      if (counter == howMany) {
        stop = true; // rest will be covered by solver
      }
    }
    return new LcmOutput(pairs, lcmMinVal);
  }

  public static void removeColsAndRows(int n, int[][] costLcm, int smin, int dmin) {
    int s;
    int d;
    for (s = 0; s < n; s++) {
      costLcm[s][dmin] = BIG_COST;
    }
    // the same with the row
    for (d = 0; d < n; d++) {
      costLcm[smin][d] = BIG_COST;
    }
  }

  public static int[][] calculateCost(TaxiOrder[] tmpDemand, Cab[] tmpSupply) {
    int n = 0;
    int c;
    int d;
    int numbSupply = tmpSupply.length;
    int numbDemand = tmpDemand.length;
    n = Math.max(numbSupply, numbDemand); // checking max size for unbalanced scenarios
    if (n == 0) {
      return new int[0][0];
    }
    int[][] cost = new int[n][n];
    // resetting cost table
    for (c = 0; c < n; c++) {
      for (d = 0; d < n; d++) {
        cost[c][d] = BIG_COST;
      }
    }
    for (c = 0; c < numbSupply; c++) {
      for (d = 0; d < numbDemand; d++) {
        int dst = DistanceService.getDistance(tmpSupply[c].getLocation(), tmpDemand[d].fromStand);
        if (dst <= tmpDemand[d].getMaxWait() - SCHEDULING_DURATION - PoolUtil.POOL_MAX_WAIT_TIME) {
          // take this possibility only if reasonable time to pick-up a customer
          // otherwise big_cost will stay in this cell
          cost[c][d] = dst;
        }
      }
    }
    try (FileWriter fr = new FileWriter(new File(SOLVER_COST_FILE))) {
      fr.write(n + "\n");
      for (c = 0; c < n; c++) {
        for (d = 0; d < n; d++) {
          fr.write(cost[c][d] + " ");
        }
        fr.write("\n");
      }
    } catch (IOException ioe) {
      logger.warn("IOE: {}", ioe.getMessage());
    }
    return cost;
  }

  // TASK: it is not wise to make this search here and in calculatCost
  public static Cab[] getRidOfDistantCabs(TaxiOrder[] demand, Cab[] supply) {
    List<Cab> list = new ArrayList<>();
    for (Cab cab : supply) {
      for (TaxiOrder taxiOrder : demand) {
        int dst = DistanceService.getDistance(cab.getLocation(), taxiOrder.fromStand);
        if (dst <= taxiOrder.getMaxWait() - SCHEDULING_DURATION - PoolUtil.POOL_MAX_WAIT_TIME) {
          // great, we have at least one customer in range for this cab
          list.add(cab);
          break;
        }
      }
    }
    return list.toArray(new Cab[0]);
  }

  public static TaxiOrder[] getRidOfDistantCustomers(TaxiOrder[] demand, Cab[] supply) {
    List<TaxiOrder> list = new ArrayList<>();
    for (TaxiOrder taxiOrder : demand) {
      for (Cab cab : supply) {
        int dst = DistanceService.getDistance(cab.getLocation(), taxiOrder.fromStand);
        if (dst <= taxiOrder.getMaxWait() - SCHEDULING_DURATION - PoolUtil.POOL_MAX_WAIT_TIME) {
          // great, we have at least one cab in range for this customer
          list.add(taxiOrder);
          break;
        }
      }
    }
    return list.toArray(new TaxiOrder[0]);
  }
}
