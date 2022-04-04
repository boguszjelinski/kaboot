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

import static java.lang.Math.abs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;

public class PoolUtil {

  int [][]cost;

  public PoolUtil(int stands) {
    this.cost = setCosts(stands);
  }

  /** for tests only!
   *  we are comparing two 'PoolElement' objects, idexed with 'i' and 'j'.
   *  If we find any customer from 'j' in 'i', then the 'j' pool plan will
   *  not be taken into consideration, 'i' is better.

   * @param arr pools TASK do we need all?
   * @param i  the first to be compared
   * @param j  the other one
   * @param custInPool there are pool with 4,3 and 2 customers
   * @return boolean
   */
  public static boolean isFound(PoolElement[] arr, int i, int j, int custInPool) {
    for (int x = 0; x < custInPool; x++) {
      for (int y = 0; y < custInPool; y++) {
        if (arr[j].getCust()[x] == arr[i].getCust()[y]) {
          return true;
        }
      }
    }
    return false;
  }

  /** if pool 'i' is found in pool 'j'.

   * @param arr the whole pool list TASK: neccessary?
   * @param i index
   * @param j index
   * @param custInPool how many passengers
   * @return is found?
   */
  public static boolean isFoundV2(PoolElement[] arr, int i, int j, int custInPool) {
    for (int x = 0; x < custInPool + custInPool - 1; x++) { // -1 the last is OUT
      if (arr[i].custActions[x] == 'i') {
        for (int y = 0; y < custInPool + custInPool - 1; y++) {
          if (arr[j].custActions[y] == 'i' && arr[j].getCust()[y] == arr[i].getCust()[x]) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   *  for tests only.

   * @param arr pools
   * @param inPool how many passengers can a cab take
   * @return array
   */
  public static PoolElement[] removeDuplicates(PoolElement[] arr, int inPool) {
    if (arr == null) {
      return new PoolElement[0]; // empty array
    }
    Arrays.sort(arr);
    // removing duplicates
    int i;
    for (i = 0; i < arr.length; i++) {
      if (arr[i].getCost() == -1) { // this -1 marker is set below
        continue;
      }
      for (int j = i + 1; j < arr.length; j++) {
        if (arr[j].getCost() != -1 // not invalidated; this check is for performance reasons
                && isFound(arr, i, j, inPool)) {
          arr[j].setCost(-1); // duplicated
          // we remove an element with greater costs (list is pre-sorted)
        }
      }
    }
    // just collect non-duplicated pool plans
    List<PoolElement> ret = new ArrayList<>();
    for (i = 0; i < arr.length; i++) {
      if (arr[i].getCost() != -1) {
        ret.add(arr[i]);
      }
    }
    return ret.toArray(new PoolElement[0]);
  }

  /**
   * LCM.

   * @param srvc distances
   * @param supply cabs
   * @param o to get from i order
   * @return index of cab
   */
  public static int findNearestCab(DistanceService srvc, Cab[] supply, TaxiOrder o) {
    int dist = 1000; // big
    int nearest = -1;
    for (int i = 0; i < supply.length; i++) {
      if (supply[i] == null) { // allocated earlier to a pool
        continue;
      }
      Cab c = supply[i];
      if (srvc.distance[c.getLocation()][o.getFromStand()] < dist) {
        dist = srvc.distance[c.getLocation()][o.getFromStand()];
        nearest = i;
      }
    }
    return nearest;
  }

  /** if maxWait and maxLoss are met.

   * @param srvc distances
   * @param el pool
   * @param distCab distance from the cab's location to first pickup
   * @return are met?
   */
  public static boolean constraintsMet(DistanceService srvc, PoolElement el, int distCab) {
    // TASK: distances in pool should be stored to speed-up this check
    int dist = 0;
    for (int i = 0; i < el.getCust().length; i++) {
      TaxiOrder o = el.getCust()[i];
      if (el.custActions[i] == 'i' && dist + distCab > o.getMaxWait()) {
        return false;
      }
      if (el.custActions[i] == 'o' && dist > (1 + o.getMaxLoss() / 100.0) * o.getDistance()) {
        // TASK: remove this calculation above, it should be stored
        return false;
      }
      if (i < el.getCust().length - 1) {
        dist += srvc.distance[el.custActions[i] == 'i' ? o.getFromStand() : o.getToStand()]
                [el.custActions[i + 1] == 'i'
                    ? el.getCust()[i + 1].getFromStand() : el.getCust()[i + 1].getToStand()];
      }
    }
    return true;
  }

  /** for testing only.
   *
   * @param size number of stops
   * @return array
   */
  public static int[][] setCosts(int size) {
    int[][] costMatrix = new int[size][size];
    for (int i = 0; i < size; i++) {
      for (int j = i; j < size; j++) {
        // TASK: should we use BIG_COST to mark stands very distant from any cab ?
        costMatrix[j][i] = abs(i - j);
        // abs; simplification of distance - stop9 is closer to stop7 than to stop1
        costMatrix[i][j] = costMatrix[j][i];
      }
    }
    return costMatrix;
  }

  /**
   * take the initial demand and check if you find customers without a pool or
   * in the beginning of a pool
   * they will be sent to LCM or solver.

   * @param arr list of pools
   * @param tempDemand orders
   * @return array
   */
  public static TaxiOrder[] findFirstLegInPoolOrLone(PoolElement[] arr, TaxiOrder[] tempDemand) {
    if (arr == null || arr.length == 0) {
      return tempDemand;
    }
    List<TaxiOrder> ret = new ArrayList<>();

    for (TaxiOrder td : tempDemand) {
      boolean found = false;
      for (PoolElement a : arr) {
        for (int j = 1; j < a.getCust().length / 2; j++) { // 0: the first leg
          // /2 -> pickups + dropp-offs
          if (a.getCust()[j].id.equals(td.id)) { // this customer will be picked
            // up by another customer should not be sent tol solver
            found = true;
            break;
          }
        }
      }
      if (!found) { // 'd' is not picked up by others,
        // he is the first one to be picked up, therefore will be sent to solver
        ret.add(td);
      }
    }
    return ret.toArray(new TaxiOrder[0]); // ret.size()
  }

  /** we had to split this and that below to avoid Sonar complaits.

   * @param arr    pools
   * @param tempDemand  orders
   * @return array
   */
  public static TaxiOrder[] removePoolFromDemand(PoolElement[] arr, TaxiOrder[] tempDemand) {
    if (arr == null || arr.length == 0) {
      return tempDemand;
    }
    return rmPoolFromDemand(arr, tempDemand);
  }

  private static  TaxiOrder[] rmPoolFromDemand(PoolElement[] arr, TaxiOrder[] tempDemand) {
    List<TaxiOrder> ret = new ArrayList<>();
    for (TaxiOrder td : tempDemand) {
      boolean found = false;
      for (PoolElement a : arr) {
        for (int j = 0; j < a.getCust().length - 1; j++) {
          //-1 above the last one is OUT, so IN has been checked
          if (a.getCust()[j].id.equals(td.id)) {
            found = true;
            break;
          }
        }
        if (found) {
          break;
        }
      }
      if (!found) {
        ret.add(td);
      }
    }
    return ret.toArray(new TaxiOrder[0]);
  }

  /** removing nulls which means 'allocated'.

   * @param supply cabs
   * @return array
   */
  public static Cab[] trimSupply(Cab[] supply) {
    List<Cab> ret = new ArrayList<>();
    for (Cab c : supply) {
      if (c != null) {
        ret.add(c);
      }
    }
    return ret.toArray(new Cab[0]);
  }

  /** Take the initial demand and check if you find customers without a pool.

   * @param arr pool plans
   * @param tempDemand initial demand
   * @return customers without a pool at all
   */
  public static TaxiOrder[] findCustomersWithoutPoolV2(PoolElement[] arr, TaxiOrder[] tempDemand) {
    if (arr == null || arr.length == 0) {
      return tempDemand;
    }
    List<TaxiOrder> ret = new ArrayList<>();

    for (TaxiOrder td : tempDemand) {
      if (!elementIsFound(arr, td)) { // 'd' is not picked up by others,
        // he is the first one to be picked up, therefore will be sent to solver
        ret.add(td);
      }
    }
    return ret.toArray(new TaxiOrder[0]); // ret.size()
  }

  private static boolean elementIsFound(PoolElement[] arr, TaxiOrder td) {
    boolean found = false;
    for (PoolElement a : arr) {
      for (int j = 0; j < a.getCust().length; j++) {
        if (a.custActions[j] == 'i' && a.getCust()[j].id.equals(td.id)) { // this customer will be
          // picked up by another customer should not be sent tol solver
          found = true;
          break;
        }
      }
      if (found) {
        break;
      }
    }
    return found;
  }

  /**
   * Difference as angle of two bearings (stops).

   * @param a first bearing
   * @param b second one
   * @return angle
   */
  public static int bearingDiff(int a, int b) {
    int r = (a - b) % 360;
    if (r < -180.0) {
      r += 360.0;
    } else if (r >= 180.0) {
      r -= 360.0;
    }
    return Math.abs(r);
  }
}
