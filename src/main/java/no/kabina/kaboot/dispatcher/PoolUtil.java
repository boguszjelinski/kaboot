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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Distance;

import static java.lang.Math.abs;

public class PoolUtil {

  private static final Logger logger = LoggerFactory.getLogger(PoolUtil.class);

  private int maxNumbStands;

  TaxiOrder[] demand;
  int [][]cost;
  private static final int MAX_IN_POOL = 4; // just for memory allocation, might be 10 as well
  int []pickup  = new int[MAX_IN_POOL]; // will have numbers of customers
  int []dropoff = new int[MAX_IN_POOL]; // will have indexes to 'pickup' table
  List<PoolElement> poolList = new ArrayList<>();

  public PoolUtil(int stands) {
    this.maxNumbStands = stands;
    this.cost = setCosts(stands);
  }

  /**
   *  This routine builds 'poolList' instance variable
   * @param level current level (depth) of recursion
   * @param custInPool max level (leaf)
   */
  private void dropCustomers(int level, int custInPool) {
    if (level == custInPool) {
      // the leaf in recursion, don't go deeper
      // we now know how to pick up and drop-off customers
      if (allAreHappy(custInPool)) { // add to pool
        TaxiOrder[] orders = new TaxiOrder[custInPool + custInPool]; // pick-ups + drop-offs
        // reset
        for (int c = 0; c < 2 * custInPool; c++) {
          orders[c] = null;
        }
        for (int i = 0; i < custInPool; i++) {
          orders[i] = demand[pickup[i]]; // pickup
          orders[i + custInPool] = demand[pickup[dropoff[i]]];  // drop-off
        }
        int poolCost = getPoolCost(0, custInPool - 1, custInPool);
        // that is an important decision - is this the 'goal' function to be optimized ?
        // we will sort the list below based on this
        poolList.add(new PoolElement(orders, custInPool, poolCost));
      }
    } else {
      for (int c = 0; c < custInPool; c++) {
        // check if 'c' not in use in previous levels - "variation without repetition"
        if (isFound(level, c, dropoff)) { // variation without repetion in this table - you can't drop someone off twice
          continue; // next proposal please
        }
        dropoff[level] = c;
        // a drop-off more
        dropCustomers(level + 1, custInPool);
      }
    }
  }

  private boolean allAreHappy(int custInPool) {
    for (int d = 0; d < custInPool; d++) { // checking if all passengers get happy, starting from the one who gets dropped-off first
      int poolCost = getPoolCost(dropoff[d], d, custInPool);
      // four depth levels of indexes (with cost[]) - how about that!
      TaxiOrder o = demand[pickup[dropoff[d]]];
      if (poolCost > cost[o.fromStand][o.toStand] * (1 + o.getMaxLoss()/100.0)) { // TASK: how much will floating point affect performance ?
        return false; // not happy
      }
    }
    return true;
  }

  private int getPoolCost(int i2, int i3, int custInPool) {
    int poolCost = 0;
    for (int i = i2; i < custInPool - 1; i++) { // cost of pick-up
      poolCost += cost[demand[pickup[i]].fromStand][demand[pickup[i + 1]].fromStand];
    }
    // cost of first drop-off
    poolCost += cost[demand[pickup[custInPool - 1]].fromStand][demand[pickup[dropoff[0]]].toStand]; // drop-off the first one
    for (int i = 0; i < i3; i++) { // cost of drop-off of the rest
      poolCost += cost[demand[pickup[dropoff[i]]].toStand][demand[pickup[dropoff[i + 1]]].toStand];
    }
    return poolCost;
  }

  public void checkPool(int level, int numbCust, int start, int stop, int custInPool) { // level of recursion = place in the pick-up queue
    // if we have reached the leaf of recursion, we won't go deeper
    if (level == custInPool) { // now we have all customers for a pool (proposal) and their order of pick-up
      // next is to generate combinations for the "drop-off" phase - recursion too
      dropCustomers(0, custInPool);
    } else {
      for (int c = start; c < stop; c++) {
        // check if 'c' not in use in previous levels - "variation without repetition"
        if (!isFound(level, c, pickup)) {
          pickup[level] = c;
          // check if the customer is happy, that he doesn't have to wait for too long
          // at this stage (pickup) we don't know anything about the "pool loss"
          int pCost = 0;
          for (int l = 0; l < level; l++) {
            pCost += cost[demand[pickup[l]].fromStand][demand[pickup[l + 1]].fromStand];
          }
          if (pCost > demand[pickup[level]].getMaxWait()) { // pay attention that we would have to add to 'pCost' the time which it takes to pickup the first customer by the cab
            continue;
          }
          // find the next customer
          checkPool(level + 1, numbCust, 0, numbCust, custInPool);
        }
      }
    }
  }

  public static boolean isFound(int level, int c, int[] tbl) {
    for (int l = 0; l < level; l++) {
      if (tbl[l] == c) {
        return true;
      }
    }
    return false;
  }

