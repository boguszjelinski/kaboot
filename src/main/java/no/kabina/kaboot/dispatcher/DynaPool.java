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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import no.kabina.kaboot.orders.TaxiOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DynaPool {

  private final Logger logger = LoggerFactory.getLogger(DynaPool.class);

  private TaxiOrder[] demand;
  public static final int MAX_IN_POOL = 8; // just for memory allocation, might be 10 as well
  public static final int MAX_THREAD = 8;
  private List<Branch>[] node;

  @Value("${kaboot.scheduler.max-angle}")
  private int maxAngle;

  @Autowired
  DistanceService distSrvc;

  @Autowired
  DynaPoolAsync asyncUtil;

  public DynaPool() {}

  public DynaPool(DistanceService srvc, DynaPoolAsync asyncUtil) {
    this.distSrvc = srvc;
    this.asyncUtil = asyncUtil;
  }

  public DynaPool(DistanceService srvc, DynaPoolAsync asyncUtil, int maxAngle) {
    this.distSrvc = srvc;
    this.maxAngle = maxAngle;
    this.asyncUtil = asyncUtil;
  }

  public void setDemand(TaxiOrder[] demand) {
    this.demand = demand;
  }

  /**  Constructor for tests.
   */
  public DynaPool(int [][] dists, int[] bearing, int maxAngle) {
    this.maxAngle = maxAngle;
    if (distSrvc == null) {
      distSrvc = new DistanceService(dists, bearing);
    }
    if (asyncUtil == null) {
      asyncUtil = new DynaPoolAsync(distSrvc);
    }
  }

  /**  Constructor for tests.
   */
  public PoolElement[] findPool(TaxiOrder[] dem, int inPool, int threadsNumb) {
    if (inPool > MAX_IN_POOL) {
      // TASK log
      return new PoolElement[0];
    }
    setDemand(dem);
    asyncUtil.setDemand(dem);
    initMem(inPool);
    dive(0, inPool, threadsNumb);
    String logStr = "";
    for (int i = 0; i < inPool + inPool - 1; i++) {
      logStr += "node[" + i + "].size: " + node[i].size() + ", ";
    }
    logger.debug("Pool nodes size: {}", logStr);
    List<PoolElement> poolList = getList(inPool);
    return PoolUtil.removeDuplicates(poolList.toArray(new PoolElement[0]), inPool);
  }

  public void initMem(int inPool) {
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
  public void dive(int lev, int inPool, int threadsNumb) {
    if (lev > inPool + inPool - 3) { // lev >= 2*inPool-2, where -2 are last two levels
      storeLeaves(lev);
      return; // last two levels are "leaves"
    }
    dive(lev + 1, inPool, threadsNumb);

    int step = demand.length / threadsNumb;
    threadsNumb += demand.length % threadsNumb > 0 ? 1 : 0;
    // +1 as there might be one "tail" thread from division below (step)
    CompletableFuture<List<Branch>>[] arr = new CompletableFuture[threadsNumb];

    for (int thread = 0, cust = 0; cust < demand.length; cust += step, thread++) {
      arr[thread] = asyncUtil.checkCustRange(cust, Math.min(cust + step, demand.length), lev, inPool, node[lev + 1]);
    }
    CompletableFuture.allOf(arr);
    // integrate results from multiple threads
    for (int i = 0; i < threadsNumb; i++) {
      try {
        if (arr[i] != null && arr[i].get() != null) {
          node[lev].addAll(arr[i].get());
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
      } catch (ExecutionException e) {
        e.printStackTrace();
      }
    }
    // removing duplicates which come from lev+1,
    // as for that level it does not matter which order is better in stages towards leaves
    node[lev] = rmDuplicates(node[lev], lev);
  }

  private void storeLeaves(int lev) {
    for (int c = 0; c < demand.length; c++) {
      for (int d = 0; d < demand.length; d++) {
        // two situations: <1in, 1out>, <1out, 2out>
        if (c == d)  {
          // IN and OUT of the same passenger, we don't check bearing as they are
          // probably distant stops
          addBranch(c, d, 'i', 'o', 1, lev);
        } else if (distSrvc.getDistances()[demand[c].toStand][demand[d].toStand]
                    < distSrvc.getDistances()[demand[d].fromStand][demand[d].toStand]
                      * (100.0 + demand[d].getMaxLoss()) / 100.0
                && PoolUtil.bearingDiff(distSrvc.bearing[demand[c].toStand],
                               distSrvc.bearing[demand[d].toStand]) < maxAngle
        ) {
          // TASK - this calculation above should be replaced by
          // a redundant value in taxi_order - distance * loss
          addBranch(c, d, 'o', 'o', 2, lev);
        }
      }
    }
  }

  private void addBranch(int id1, int id2, char dir1, char dir2, int outs, int lev) {
    int[] ids = new int[2];
    ids[0] = id1;
    ids[1] = id2;
    char[] dirs = new char[2];
    dirs[0] = dir1;
    dirs[1] = dir2;
    int[] sortedIds = new int[2];
    char[] sortedDirs = new char[2];
    if (id1 < id2) {
      sortedIds[0] = id1;
      sortedIds[1] = id2;
      sortedDirs[0] = dir1;
      sortedDirs[1] = dir2;
    } else if (id1 > id2) {
      sortedIds[0] = id2;
      sortedIds[1] = id1;
      sortedDirs[0] = dir2;
      sortedDirs[1] = dir1;
    } else if (dir1 == 'i') { // performance over estetics - keep ifs simple even with redundant code
      sortedIds[0] = id1;
      sortedIds[1] = id2;
      sortedDirs[0] = dir1;
      sortedDirs[1] = dir2;
    } else {
      sortedIds[0] = id2;
      sortedIds[1] = id1;
      sortedDirs[0] = dir2;
      sortedDirs[1] = dir1;
    }
    Branch b = new Branch(distSrvc.getDistances()[demand[id1].toStand][demand[id2].toStand],
                          outs, ids, dirs, sortedIds, sortedDirs);
    node[lev].add(b);
  }

  private List<Branch> rmDuplicates(List<Branch> node, int lev) {
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

    for (Branch b : arr) {
      if (b.cost != -1) {
        // sorting the key - putting the first element in the right place
        if (lev > 0) {
          int c = b.custIDsSorted[0];
          char action = b.custActionsSorted[0];
          for (int j = 1; j < b.custIDsSorted.length; j++) {
            if (b.custIDsSorted[j] == c) {
              int offset = 0;
              if (b.custActionsSorted[j] == 'o') {
                // that means that branch.custActionsSorted[0] == 'i'
                offset = 1; // you should put 'c' (which is IN) in position j - 1
              }
              for (int k = 0; k < j - offset; k++) {
                b.custIDsSorted[k] = b.custIDsSorted[k + 1];
                b.custActionsSorted[k] = b.custActionsSorted[k + 1];
              }
              b.custIDsSorted[j - offset] = c;
              b.custActionsSorted[j - offset] = action;
              break;
            } else if (b.custIDsSorted[j] > c) {
              for (int k = 0; k < j - 1; k++) {
                b.custIDsSorted[k] = b.custIDsSorted[k + 1];
                b.custActionsSorted[k] = b.custActionsSorted[k + 1];
              }
              b.custIDsSorted[j - 1] = c;
              b.custActionsSorted[j - 1] = action;
              break;
            }
          }
          b.key = genKey(b);
        }
        list.add(b);
      }
    }
    return list;
  }

  private String genKey(Branch b) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < b.custIDsSorted.length; i++) {
      buf.append(b.custIDsSorted[i]).append(b.custActionsSorted[i]);
    }
    return buf.toString();
  }

  /**
      Turn the root node into a list of pools.
   */
  public List<PoolElement> getList(int inPool) {
    List<PoolElement> ret = new ArrayList<>();

    for (Branch p : node[0]) {
      TaxiOrder[] orders = new TaxiOrder[p.custIDs.length];
      for (int i = 0; i < p.custIDs.length; i++) {
        orders[i] = demand[p.custIDs[i]];
      }
      ret.add(new PoolElement(orders, p.custActions, inPool, p.cost));
    }
    return ret;
  }
}
