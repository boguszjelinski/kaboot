package no.kabina.kaboot.scheduler;
/* Author: Bogusz Jelinski
   A.D.: 2020
   Title: pool service
   Description:
*/

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import no.kabina.kaboot.orders.TaxiOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PoolUtil {

  @Autowired
  private static DistanceService dsrvc;

  static final int maxNumbStands = 50;
  static final int ID = 0;
  static final int FROM = 1;
  static final int TO = 2;

  // four most important parameters 
  static final int maxNumbCust = 1500;  // number of customers/passengers
  public static int MAX_IN_POOL;  // how many passengers can a cab take
  static final int MAX_WAIT_TIME = 10; // how many 'minutes' (time entities) want a passenger to wait for a cab
  static final double MAX_LOSS = 1.01; // 1.3 would mean that one can accept a trip to take 30% time more than being transported alone

  static int [][]cost  = new int[maxNumbStands][maxNumbStands];
  static TaxiOrder[] demand ;
  static int []pickup  = new int[MAX_IN_POOL]; // will have numbers of customers
  static int []dropoff = new int[MAX_IN_POOL]; // will have indexes to 'pickup' table
  static int numb_cust = 0; // maybe *2? twice as many customers than there are stops;
  static int count = 0;
  static int count_all = 0;
  static List<PoolElement> poolList = new ArrayList<PoolElement>();

  private static void dropCustomers(int level) {
    if (level == MAX_IN_POOL) { // we now know how to pick up and drop-off customers
      count_all++;
      boolean happy = true;
      for (int d = 0; d < MAX_IN_POOL; d++) { // checking if all 3(?) passengers get happy, starting from the one who gets dropped-off first
        int pool_cost = 0;
        // cost of pick-up phase
        for (int ph = dropoff[d]; ph < MAX_IN_POOL-1; ph++) {
          pool_cost += cost[demand[pickup[ph]].fromStand][demand[pickup[ph + 1]].fromStand];
        }
        // cost of first drop-off
        pool_cost += cost[demand[pickup[MAX_IN_POOL-1]].fromStand][demand[pickup[dropoff[0]]].toStand];
        // cost of drop-off
        for (int ph = 0; ph < d; ph++) {
          pool_cost += cost[demand[pickup[dropoff[ph]]].toStand][demand[pickup[dropoff[ph + 1]]].toStand];
        }
        if (pool_cost > cost[demand[pickup[dropoff[d]]].fromStand][demand[pickup[dropoff[d]]].toStand] * MAX_LOSS) {
          happy = false; // not happy
          break;
        }
      }
      if (happy) {
        // add to pool
        PoolElement pool = new PoolElement();
        // TODO:
        pool.numbOfCust = MAX_IN_POOL;
        pool.cust = new TaxiOrder[MAX_IN_POOL + MAX_IN_POOL]; // pick-ups + drop-offs
        for (int c = 0; c < 2*MAX_IN_POOL; c++ ) {
          pool.cust[c] = null;
        }
        for (int i = 0; i < MAX_IN_POOL; i++) {
          pool.cust[i] = demand[pickup[i]];
          pool.cust[i + MAX_IN_POOL] = demand[pickup[dropoff[i]]];
        }
        int pool_cost = 0;
        for (int i = 0; i < MAX_IN_POOL -1; i++) { // cost of pick-up
          pool_cost += cost[demand[pickup[i]].fromStand][demand[pickup[i + 1]].fromStand];
        }
        pool_cost += cost[demand[pickup[MAX_IN_POOL-1]].fromStand][demand[pickup[dropoff[0]]].toStand]; // drop-off the first one
        for (int i = 0; i < MAX_IN_POOL -1; i++) { // cost of drop-off of the rest
          pool_cost += cost[demand[pickup[dropoff[i]]].toStand][demand[pickup[dropoff[i + 1]]].toStand];
        }
        // that is an imortant decision - is this the 'goal' function to be optimized ?
        pool.cost = pool_cost;
        poolList.add(pool);
        count++;
      }
    } else {
      for (int c = 0; c < MAX_IN_POOL; c++) {
        // check if 'c' not in use in previous levels - "variation without repetition"
        boolean found = false;
        for (int l = 0; l < level; l++) {
          if (dropoff[l] == c) {
            found = true;
            break;
          }
        }
        if (found) {
          continue; // next proposal please
        }
        dropoff[level] = c;
        // a drop-off more
        dropCustomers(level + 1);
      }
    }
  }

  private static void poolv2(int level) { // level of recursion = place in the pick-up queue
    if (level == MAX_IN_POOL) { // now we have all customers for a pool (proposal) and their order of pick-up
        // next is to generate combinations for the "drop-off" phase
        dropCustomers(0);
    } else {
      for (int c = 0; c < numb_cust; c++) {
        // check if 'c' not in use in previous levels - "variation without repetition"
        boolean found = false;
        for (int l = 0; l < level; l++) {
          if (pickup[l] == c) {
            found = true;
            break;
          }
        }
        if (found) {
          continue; // next proposal please
        }
        pickup[level] = c;
        // check if the customer is happy, that he doesn't have to wait for too long
        int p_cost = 0;
        for (int l = 0; l < level; l++) {
          p_cost += cost[demand[pickup[l]].fromStand][demand[pickup[l + 1]].fromStand];
        }
        if (p_cost > MAX_WAIT_TIME) {
          continue;
        }
        // find the next customer
        poolv2(level + 1);
      }
    }
  }

  public static PoolElement[] findPool(TaxiOrder[] dem, int inPool) {
    MAX_IN_POOL = inPool;
    for (int i = 0; i < maxNumbStands; i++) {
      for (int j = i; j < maxNumbStands; j++) {
        cost[j][i] = dsrvc.getDistance(j, i); // simplification of distance - stop9 is closer to stop7 than to stop1
        cost[i][j] = cost[j][i];
      }
    }
    numb_cust = dem.length;
    demand = dem;
/*
    List<TaxiOrder> orders = taxiOrderRepository.findByStatus(TaxiOrder.OrderStatus.RECEIVED);
    int i = 0;
    for (TaxiOrder o : orders) {
      demand[i][ID] = o.getId().intValue();
      demand[i].fromStand = o.getFromStand();
      demand[i].toStand = o.getToStand();
      i++;
    }
*/
    // for (int i=0; i<numb_cust; i++)
    //     printf ("customer %d: from: %d to: %d\n", i, demand[i][0], demand[i][1]);
    System.out.println("numb_cust: " +  numb_cust);
    //show_cost();
    poolv2(0);
    // sorting
    PoolElement[] arr = poolList.toArray(new PoolElement[poolList.size()]);
    Arrays.sort(arr);
    // removin duplicates
    int i = 0;
    for (i = 0; i < arr.length; i++) {
      if (arr[i].cost == -1) {
        continue;
      }
      for (int j = i+1; j < arr.length; j++) {
        if (arr[j].cost != -1) { // not invalidated; for performance reasons
          boolean found = false;
          for (int x = 0; x < MAX_IN_POOL; x++) {
            for (int y = 0; y < MAX_IN_POOL; y++) {
              if (arr[j].cust[x] == arr[i].cust[y]) {
                found = true;
                break;
              }
            }
            if (found) {
              break;
            }
          }
          if (found) {
            arr[j].cost = -1; // duplicated
          }
        }
      }
    }
    // creation of routes - TODO: make it transactional
    int good_count = 0;
    for (i = 0; i < arr.length; i++) {
      if (arr[i].cost != -1) {
        good_count++;
        //savePool(arr[i]);
      }
    }
    System.out.println("Not duplicated count2: " + good_count);
    return arr;
  }

  public static TaxiOrder[] findFirstLegInPool(PoolElement[] arr, TaxiOrder[] tempDemand) {
    List<TaxiOrder> ret = new ArrayList<>();

    for (TaxiOrder td : tempDemand) {
      boolean found = false;
      for (PoolElement a : arr) {
        for (int j = 1; j < a.cust.length / 2; j++) { // 0: the first leg; /2 -> pickups + dropp-offs
          if (a.cust[j].id == td.id) { // this customer will be picked up by another customer should not be sent tol solver
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
