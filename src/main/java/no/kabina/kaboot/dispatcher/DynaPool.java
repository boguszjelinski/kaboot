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

import no.kabina.kaboot.orders.TaxiOrder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class DynaPool {

  private static final Logger logger = LoggerFactory.getLogger(DynaPool.class);
  
  private int maxNumbStands;

  TaxiOrder[] demand;
  int [][]cost;
  private static final int MAX_IN_POOL = 4; // just for memory allocation, might be 10 as well

  List<Branch>[] node = new ArrayList[MAX_IN_POOL-1];
  List<Branch>[] nodeP = new ArrayList[MAX_IN_POOL-1];
  HashMap<String, Branch> map = new HashMap<>();

  public DynaPool(int stands) {
    this.maxNumbStands = stands;
    this.cost = setCosts();
  }

  /**
   *
   * @param dem
   * @param inPool how many passengers can a cab take
   * @return
   */
  public PoolElement[] findPool(TaxiOrder[] dem, int inPool) {
    this.demand = dem;
    initMem(inPool);
    // TASK: two threads
    storeLeaves(inPool, demand.length);
    storePickUpLeaves(inPool, demand.length);
    dive(0, inPool, demand.length);
    diveP(0, inPool, demand.length);
    List<PoolElement> poolList = mergeTrees(inPool);
    return PoolUtil.removeDuplicates(poolList.toArray(new PoolElement[0]), inPool);
  }

  private void initMem(int inPool) {
    for (int i = 0; i < inPool - 1; i++) {
      node[i] = new ArrayList<>();
      nodeP[i] = new ArrayList<>();
    }
  }

  private void dive(int lev, int inPool, int custNumb) {
    if (lev > inPool - 3) {
      return; // last two levels are "leaves"
    }
    dive(lev + 1, inPool, custNumb);

    for (int c = 0; c < custNumb; c++) {
      for (Branch b : node[lev + 1]) { // we iterate over product of the stage further in the tree: +1
        if (!isFoundInBranchOrTooLong(c, b)) { // one of two in 'b' is dropped off earlier
          storeBranch(lev, c, b, inPool);
        }
      }
    }
    // removing duplicates which come from lev+1
    node[lev] = rmDuplicates(node[lev], lev, true); // true - make hash
  }

  private void storeBranch(int lev, int c, Branch b, int inPool) {
    int[] drops = new int[inPool - lev];
    drops[0] = c;
    for (int j = 0; j < inPool - lev - 1; j++) { // further stage has one passenger less: -1
      drops[j + 1] = b.dropoff[j];
    }
    Branch b2 = new Branch(c + "-" + b.key, // no sorting as we have to remove lev+1 duplicates eg. 1-4-5 and 1-5-4
                      cost[demand[c].toStand][demand[b.dropoff[0]].toStand] + b.cost,
                          drops);
    node[lev].add(b2);
  }

  private void storeLeaves(int inPool, int custNumb) {
    for (int c = 0; c < custNumb; c++) {
      for (int d = 0; d < custNumb; d++) {
        if (c != d
                && cost[demand[c].toStand][demand[d].toStand]
                        < cost[demand[d].fromStand][demand[d].toStand] * (100.0 + demand[d].getMaxLoss()) / 100.0) {
          int[] drops = new int[2];
          drops[0] = c;
          drops[1] = d;
          String key = c > d ? d + "-" + c : c + "-" + d;
          Branch b = new Branch(key, cost[demand[c].toStand][demand[d].toStand], drops);
          node[inPool - 2].add(b);
        }
      }
    }
  }

  private void storePickUpLeaves(int inPool, int custNumb) {
    for (int c = 0; c < custNumb; c++) {
      for (int d = 0; d < custNumb; d++) {
        if (c != d
                && cost[demand[c].fromStand][demand[d].fromStand]
                        < cost[demand[d].fromStand][demand[d].toStand] * (100.0 + demand[d].getMaxLoss()) / 100.0) {
          int[] pickUp = new int[2];
          pickUp[0] = c;
          pickUp[1] = d;
          String key = c > d ? d + "-" + c : c + "-" + d;
          Branch b = new Branch(key, cost[demand[c].fromStand][demand[d].fromStand], pickUp);
          nodeP[inPool - 2].add(b);
        }
      }
    }
  }

  private void diveP(int lev, int inPool, int custNumb) {
    if (lev > inPool - 3) {
      return; // last two levels are "leaves"
    }
    diveP(lev + 1, inPool, custNumb);

    for (int c = 0; c < custNumb; c++) {
      for (Branch b : nodeP[lev + 1]) { // we iterate over product of the stage further in the tree: +1
        if (!isFoundInBranchOrTooLongP(c, b)) { // one of two in 'b' is dropped off earlier
          storeBranchP(lev, c, b, inPool);
        }
      }
    }
    // removing duplicates which come from lev+1
    nodeP[lev] = rmDuplicates(nodeP[lev], lev, false);
  }

  private void storeBranchP(int lev, int c, Branch b, int inPool) {
    int[] pickups = new int[inPool - lev];
    pickups[0] = c;
    for(int j = 0; j < inPool - lev - 1; j++) { // further stage has one passenger less: -1
      pickups[j + 1] = b.dropoff[j];
    }
    Branch b2 = new Branch(c + "-" + b.key, // no sorting as we have to remove lev+1 duplicates eg. 1-4-5 and 1-5-4
                      cost[demand[c].fromStand][demand[b.dropoff[0]].fromStand] + b.cost,
                          pickups);
    nodeP[lev].add(b2);
  }

  private boolean isFoundInBranchOrTooLong(int c, Branch b) {
    for (int i = 0; i < b.dropoff.length; i++) {
      if (b.dropoff[i] == c) {
        return true; // current passenger is in the branch below -> reject that combination
      }
    }
    // now checking if anyone in the branch does not lose too much with the pool
    int wait = cost[demand[c].toStand][demand[b.dropoff[0]].toStand];

    for (int i = 0; i < b.dropoff.length; i++) {
      if (wait > cost[demand[b.dropoff[i]].fromStand][demand[b.dropoff[i]].toStand]
                  * (100.0 + demand[b.dropoff[i]].getMaxLoss()) / 100.0) {
        return true;
      }
      if (i + 1 < b.dropoff.length) {
        wait += cost[demand[b.dropoff[i]].toStand][demand[b.dropoff[i + 1]].toStand];
      }
    }
    return false;
  }

  private boolean isFoundInBranchOrTooLongP(int c, Branch b) {
    for (int i = 0; i < b.dropoff.length; i++) {
      if (b.dropoff[i] == c) {
        return true; // current passenger is in the branch below -> reject that combination
      }
    }
    // now checking if anyone in the branch does not lose too much with the pool
    int wait = cost[demand[c].fromStand][demand[b.dropoff[0]].fromStand];

    for (int i = 0; i < b.dropoff.length; i++) {
      if (wait > cost[demand[b.dropoff[i]].fromStand][demand[b.dropoff[i]].toStand]
                * (100.0 + demand[b.dropoff[i]].getMaxLoss()) / 100.0) {
        return true;
      }
      if (i + 1 < b.dropoff.length) {
        wait += cost[demand[b.dropoff[i]].fromStand][demand[b.dropoff[i + 1]].fromStand];
      }
    }
    return false;
  }

  private List<Branch> rmDuplicates(List<Branch> node, int lev, boolean makeHash) {
    Branch[] arr = node.toArray(new Branch[0]);
    if (arr == null || arr.length < 2) {
      return null;
    }

    // removing duplicates from the previous stage
    // TASK: there might be more duplicates than one at lev==1 or 0 !!!!!!!!!!!!!
    Arrays.sort(arr);

    for (int i = 0; i < arr.length - 1; i++) {
      if (arr[i].cost == -1) { // this -1 marker is set below
        continue;
      }
      if (arr[i].key.equals(arr[i + 1].key)) {
        if (arr[i].cost > arr[i + 1].cost) {
          arr[i].cost = -1; // to be deleted
        } else {
          arr[i + 1].cost = -1;
        }
      }
    }
    // removing but also recreating the key - must be sorted

    List<Branch> list = new ArrayList<>();

    for (Branch branch : arr) {
      if (branch.cost != -1) {
        String key = branch.key;
        if (lev > 0 || !makeHash) { // do not sort, it also means - for lev 0 there will be MAX_IN_POOL
          // maps for the same combination, which is great for tree merging
          // do sort if lev==0 in pick-up tree (!makeHash)
          int[] copiedArray = Arrays.copyOf(branch.dropoff, branch.dropoff.length);
          Arrays.sort(copiedArray);
          key = "";
          for (int j = 0; j < copiedArray.length; j++) {
            key += copiedArray[j];
            if (j < copiedArray.length - 1) {
              key += "-";
            }
          }
          branch.key = key;
        }
        if (lev == 0 && makeHash) {
          map.put(key, branch);
        } else {
          list.add(branch);
        }
      }
    }
    /*
    for (int i = 0; i < arr.length; i++) {
      if (arr[i].cost != -1) {
        String key = arr[i].key;
        if (lev > 0) { // do not sort, it also means - for lev 0 there will be MAX_IN_POOL maps for the same combination, which is great for tree merging
          int[] copiedArray = Arrays.copyOf(arr[i].dropoff, arr[i].dropoff.length);
          Arrays.sort(copiedArray);
          key = "";
          for (int j = 0; j < copiedArray.length; j++) {
            key += copiedArray[j];
            if (j < copiedArray.length - 1) key += "-";
          }
          arr[i].key = key;
        }
        if (lev == 0 && makeHash) map.put(key, arr[i]);
        else list.add(arr[i]);
      }
    }
    */
    return list;
  }

  private List<PoolElement> mergeTrees(int inPool) {
    List<PoolElement> ret = new ArrayList<>();

    for (Branch p : nodeP[0]) {
      // there might be as many as MAX_IN_POOL hash keys (drop-off plans) for this pick-up plan
      // which means MAX_IN_POOL possible plans
      for (int k = 0; k < inPool; k++) {
        String key = genHashKey(p, k, inPool);

        Branch d = map.get(key); // get drop-offs for that key
        if (d == null) {
          continue; // drop-off was not acceptable
        }
        // checking if drop-off still acceptable with that pick-up phase merged
        boolean tooLong = false;
        // we have to check all passengers again in the drop-off phase,
        // TASK: there must a method to write this code in a shorter way, it looks ugly ...
        for (int c = 0; c < inPool && !tooLong; c++) {
          int wait = 0;  // the trip does not start for all at the same stop, some get picked up later than others
          // pick up phase
          for (int ii = c; ii < inPool - 1; ii++) {
            wait += cost[demand[p.dropoff[ii]].fromStand][demand[p.dropoff[ii+1]].fromStand];
          }
          // transition from pickup to dropoff
          wait += cost[demand[p.dropoff[p.dropoff.length-1]].fromStand][demand[d.dropoff[0]].toStand];
          if (wait > cost[demand[d.dropoff[0]].fromStand][demand[d.dropoff[0]].toStand]
                  * (1.0 + demand[d.dropoff[0]].getMaxLoss() / 100.0)) {
            tooLong = true;
            break;
          }
          // drop off phase
          int i = 0;
          while (p.dropoff[c] != d.dropoff[i]/* && i != d.dropoff.length*/) {
            wait += cost[demand[d.dropoff[i]].toStand][demand[d.dropoff[i + 1]].toStand];
            i++;
            if (wait > cost[demand[d.dropoff[i]].fromStand][demand[d.dropoff[i]].toStand]
                    * (1.0 + demand[d.dropoff[i]].getMaxLoss() / 100.0)) {
              tooLong = true;
              break;
            }
          }
        }
        if (tooLong) {
          continue;
        }
        // a viable plan -> merge pickup and dropoff
        TaxiOrder[] both = new TaxiOrder[p.dropoff.length + d.dropoff.length];
        int i = 0;
        for (; i < p.dropoff.length; i++) {
          both[i] = demand[p.dropoff[i]];
        }
        // TASK: Use "Arrays.copyOf", "Arrays.asList", "Collections.addAll" or "System.arraycopy" instead.
        for (; i < p.dropoff.length + d.dropoff.length; i++) {
          both[i] = demand[d.dropoff[i - p.dropoff.length]];
        }
        int cst = p.cost + cost[demand[p.dropoff[p.dropoff.length - 1]].fromStand][demand[d.dropoff[0]].fromStand]
                    + d.cost;
        ret.add(new PoolElement(both, inPool, cst));
      }
    }
    return ret;
  }

  private int calcCost(int[] pickups, int k, int inPool) {
    int sum = 0;
    for (int i = k; i < inPool - 1; i++) {
      sum += cost[demand[pickups[i]].fromStand][demand[pickups[i+1]].fromStand];
    }
    return sum;
  }

  private String genHashKey(Branch p, int k, int inPool) {
    int [] tab = new int[inPool];
    tab[0] = -1; // -1 will always come first during sorting, 
    // now the other three passengers
    for (int i = 0, j = 1; i < inPool; i++) {
      if (i == k) {
        continue; // we have this one, see above
      }
      tab[j++] = p.dropoff[i];
    }
    Arrays.sort(tab);
    tab[0] = p.dropoff[k];

    String key = "";
    for (int j = 0; j < tab.length; j++) {
      key += tab[j];
      if (j < tab.length - 1) {
        key += "-";
      }
    }
    return key;
  }

  private int[][] setCosts() {
    int[][] costMatrix = new int[maxNumbStands][maxNumbStands];
    for (int i = 0; i < maxNumbStands; i++) {
      for (int j = i; j < maxNumbStands; j++) {
        // TASK: should we use BIG_COST to mark stands very distant from any cab ?
        costMatrix[j][i] = DistanceService.getDistance(j, i); // simplification of distance - stop9 is closer to stop7 than to stop1
        costMatrix[i][j] = costMatrix[j][i];
      }
    }
    return costMatrix;
  }
  
  class Branch implements Comparable<Branch> {
    public String key; // used to remove duplicates and search in hashmap
    public int cost;
    public int[] dropoff; // we could get rid of it to gain on memory (key stores this too); but we would lose time on parsing 

    Branch (String key, int cost, int[] drops) {
      this.key = key;
      this.cost = cost;
      this.dropoff = drops;
    }

    @Override
    public int compareTo(Branch pool) {
      return this.key.compareTo(pool.key);
    }

    @Override
    public boolean equals(Object pool) {
      if (pool == null || this.getClass() != pool.getClass()) {
        return false;
      }
      return this.key.equals(((Branch) pool).key);
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder(17, 37)
              .append(key)
              .append(cost)
              .append(dropoff[0])
              .append(dropoff[1])
              .toHashCode();
    }
    /*
    @Override
    public int hashCode() {
      int result = (int) (dropoff[0] ^ (dropoff[0] >>> 32));
      result = 31 * result + cost;
      result = 31 * result + dropoff[1];
      return result;
    }
     */
  }
}
