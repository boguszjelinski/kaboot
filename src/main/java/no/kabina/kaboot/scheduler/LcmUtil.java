package no.kabina.kaboot.scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LcmUtil {

  @Autowired
  static DistanceService dsrvc;

  private static final int bigCost = 250000;
  private static final int DROP_TIME = 10;

  /** Low Cost Method aka "greedy" - looks for lowest values in the matrix
  *
  * @param cost
  */
  public static List<LcmPair> lcm(int[][] cost) {

    final int maxNonLcm = 0; // 0 = no solver now
    int lcmMinVal;
    int n = cost.length;
    int[][] costLcm = Arrays.stream(cost).map(int[]::clone).toArray(int[][]::new);
    List<LcmPair> pairs = new ArrayList<LcmPair>();
    int size = n;
    for (int i = 0; i < n; i++) { // we need to repeat the search (cut off rows/columns) 'n' times
      lcmMinVal = bigCost;
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
      if (lcmMinVal == bigCost) {
        break; // no more interesting stuff there, quit LCM
      }
      // assigning cab and the client
      pairs.add(new LcmPair(smin, dmin));

      // removing the column from further search by assigning big cost
      for (s = 0; s < n; s++) {
        costLcm[s][dmin] = bigCost;
      }
      // the same with the row
      for (d = 0; d < n; d++) {
        costLcm[smin][d] = bigCost;
      }
      size--;
      if (size == maxNonLcm) {
        break; // rest will be covered by solver
      }
    }
    return pairs;
  }

  public static int[][] calculate_cost(TaxiOrder[] temp_demand, Cab[] temp_supply) {
    int n = 0, c, d;
    int n_supply = temp_supply.length;
    int n_demand = temp_demand.length;
    n = Math.max(n_supply, n_demand); // checking max size for unbalanced scenarios
    if (n == 0) return new int[0][0];

    int[][] cost = new int[n][n];
    // resetting cost table
    for (c = 0; c < n; c++) {
      for (d = 0; d < n; d++) {
        cost[c][d] = bigCost;
      }
    }
    for (c = 0; c < n_supply; c++) {
      for (d = 0; d < n_demand; d++) {
        int dst = dsrvc.getDistance(temp_supply[c].getLocation(), temp_demand[d].fromStand);
        if (dst < DROP_TIME) { // take this possibility only if reasonable time to pick-up a customer
          // otherwise big_cost will stay in this cell
          cost[c][d] = dst;
        }
      }
    }
/*    FileWriter fr = new FileWriter(new File(SOLVER_COST_FILE));
    fr.write( n +"\n");
    for (c=0; c<n; c++) {
      for (d=0; d<n; d++) fr.write(cost[c][d]+" ");
      fr.write("\n");
    }
    fr.close();
 */
    return cost;
  }


}
