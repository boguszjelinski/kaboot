/*
 * Copyright 2022 Bogusz Jelinski
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
import no.kabina.kaboot.orders.TaxiOrder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class DynaPool2 {

  private DistanceService distSrvc;
  private TaxiOrder[] demand;
  private static final int MAX_IN_POOL = 8; // just for memory allocation, might be 10 as well
  private List<Branch>[] node;
  private int maxAngle;

  public DynaPool2(DistanceService srvc, int maxAngle) {
    this.distSrvc = srvc;
    this.maxAngle = maxAngle;
  }

  // for tests
  public DynaPool2(int [][] dists, int maxAngle) {
    this.maxAngle = maxAngle;
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
    if (inPool > MAX_IN_POOL) {
      // TASK log
      return new PoolElement[0];
    }
    this.demand = dem;
    initMem(inPool);
    // TASK: two threads for these two trees
    // the code is mirrored in these trees due to different source of data - from & to
    dive(0, inPool);
    List<PoolElement> poolList = getList(inPool);
    return PoolUtil.removeDuplicates(poolList.toArray(new PoolElement[0]), inPool);
  }

  private void initMem(int inPool) {
    node = new ArrayList[MAX_IN_POOL + MAX_IN_POOL - 1];
    for (int i = 0; i < inPool * inPool - 1; i++) {
      node[i] = new ArrayList<>();
    }
  }

  /** recursion to go through all stages but leaves of the tree
   * at each stage, starting with the last, calculate the permutation cost and store the branch if feasible
   *
   * @param lev starting with 0
   * @param inPool how many passengers can a cab take
   */
  private void dive(int lev, int inPool) {
    if (lev > inPool + inPool - 3) { // lev >= 2*inPool-2, where -2 are last two levels
      storeLeaves(lev);
      return; // last two levels are "leaves"
    }
    dive(lev + 1, inPool);

    for (int c = 0; c < demand.length; c++) {
      for (Branch b : node[lev + 1]) { // we iterate over product of the stage further in the tree: +1
        storeBranchIfNotFoundDeeperAndNotTooLong(lev, c, b, inPool);
      }
    }
    // removing duplicates which come from lev+1,
    // as for that level it does not matter which order is better in stages towards leaves
    node[lev] = rmDuplicates(node[lev], lev, true); // true - make hash
  }

  private void storeBranchIfNotFoundDeeperAndNotTooLong(int lev, int c, Branch b, int inPool) {
    // isFoundInBranchOrTooLong(int c, Branch b)
    // to situations: c IN and c OUT
    // c IN has to have c OUT in level+1, and c IN cannot exist in level + 1
    // c OUT cannot have c OUT in level +1
    boolean inFound = false;
    boolean outFound = false;
    for (int i = 0; i < b.custIDs.length; i++) {
      if (b.custIDs[i] == c) {
        if (b.custActions[i] == 'i') {
          inFound = true;
        } else {
          outFound = true;
        }
        // current passenger is in the branch below
      }
    }
    // now checking if anyone in the branch does not lose too much with the pool
    // c IN
    int nextStop = b.custActions[0] == 'i' ? demand[b.custIDs[0]].fromStand : demand[b.custIDs[0]].toStand;
    if (!inFound
        && outFound
        && !isTooLong(distSrvc.getDistances()[demand[c].fromStand][nextStop], b)
        // TASK? if the next stop is OUT of passenger 'c' - we might allow bigger angle
        && bearingDiff(distSrvc.bearing[demand[c].fromStand], distSrvc.bearing[nextStop]) < maxAngle) {
      storeBranch('i', lev, c, b, inPool);
    }
    // c OUT
    if (lev > 0 // the first stop cannot be OUT
        && b.outs < inPool // numb OUT must be numb IN
        && !outFound // there is no such OUT later on
        && !isTooLong(distSrvc.getDistances()[demand[c].toStand][nextStop], b)
        && bearingDiff(distSrvc.bearing[demand[c].toStand], distSrvc.bearing[nextStop]) < maxAngle) {
      storeBranch('o', lev, c, b, inPool);
    }
  }

  private void storeBranch(char action, int lev, int c, Branch b, int inPool) {
    int len = inPool + inPool - lev;
    int[] drops = new int[len];
    drops[0] = c;
    char[] actions = new char[len];
    actions[0] = action;
    // !! System.arraycopy is slower
    for (int j = 0; j < len - 1; j++) { // further stage has one passenger less: -1
      drops[j + 1] = b.custIDs[j];
      actions[j + 1] = b.custActions[j];
    }
    Branch b2 = new Branch(c + action + "-" + b.key, // no sorting as we have to remove lev+1 duplicates eg. 1-4-5 and 1-5-4
                      distSrvc.getDistances()[action == 'i' ? demand[c].fromStand : demand[c].toStand]
                      [b.custActions[0] == 'i' ? demand[b.custIDs[0]].fromStand : demand[b.custIDs[0]].toStand] + b.cost,
                      action == 'o' ? b.outs + 1: b.outs, drops, actions);
    node[lev].add(b2);
  }

  private boolean isTooLong(int wait, Branch b) {
    for (int i = 0; i < b.custIDs.length; i++) {
      if (wait > distSrvc.getDistances()[demand[b.custIDs[i]].fromStand][demand[b.custIDs[i]].toStand]
                  * (100.0 + demand[b.custIDs[i]].getMaxLoss()) / 100.0) {
        return true;
      }
      if (i + 1 < b.custIDs.length) {
        wait += distSrvc.getDistances()[b.custActions[i] == 'i' ? demand[b.custIDs[i]].fromStand : demand[b.custIDs[i]].toStand]
                                       [b.custActions[i + 1] == 'i' ? demand[b.custIDs[i + 1]].fromStand : demand[b.custIDs[i + 1]].toStand];
      }
    }
    return false;
  }

  private void storeLeaves(int lev) {
    for (int c = 0; c < demand.length; c++) {
      for (int d = 0; d < demand.length; d++) {
        // to situations: <1in, 1out>, <1out, 2out>
        if (c == d)  {
          // IN and OUT of the same passenger, we don't check bearing as they are probably distant stops
          addBranch(c, d, 'i', 'o', 1, lev);
        } else if (distSrvc.getDistances()[demand[c].toStand][demand[d].toStand]
                    < distSrvc.getDistances()[demand[d].fromStand][demand[d].toStand] * (100.0 + demand[d].getMaxLoss()) / 100.0
                && bearingDiff(distSrvc.bearing[demand[c].toStand], distSrvc.bearing[demand[d].toStand]) < maxAngle
        ) {
          // TASK - this calculation above should be replaced by a redundant value in taxi_order - distance * loss
          addBranch(c, d, 'o', 'o', 2, lev);
        }
      }
    }
  }

  public static int bearingDiff(int a, int b) {
    int r = (a - b) % 360;
    if (r < -180.0) {
      r += 360.0;
    } else if (r >= 180.0) {
      r -= 360.0;
    }
    return Math.abs(r);
  }

  private void addBranch(int id1, int id2, char dir1, char dir2, int outs, int lev) {
    int[] ids = new int[2];
    ids[0] = id1;
    ids[1] = id2;
    char[] dirs = new char[2];
    dirs[0] = dir1;
    dirs[1] = dir2;
    Branch b = new Branch(id1 < id2 ? id1 + dir1 + "-" + id2 + dir2 : id2 + dir2 + "-" + id1 + dir1, // if id1 == id2 then both are OK :)
                          distSrvc.getDistances()[demand[id1].toStand][demand[id2].toStand], 
                          outs, ids, dirs);
    node[lev].add(b);
  }

  private List<Branch> rmDuplicates(List<Branch> node, int lev, boolean makeHash) {
    Branch[] arr = node.toArray(new Branch[0]);
    if (arr.length < 2) { //TASK: why not < 1
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
          int[] copiedIDs = Arrays.copyOf(branch.custIDs, branch.custIDs.length);
          //Arrays.sort(copiedArray);
          key = "";
          for (int j = 0; j < copiedIDs.length; j++) {
            int idx = idxOfMin(copiedIDs);
            key += copiedIDs[idx] + branch.custActions[idx]; // TASK: stringbuilder
            if (j < copiedIDs.length - 1) {
              key += "-";
            }
            copiedIDs[idx] = 100000; // any MAX TASK: magic ID
          }
          branch.key = key;
        }
        list.add(branch);
      }
    }
    return list;
  }

  public static int idxOfMin(int[] arr) {
    int idx = 0;
    for (int i = 1; i < arr.length; i++) {
        if (arr[i] < arr[idx]) {
            idx = i;
        }
    }
    return idx;
  }

  private List<PoolElement> getList(int inPool) {
    List<PoolElement> ret = new ArrayList<>();

    for (Branch p : node[0]) {
      TaxiOrder[] orders = new TaxiOrder[p.custIDs.length];
      for (int i = 0; i < p.custIDs.length; i++) {
          orders[i] = demand[p.custIDs[i]];
      }
      ret.add(new PoolElement(orders, inPool, p.cost));
    }
    return ret;
  }

  class Branch implements Comparable<Branch> {
    public String key; // used to remove duplicates and search in hashmap
    public int cost;
    public int outs; // number of OUT nodes, so that we can guarantee enough IN nodes
    public int[] custIDs; // we could get rid of it to gain on memory (key stores this too); but we would lose time on parsing
    public char[] custActions;

    Branch (String key, int cost, int[] drops) {
      this.key = key;
      this.cost = cost;
      this.custIDs = drops;
    }

    Branch (String key, int cost, int outs, int[] ids, char[] actions) {
      this.key = key;
      this.cost = cost;
      this.outs = outs;
      this.custIDs = ids;
      this.custActions = actions;
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
