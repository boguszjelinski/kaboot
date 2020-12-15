package no.kabina.kaboot.dispatcher;
/* Author: Bogusz Jelinski
   A.D.: 2020
   Title: pool service
   Description:
*/

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import no.kabina.kaboot.orders.TaxiOrder;

public class PoolUtil {

  static final int MAX_NUMB_STANDS = 50;
  private static final int MAX_IN_POOL = 4;
  public static final int POOL_MAX_WAIT_TIME = 2; // how many 'minutes' (time entities) want a passenger to wait for a cab
  static final double MAX_LOSS = 1.01; // 1.3 would mean that one can accept a trip to take 30% time more than being transported alone

  TaxiOrder[] demand;
  int [][]cost;
  int []pickup  = new int[MAX_IN_POOL]; // will have numbers of customers
  int []dropoff = new int[MAX_IN_POOL]; // will have indexes to 'pickup' table
  List<PoolElement> poolList = new ArrayList<>();

  /**
   *  This routine builds 'poolList' instance variable
   * @param level
   * @param custInPool
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
          orders[i + custInPool] = demand[pickup[dropoff[i]]];  // dropp-off
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
    for (int d = 0; d < custInPool; d++) { // checking if all 3(?) passengers get happy, starting from the one who gets dropped-off first
      int poolCost = getPoolCost(dropoff[d], d, custInPool);
      // three depth levels of indexes - how about that!
      if (poolCost > cost[demand[pickup[dropoff[d]]].fromStand][demand[pickup[dropoff[d]]].toStand] * MAX_LOSS) {
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

  private void findPool(int level, int numbCust, int custInPool) { // level of recursion = place in the pick-up queue
    // if we have reached the leaf of recursion, we won't go deeper
    if (level == custInPool) { // now we have all customers for a pool (proposal) and their order of pick-up
      // next is to generate combinations for the "drop-off" phase - recursion too
      dropCustomers(0, custInPool);
    } else {
      for (int c = 0; c < numbCust; c++) {
        // check if 'c' not in use in previous levels - "variation without repetition"
        if (!isFound(level, c, pickup)) {
          pickup[level] = c;
          // check if the customer is happy, that he doesn't have to wait for too long
          int pCost = 0;
          for (int l = 0; l < level; l++) {
            pCost += cost[demand[pickup[l]].fromStand][demand[pickup[l + 1]].fromStand];
          }
          if (pCost > POOL_MAX_WAIT_TIME) { // TASK: each customer has its own preference
            continue;
          }
          // find the next customer
          findPool(level + 1, numbCust, custInPool);
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

  /**
   *
   * @param dem
   * @param inPool how many passengers can a cab take
   * @return
   */
  public PoolElement[] checkPool(TaxiOrder[] dem, int inPool) {
    this.poolList = new ArrayList<>();
    this.demand = dem;
    this.cost = setCosts();
    // 'poolList' will be built
    findPool(0, demand.length, inPool);
    // sorting
    PoolElement[] arr = poolList.toArray(new PoolElement[0]);
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

  private static int[][] setCosts() {
    int[][] costMatrix = new int[MAX_NUMB_STANDS][MAX_NUMB_STANDS];
    for (int i = 0; i < MAX_NUMB_STANDS; i++) {
      for (int j = i; j < MAX_NUMB_STANDS; j++) {
        // TASK: should we use BIG_COST to mark stands very distant from any cab ?
        costMatrix[j][i] = DistanceService.getDistance(j, i); // simplification of distance - stop9 is closer to stop7 than to stop1
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

  /**
   * take the initial demand and check if you find customers without a pool
   * @param arr pool plans
   * @param tempDemand initial demand
   * @return customers without a pool at all
   */
  public static TaxiOrder[] findCustomersWithoutPool(PoolElement[] arr, TaxiOrder[] tempDemand) {
    if (arr == null || arr.length == 0) {
      return tempDemand;
    }
    List<TaxiOrder> ret = new ArrayList<>();

    for (TaxiOrder td : tempDemand) {
      boolean found = false;
      for (PoolElement a : arr) {
        for (int j = 0; j < a.getCust().length / 2; j++) { // 0: the first leg; /2 -> pickups + dropp-offs
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
}
