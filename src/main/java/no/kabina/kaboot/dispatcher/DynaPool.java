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
import java.util.HashMap;
import java.util.List;
import no.kabina.kaboot.orders.TaxiOrder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class DynaPool {

  private DistanceService distSrvc;

  TaxiOrder[] demand;
  private static final int MAX_IN_POOL = 6; // just for memory allocation, might be 10 as well

  List<Branch>[] node;
  List<Branch>[] nodeP;
  HashMap<String, Branch> map = new HashMap<>();

  public DynaPool(DistanceService srvc) {
    this.distSrvc = srvc;
  }

  // for tests
  public DynaPool(int [][] dists) {
    if (distSrvc == null) {
      distSrvc = new DistanceService(dists);
    }
  }

  /**
   *
   * @param dem requests from potential passengers
   * @param inPool how many passengers can a cab take
   * @return array of pools
   */
  public PoolElement[] findPool(TaxiOrder[] dem, int inPool) {
    this.demand = dem;
    initMem(inPool);
    // TASK: two threads for these two trees
    // the code is mirrored in these trees due to different source of data - from & to
    dive(0, inPool);
    diveP(0, inPool);
    List<PoolElement> poolList = mergeTrees(inPool);
    return PoolUtil.removeDuplicates(poolList.toArray(new PoolElement[0]), inPool);
  }

  private void initMem(int inPool) {
    node = new ArrayList[MAX_IN_POOL - 1];
    nodeP = new ArrayList[MAX_IN_POOL - 1];
    for (int i = 0; i < inPool - 1; i++) {
      node[i] = new ArrayList<>();
      nodeP[i] = new ArrayList<>();
    }
  }

  /** recursion to go through all stages but leaves of the tree
   * at each stage, starting with the last, calculate the permutation cost and store the branch if feasible
   *
   * @param lev starting with 0
   * @param inPool how many passengers can a cab take
   */
  private void dive(int lev, int inPool) {
    if (lev > inPool - 3) {
      storeLeaves(inPool);
      return; // last two levels are "leaves"
    }
    dive(lev + 1, inPool);

    for (int c = 0; c < demand.length; c++) {
      for (Branch b : node[lev + 1]) { // we iterate over product of the stage further in the tree: +1
        if (!isFoundInBranchOrTooLong(c, b)) { // one of two in 'b' is dropped off earlier
          storeBranch(lev, c, b, inPool);
        }
      }
    }
    // removing duplicates which come from lev+1,
    // as for that level it does not matter which order is better in stages towards leaves
    node[lev] = rmDuplicates(node[lev], lev, true); // true - make hash
  }

  private void storeBranch(int lev, int c, Branch b, int inPool) {
    int[] drops = new int[inPool - lev];
    drops[0] = c;
    // !! System.arraycopy is slower
    for (int j = 0; j < inPool - lev - 1; j++) { // further stage has one passenger less: -1
      drops[j + 1] = b.custIDs[j];
    }
    Branch b2 = new Branch(c + "-" + b.key, // no sorting as we have to remove lev+1 duplicates eg. 1-4-5 and 1-5-4
                      distSrvc.getDistances()[demand[c].toStand][demand[b.custIDs[0]].toStand] + b.cost,
                          drops);
    node[lev].add(b2);
  }

  private void storeLeaves(int inPool) {
    for (int c = 0; c < demand.length; c++) {
      for (int d = 0; d < demand.length; d++) {
        if (c != d
                && distSrvc.getDistances()[demand[c].toStand][demand[d].toStand]
                        < distSrvc.getDistances()[demand[d].fromStand][demand[d].toStand] * (100.0 + demand[d].getMaxLoss()) / 100.0) {
          int[] drops = new int[2];
          drops[0] = c;
          drops[1] = d;
          String key = c > d ? d + "-" + c : c + "-" + d;
          Branch b = new Branch(key, distSrvc.getDistances()[demand[c].toStand][demand[d].toStand], drops);
          node[inPool - 2].add(b);
        }
      }
    }
  }

  private void storePickUpLeaves(int inPool) {
    for (int c = 0; c < demand.length; c++) {
      for (int d = 0; d < demand.length; d++) {
        if (c != d
                && distSrvc.getDistances()[demand[c].fromStand][demand[d].fromStand]
                        < distSrvc.getDistances()[demand[d].fromStand][demand[d].toStand] * (100.0 + demand[d].getMaxLoss()) / 100.0) {
          int[] pickUp = new int[2];
          pickUp[0] = c;
          pickUp[1] = d;
          String key = c > d ? d + "-" + c : c + "-" + d;
          Branch b = new Branch(key, distSrvc.getDistances()[demand[c].fromStand][demand[d].fromStand], pickUp);
          nodeP[inPool - 2].add(b);
        }
      }
    }
  }

  private void diveP(int lev, int inPool) {
    if (lev > inPool - 3) {
      storePickUpLeaves(inPool);
      return; // last two levels are "leaves"
    }
    diveP(lev + 1, inPool);

    for (int c = 0; c < demand.length; c++) {
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
    // !! System.arraycopy is slower
    for (int j = 0; j < inPool - lev - 1; j++) { // further stage has one passenger less: -1
      pickups[j + 1] = b.custIDs[j];
    }
    Branch b2 = new Branch(c + "-" + b.key, // no sorting as we have to remove lev+1 duplicates eg. 1-4-5 and 1-5-4
                      distSrvc.getDistances()[demand[c].fromStand][demand[b.custIDs[0]].fromStand] + b.cost,
                          pickups);
    nodeP[lev].add(b2);
  }

  private boolean isFoundInBranchOrTooLong(int c, Branch b) {
    for (int i = 0; i < b.custIDs.length; i++) {
      if (b.custIDs[i] == c) {
        return true; // current passenger is in the branch below -> reject that combination
      }
    }
    // now checking if anyone in the branch does not lose too much with the pool
    int wait = distSrvc.getDistances()[demand[c].toStand][demand[b.custIDs[0]].toStand];

    for (int i = 0; i < b.custIDs.length; i++) {
      if (wait > distSrvc.getDistances()[demand[b.custIDs[i]].fromStand][demand[b.custIDs[i]].toStand]
                  * (100.0 + demand[b.custIDs[i]].getMaxLoss()) / 100.0) {
        return true;
      }
      if (i + 1 < b.custIDs.length) {
        wait += distSrvc.getDistances()[demand[b.custIDs[i]].toStand][demand[b.custIDs[i + 1]].toStand];
      }
    }
    return false;
  }

  private boolean isFoundInBranchOrTooLongP(int c, Branch b) {
    for (int i = 0; i < b.custIDs.length; i++) {
      if (b.custIDs[i] == c) {
        return true; // current passenger is in the branch below -> reject that combination
      }
    }
    // now checking if c does not lose too much with the pool
    int wait = distSrvc.getDistances()[demand[c].fromStand][demand[b.custIDs[0]].fromStand];

    for (int i = 0; i < b.custIDs.length; i++) {
      if (wait > distSrvc.getDistances()[demand[c].fromStand][demand[c].toStand]
                * (100.0 + demand[c].getMaxLoss()) / 100.0) {
        return true;
      }
      if (i + 1 < b.custIDs.length) {
        wait += distSrvc.getDistances()[demand[b.custIDs[i]].fromStand][demand[b.custIDs[i + 1]].fromStand];
      }
    }
    return false;
  }

  private List<Branch> rmDuplicates(List<Branch> node, int lev, boolean makeHash) {
    Branch[] arr = node.toArray(new Branch[0]);
    if (arr.length < 2) {
      return new ArrayList<>();
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
          int[] copiedArray = Arrays.copyOf(branch.custIDs, branch.custIDs.length);
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
        if (d == null || !allAreHappy(inPool, p, d)) {
          continue; // drop-off was not acceptable
        }
        // a viable plan -> merge pickup and dropoff
        TaxiOrder[] both = new TaxiOrder[p.custIDs.length + d.custIDs.length];
        int i = 0;
        for (; i < p.custIDs.length; i++) {
          both[i] = demand[p.custIDs[i]];
        }
        // TASK: Use "Arrays.copyOf", "Arrays.asList", "Collections.addAll" or "System.arraycopy" instead.
        for (; i < p.custIDs.length + d.custIDs.length; i++) {
          both[i] = demand[d.custIDs[i - p.custIDs.length]];
        }
        int cst = p.cost
                  + distSrvc.getDistances()[demand[p.custIDs[p.custIDs.length - 1]].fromStand][demand[d.custIDs[0]].toStand]
                  + d.cost;
        ret.add(new PoolElement(both, inPool, cst));
      }
    }
    return ret;
  }

  private boolean allAreHappy(int inPool, Branch p, Branch d) {
    // checking if drop-off still acceptable with that pick-up phase merged
    // we have to check all passengers again in the drop-off phase,
    // TASK: there must a method to write this code in a shorter way, it looks ugly ...
    for (int c = 0; c < inPool; c++) {
      int wait = 0;  // the trip does not start for all at the same stop, some get picked up later than others
      // pick up phase
      for (int i = c; i < inPool - 1; i++) {
        wait += distSrvc.getDistances()[demand[p.custIDs[i]].fromStand][demand[p.custIDs[i + 1]].fromStand];
      }
      // transition from pickup to dropoff
      wait += distSrvc.getDistances()[demand[p.custIDs[p.custIDs.length-1]].fromStand][demand[d.custIDs[0]].toStand];
      // drop off phase
      int i = 0;
      while (p.custIDs[c] != d.custIDs[i]/* && i != d.dropoff.length*/) {
        wait += distSrvc.getDistances()[demand[d.custIDs[i]].toStand][demand[d.custIDs[i + 1]].toStand];
        i++;
      }
      // now the verdict; this 'if' might be executed after pick-up above too
      if (wait > distSrvc.getDistances()[demand[p.custIDs[c]].fromStand][demand[p.custIDs[c]].toStand]
              * (1.0 + demand[p.custIDs[c]].getMaxLoss() / 100.0)) {
        return false;
      }
    }
    return true;
  }

  private String genHashKey(Branch p, int k, int inPool) {
    int [] tab = new int[inPool];
    tab[0] = -1; // -1 will always come first during sorting, 
    // now the other three passengers
    for (int i = 0, j = 1; i < inPool; i++) {
      if (i == k) {
        continue; // we have this one, see below tab[0]=...
      }
      tab[j++] = p.custIDs[i];
    }
    Arrays.sort(tab);
    tab[0] = p.custIDs[k];

    String key = "";
    for (int j = 0; j < tab.length; j++) {
      key += tab[j];
      if (j < tab.length - 1) {
        key += "-";
      }
    }
    return key;
  }

  class Branch implements Comparable<Branch> {
    public String key; // used to remove duplicates and search in hashmap
    public int cost;
    public int[] custIDs; // we could get rid of it to gain on memory (key stores this too); but we would lose time on parsing

    Branch (String key, int cost, int[] drops) {
      this.key = key;
      this.cost = cost;
      this.custIDs = drops;
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
              .append(custIDs[0])
              .append(custIDs[1])
              .toHashCode();
    }
  }
}