  /**
   *  we are comparing two 'PoolElement' objects, idexed with 'i' and 'j'.
   *  If we find any customer from 'j' in 'i', then the 'j' pool plan will not be taken into consideration, 'i' is better
   * @param arr
   * @param i
   * @param j
   * @param custInPool there are pool with 4,3 and 2 customers
   * @return
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
   *
   * @param dem
   * @param inPool how many passengers can a cab take
   * @return
   */
  public PoolElement[] findPool(TaxiOrder[] dem, int inPool) {
    this.poolList = new ArrayList<>();
    this.demand = dem;
    // 'poolList' will be built
    checkPool(0, demand.length, 0, demand.length, inPool); // this is not SMP
    return removeDuplicates(poolList.toArray(new PoolElement[0]), inPool);
  }

  // returning allocated pools DynaPool v1
  public static PoolElement[] removeDuplicates(PoolElement[] arr, int inPool) {
    if (arr == null) {
      return null;
    }
    Arrays.sort(arr);
    // removing duplicates
    int i = 0;
    for (i = 0; i < arr.length; i++) {
      if (arr[i].getCost() == -1) { // this -1 marker is set below
        continue;
      }
      for (int j = i + 1; j < arr.length; j++) {
        if (arr[j].getCost() != -1 // not invalidated; this check is for performance reasons
                && isFound(arr, i, j, inPool)) {
          arr[j].setCost(-1); // duplicated; we remove an element with greater costs (list is pre-sorted)
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

  public static boolean constraintsMet(DistanceService srvc, PoolElement el, int distCab) {
    // TASK: distances in pool should be stored to speed-up this check
    int dist = 0;
    for (int i = 0; i < el.getCust().length; i++) {
      TaxiOrder o = el.getCust()[i];
      if (el.custActions[i] == 'i' && dist + distCab > o.getMaxWait()) {
        return false;
      }
      if (el.custActions[i] == 'o' && dist > (1 + o.getMaxLoss()/100.0) * o.getDistance()) { // TASK: remove this calcul
        return false;
      }
      if (i < el.getCust().length - 1) {
        dist += srvc.distance[el.custActions[i] == 'i' ? o.getFromStand() : o.getToStand()]
                [el.custActions[i + 1] == 'i' ? el.getCust()[i + 1].getFromStand() : el.getCust()[i + 1].getToStand()];
      }
    }
    return true;
  }

  public static int[][] setCosts(int size) {
    int[][] costMatrix = new int[size][size];
    for (int i = 0; i < size; i++) {
      for (int j = i; j < size; j++) {
        // TASK: should we use BIG_COST to mark stands very distant from any cab ?
        costMatrix[j][i] = abs(i - j); // abs; simplification of distance - stop9 is closer to stop7 than to stop1
        costMatrix[i][j] = costMatrix[j][i];
      }
    }
    return costMatrix;
  }

  /**
   * take the initial demand and check if you find customers without a pool or in the beginning of a pool
   * they will be sent to LCM or solver
   * @param arr
   * @param tempDemand
   * @return
   */
  public static TaxiOrder[] findFirstLegInPoolOrLone(PoolElement[] arr, TaxiOrder[] tempDemand) {
    if (arr == null || arr.length == 0) {
      return tempDemand;
    }
    List<TaxiOrder> ret = new ArrayList<>();

    for (TaxiOrder td : tempDemand) {
      boolean found = false;
      for (PoolElement a : arr) {
        for (int j = 1; j < a.getCust().length / 2; j++) { // 0: the first leg; /2 -> pickups + dropp-offs
          if (a.getCust()[j].id.equals(td.id)) { // this customer will be picked up by another customer should not be sent tol solver
            found = true;
            break;
          }
        }
      }
      if (!found) { // 'd' is not picked up by others, he is the first one to be picked up, therefore will be sent to solver
        ret.add(td);
      }
    }
    return ret.toArray(new TaxiOrder[0]); // ret.size()
  }

  public static TaxiOrder[] removePoolFromDemand(PoolElement[] arr, TaxiOrder[] tempDemand) {
    if (arr == null || arr.length == 0) {
      return tempDemand;
    }
    List<TaxiOrder> ret = new ArrayList<>();

    for (TaxiOrder td : tempDemand) {
      boolean found = false;
      for (PoolElement a : arr) {
        for (int j = 0; j < a.getCust().length -1 ; j++) { //-1 the last one is OUT, so IN has been checked
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

  public static Cab[] trimSupply(Cab[] supply) {
    List<Cab> ret = new ArrayList<>();
    for (Cab c: supply) {
      if (c != null) {
        ret.add(c);
      }
    }
    return ret.toArray(new Cab[0]);
  }

  /**
   * take the initial demand and check if you find customers without a pool
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
      boolean found = false;
      for (PoolElement a : arr) {
        for (int j = 0; j < a.getCust().length; j++) {
          if (a.custActions[j] == 'i' && a.getCust()[j].id.equals(td.id)) { // this customer will be picked up by another customer should not be sent tol solver
            found = true;
            break;
          }
        }
        if (found) {
          break;
        }
      }
      if (!found) { // 'd' is not picked up by others, he is the first one to be picked up, therefore will be sent to solver
        ret.add(td);
      }
    }
    return ret.toArray(new TaxiOrder[0]); // ret.size()
  }
}
