package no.kabina.kaboot.scheduler;
/* Author: Bogusz Jelinski
   A.D.: 2020
   Title: pool service
   Description:
*/

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;

public class PoolUtil {

  static final int MAX_NUMB_STANDS = 50;
  private static final int MAX_IN_POOL = 4;
  public static final int POOL_MAX_WAIT_TIME = 2; // how many 'minutes' (time entities) want a passenger to wait for a cab
  static final double MAX_LOSS = 1.01; // 1.3 would mean that one can accept a trip to take 30% time more than being transported alone

  static TaxiOrder[] demand;
  static int [][]cost  = new int[MAX_NUMB_STANDS][MAX_NUMB_STANDS];
  static int []pickup  = new int[MAX_IN_POOL]; // will have numbers of customers
  static int []dropoff = new int[MAX_IN_POOL]; // will have indexes to 'pickup' table
  public static int custInPool;  // how many passengers can a cab take
  static List<PoolElement> poolList = new ArrayList<>();

  private PoolUtil () {} // hiding default

  private static void dropCustomers(int level) {
    if (level == custInPool) { // we now know how to pick up and drop-off customers
      if (isHappy()) { // add to pool
        TaxiOrder[] orders = new TaxiOrder[custInPool + custInPool]; // pick-ups + drop-offs
        for (int c = 0; c < 2 * custInPool; c++) {
          orders[c] = null;
        }
        for (int i = 0; i < custInPool; i++) {
          orders[i] = demand[pickup[i]];
          orders[i + custInPool] = demand[pickup[dropoff[i]]];
        }
        int poolCost = getPoolCost(0, custInPool - 1);
        // that is an important decision - is this the 'goal' function to be optimized ?

        poolList.add(new PoolElement(orders, custInPool, poolCost));
      }
    } else {
      for (int c = 0; c < custInPool; c++) {
        // check if 'c' not in use in previous levels - "variation without repetition"
        if (isFound(level, c, dropoff)) {
          continue; // next proposal please
        }
        dropoff[level] = c;
        // a drop-off more
        dropCustomers(level + 1);
      }
    }
  }

  private static boolean isHappy() {
    for (int d = 0; d < custInPool; d++) { // checking if all 3(?) passengers get happy, starting from the one who gets dropped-off first
      int poolCost = getPoolCost(dropoff[d], d);
      if (poolCost > cost[demand[pickup[dropoff[d]]].fromStand][demand[pickup[dropoff[d]]].toStand] * MAX_LOSS) {
        return false; // not happy
      }
    }
    return true;
  }

  private static int getPoolCost(int i2, int i3) {
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

  private static void poolv2(int level, int numbCust) { // level of recursion = place in the pick-up queue
    if (level == custInPool) { // now we have all customers for a pool (proposal) and their order of pick-up
      // next is to generate combinations for the "drop-off" phase
      dropCustomers(0);
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
          poolv2(level + 1, numbCust);
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

  public static boolean isFound(PoolElement[] arr, int i, int j) {
    for (int x = 0; x < custInPool; x++) {
      for (int y = 0; y < custInPool; y++) {
        if (arr[j].cust[x] == arr[i].cust[y]) {
          return true;
        }
      }
    }
    return false;
  }

  public static PoolElement[] findPool(TaxiOrder[] dem, int inPool) {
    poolList = new ArrayList<>();
    custInPool = inPool;
    demand = dem;

    setCosts();
    poolv2(0, dem.length);
    // sorting
    PoolElement[] arr = poolList.toArray(new PoolElement[0]);
    Arrays.sort(arr);
    // removin duplicates
    int i = 0;
    for (i = 0; i < arr.length; i++) {
      if (arr[i].cost == -1) {
        continue;
      }
      for (int j = i + 1; j < arr.length; j++) {
        if (arr[j].cost != -1 // not invalidated; this check is for performance reasons
                && isFound(arr, i, j)) {
          arr[j].cost = -1; // duplicated
        }
      }
    }

    List<PoolElement> ret = new ArrayList<>();
    for (i = 0; i < arr.length; i++) {
      if (arr[i].cost != -1) {
        ret.add(arr[i]);
      }
    }
    return ret.toArray(new PoolElement[0]);
  }

  private static void setCosts() {
    for (int i = 0; i < MAX_NUMB_STANDS; i++) {
      for (int j = i; j < MAX_NUMB_STANDS; j++) {
        cost[j][i] = DistanceService.getDistance(j, i); // simplification of distance - stop9 is closer to stop7 than to stop1
        cost[i][j] = cost[j][i];
      }
    }
  }

  public static TaxiOrder[] findFirstLegInPoolOrLone(PoolElement[] arr, TaxiOrder[] tempDemand) {
    if (arr == null || arr.length == 0) {
      return tempDemand;
    }
    List<TaxiOrder> ret = new ArrayList<>();

    for (TaxiOrder td : tempDemand) {
      boolean found = false;
      for (PoolElement a : arr) {
        for (int j = 1; j < a.cust.length / 2; j++) { // 0: the first leg; /2 -> pickups + dropp-offs
          if (a.cust[j].id.equals(td.id)) { // this customer will be picked up by another customer should not be sent tol solver
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

  public static TaxiOrder[] findCustomersWithoutPool(PoolElement[] arr, TaxiOrder[] tempDemand) {
    if (arr == null || arr.length == 0) {
      return tempDemand;
    }
    List<TaxiOrder> ret = new ArrayList<>();

    for (TaxiOrder td : tempDemand) {
      boolean found = false;
      for (PoolElement a : arr) {
        for (int j = 0; j < a.cust.length / 2; j++) { // 0: the first leg; /2 -> pickups + dropp-offs
          if (a.cust[j].id.equals(td.id)) { // this customer will be picked up by another customer should not be sent tol solver
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

  public static TempModel analyzeLcmAndPool(List<LcmPair> pairs, PoolElement[]pl, TaxiOrder[] tempDemand, Cab[] tempSupply) {
    List<TaxiOrder> assignedDemand = new ArrayList<>();
    // first create list of customers that are assigned and don't need to go to solver
    for (LcmPair pair : pairs) {
      boolean found = false;
      for (PoolElement el : pl) {
        if (el.cust[0].id.equals(tempDemand[pair.getClnt()].id)) { // this pool has been approved by LCM
          found = true;
          assignedDemand.addAll(Arrays.asList(el.cust).subList(0, el.numbOfCust));
          break;
        }
      }
      if (!found) { // no pool found - add
        assignedDemand.add(tempDemand[pair.getClnt()]);
      }
    }
    return getTempModel(pairs, tempDemand, tempSupply, assignedDemand);
  }

  private static TempModel getTempModel(List<LcmPair> pairs, TaxiOrder[] tempDemand, Cab[] tempSupply,
                                                             List<TaxiOrder> assignedDemand) {
    List<TaxiOrder> restDemand = new ArrayList<>();
    for (TaxiOrder o : tempDemand) {
      TaxiOrder assigned = assignedDemand.stream().filter(a -> o.id.equals(a.id)).findAny().orElse(null);
      if (assigned == null) {
        restDemand.add(o);
      }
    }

    List<Cab> restSupply = new ArrayList<>();
    for (Cab c : tempSupply) {
      LcmPair assigned = pairs.stream().filter(a -> c.getId().equals(tempSupply[a.getCab()].getId())).findAny().orElse(null);
      if (assigned == null) {
        restSupply.add(c);
      }
    }

    return new TempModel(restSupply.toArray(new Cab[0]), restDemand.toArray(new TaxiOrder[0]));
  }
}
