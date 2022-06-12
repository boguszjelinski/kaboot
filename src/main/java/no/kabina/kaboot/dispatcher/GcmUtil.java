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

import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Greatest Cost - finding cabs with greatest distance to limit the size of solver model
public class GcmUtil {

  private GcmUtil() {} // hiding public

  private static final Logger logger = LoggerFactory.getLogger(GcmUtil.class);

  /** Find maximum minimums - get rid of Cabs which are too distant
  *
  * @param cost matrix
  * @param tmpSupply current set of cabs to be cut
  * @param goalSize expected size of output array
  */
  public static Cab[] reduceSupply(int[][] cost, Cab[] tmpSupply, int goalSize) {
    if (tmpSupply.length <= goalSize) {
      return tmpSupply; // nothing to do
    }
    Integer[] minDistances = findMinDistancesForSupply(cost);

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
    for (int j = 0; j < minDistances.length; j++) {
      if (minDistances[j] != -1) {
        ret[idx++] = tmpSupply[j];
      }
    }
    if (idx != goalSize) {
      logger.warn("there was -1 in table earlier - change this marker to -1234567890"); // TASK
    }
    return ret;
  }

  public static TaxiOrder[] reduceDemand(int[][] cost, TaxiOrder[] tmpDemand, int goalSize) {

    if (tmpDemand.length <= goalSize) {
      return tmpDemand; // nothing to do
    }

    Integer[] minDistances = findMinDistancesForDemand(cost);

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
    for (int j = 0; j < minDistances.length; j++) {
      if (minDistances[j] != -1) {
        ret[idx++] = tmpDemand[j];
      }
    }
    if (idx != goalSize) {
      logger.warn("there was -1 in table earlier - change this marker to -1234567890"); // TASK
    }
    return ret;
  }

  public static Integer[] findMinDistancesForDemand(int[][] cost) {
    if (cost.length == 0) {
      return new Integer[0];
    }
    Integer[] minDistances = new Integer[cost[0].length];

    for (int d = 0; d < cost[0].length; d++) {
      int minVal = LcmUtil.BIG_COST;
      for (int[] ints : cost) {
        if (ints[d] < minVal) {
          minVal = ints[d];
        }
      }
      minDistances[d] = minVal;
    }
    return minDistances;
  }

  public static Integer[] findMinDistancesForSupply(int[][] cost) {
    if (cost.length == 0) {
      return new Integer[0];
    }
    int supLen = cost.length;
    int demLen = cost[0].length;
    Integer[] minDistances = new Integer[supLen];

    for (int s = 0; s < supLen; s++) {
      int minVal = LcmUtil.BIG_COST;
      for (int d = 0; d < demLen; d++) {
        if (cost[s][d] < minVal) {
          minVal = cost[s][d];
        }
      }
      minDistances[s] = minVal;
    }
    return minDistances;
  }
}
