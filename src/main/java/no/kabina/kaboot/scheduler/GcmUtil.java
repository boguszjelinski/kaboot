package no.kabina.kaboot.scheduler;

import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Greatest Cost - finding cabs with greatest distance to limit the size of solver
public class GcmUtil {

  private static Logger logger = LoggerFactory.getLogger(GcmUtil.class);

    /** Find maximum minimums - get rid of Cabs which are too distant
     *
     * @param cost
     * @param tmpSupply current set of cabs to be cut
     * @param goalSize expected size of output array
     */
  public static Cab[] reduceSupply(int[][] cost, Cab[] tmpSupply, int goalSize) {
    if (tmpSupply.length <= goalSize) {
      return tmpSupply; // nothing to do
    }

    Integer[] minDistances = new Integer[cost.length];

    for (int s = 0; s < cost.length; s++) {
      int minVal = LcmUtil.bigCost;
      for (int d = 0; d < cost.length; d++) {
        if (cost[s][d] < minVal) {
          minVal = cost[s][d];
        }
      }
      minDistances[s] = minVal;
    }
    // finding max values and getting rid of them
    for (int x = 0; x < tmpSupply.length - goalSize; x++) { // repeat so many times
      int idx = 0;
      for (int i = 0; i < minDistances.length; i++) {
        idx = minDistances[i] > minDistances[idx] ? i : idx;
      }
      minDistances[idx] = -1; // otherwise you would try to remove it more than once
    }

    Cab[] ret = new Cab[goalSize];
    int idx = 0;
    for (int j=0; j<minDistances.length; j++) {
      if (minDistances[j] != -1) {
        ret[idx++] = tmpSupply[j];
      }
    }
    if (idx != goalSize) {
      logger.warn("there was -1 in table earlier - change this marker to -1234567890"); // TODO
    }
    return ret;
  }

  public static TaxiOrder[] reduceDemand(int[][] cost, TaxiOrder[] tmpDemand, int goalSize) {
    if (tmpDemand.length <= goalSize) {
      return tmpDemand; // nothing to do
    }

    Integer[] minDistances = new Integer[cost.length];

    for (int d = 0; d < cost.length; d++) {
      int minVal = LcmUtil.bigCost;
      for (int s = 0; s < cost.length; s++) {
        if (cost[s][d] < minVal) {
          minVal = cost[s][d];
        }
      }
      minDistances[d] = minVal;
    }
    // finding max values and getting rid of them
    for (int x = 0; x < tmpDemand.length - goalSize; x++) { // repeat so many times
      int idx = 0;
      for (int i = 0; i < minDistances.length; i++) {
        idx = minDistances[i] > minDistances[idx] ? i : idx;
      }
      minDistances[idx] = -1; // otherwise you would try to remove it more than once
    }

    TaxiOrder[] ret = new TaxiOrder[goalSize];
    int idx = 0;
    for (int j=0; j<minDistances.length; j++) {
      if (minDistances[j] != -1) {
        ret[idx++] = tmpDemand[j];
      }
    }
    if (idx != goalSize) {
      logger.warn("there was -1 in table earlier - change this marker to -1234567890"); // TODO
    }
    return ret;
  }
}
