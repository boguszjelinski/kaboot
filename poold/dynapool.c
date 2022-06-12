#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include "dynapool.h"

Branch nodeSMP[NUMBTHREAD][MAXTHREADMEM];
pthread_t myThread[NUMBTHREAD];

extern int memSize[MAXNODE];
extern Branch *node[MAXNODE];
extern int nodeSize[MAXNODE];
extern int nodeSizeSMP[NUMBTHREAD];

extern struct arg_struct {
   int i;
   int chunk;
   int lev;
   int inPool;
} *args[NUMBTHREAD];

extern Stop stops[MAXSTOPSNUMB];
extern int stopsNumb;
extern Order demand[MAXORDERSNUMB];
extern Order demandTemp[MAXORDERSNUMB];
extern int demandSize;
extern Cab supply[MAXCABSNUMB];
extern int cabsNumb;
extern short distance[MAXSTOPSNUMB][MAXSTOPSNUMB];

void storeBranch(int thread, char action, int lev, int ordId, Branch *b, int inPool) {
    if (nodeSizeSMP[thread] >= MAXTHREADMEM) {
      printf("storeBranch: allocated mem too low, level: %d, inPool: %d\n", lev, inPool);
      return;
    }
    Branch *ptr = &nodeSMP[thread][nodeSizeSMP[thread]];
    ptr->ordNumb = inPool + inPool - lev;
    ptr->ordIDs[0] = ordId;
    ptr->ordActions[0] = action;
    ptr->ordIDsSorted[0] = ordId;
    ptr->ordActionsSorted[0] = action;
    // ? memcpy
    for (int j = 0; j < ptr->ordNumb - 1; j++) { // further stage has one passenger less: -1
      ptr->ordIDs[j + 1]      = b->ordIDs[j];
      ptr->ordActions[j + 1]  = b->ordActions[j];
      ptr->ordIDsSorted[j + 1]= b->ordIDs[j];
      ptr->ordActionsSorted[j + 1] = b->ordActions[j];
    }
    sprintf (ptr->key, "%d%c%s", ordId, action, b->key);
    ptr->cost = distance[action == 'i' ? demand[ordId].fromStand : demand[ordId].toStand]
                        [b->ordActions[0] == 'i' ? demand[b->ordIDs[0]].fromStand : demand[b->ordIDs[0]].toStand] + b->cost;
    ptr->outs = action == 'o' ? b->outs + 1: b->outs;
    nodeSizeSMP[thread]++;
}

// b is index of Branch in lev+1
void storeBranchIfNotFoundDeeperAndNotTooLong(int thread, int lev, int ordId, int branch, int inPool) {
    // two situations: c IN and c OUT
    // c IN has to have c OUT in level+1, and c IN cannot exist in level + 1
    // c OUT cannot have c OUT in level +1
    boolean inFound = false;
    boolean outFound = false;
    Branch *ptr = &node[lev+1][branch];
    for (int i = 0; i < ptr->ordNumb; i++) {
      if (ptr->ordIDs[i] == ordId) {
        if (ptr->ordActions[i] == 'i') {
          inFound = true;
        } else {
          outFound = true;
        }
        // current passenger is in the branch below
      }
    }
    // now checking if anyone in the branch does not lose too much with the pool
    // c IN
    int nextStop = ptr->ordActions[0] == 'i'
                    ? demand[ptr->ordIDs[0]].fromStand : demand[ptr->ordIDs[0]].toStand;
    if (!inFound
        && outFound
        && !isTooLong(distance[demand[ordId].fromStand][nextStop], ptr)
        // TASK? if the next stop is OUT of passenger 'c' - we might allow bigger angle
        && bearingDiff(stops[demand[ordId].fromStand].bearing, stops[nextStop].bearing) < MAXANGLE
        ) storeBranch(thread, 'i', lev, ordId, ptr, inPool);
    // c OUT
    if (lev > 0 // the first stop cannot be OUT
        && ptr->outs < inPool // numb OUT must be numb IN
        && !outFound // there is no such OUT later on
        && !isTooLong(distance[demand[ordId].toStand][nextStop], ptr)
        && bearingDiff(stops[demand[ordId].toStand].bearing, stops[nextStop].bearing) < MAXANGLE
        ) storeBranch(thread, 'o', lev, ordId, ptr, inPool);
}

void iterate(void *arguments) {
  struct arg_struct *ar = arguments;
  int size = (ar->i + 1) * ar->chunk > demandSize ? demandSize : (ar->i + 1) * ar->chunk;
  
  for (int ordId = ar->i * ar->chunk; ordId < size; ordId++) 
   if (demand[ordId].id != -1) { // not allocated in previous search (inPool+1)
    for (int b = 0; b < nodeSize[ar->lev + 1]; b++) 
      if (node[ar->lev + 1][b].cost != -1) {  
        // we iterate over product of the stage further in the tree: +1
        storeBranchIfNotFoundDeeperAndNotTooLong(ar->i, ar->lev, ordId, b, ar->inPool);
    }
  }
}

void addBranch(int id1, int id2, char dir1, char dir2, int outs, int lev)
{
    if (nodeSize[lev] >= memSize[lev]) {
      printf("addBranch: allocated mem too low, level: %d\n", lev);
      return;
    }
    Branch *ptr = &node[lev][nodeSize[lev]];

    if (id1 < id2 || (id1==id2 && dir1 == 'i')) {
        sprintf (ptr->key, "%d%c%d%c", id1, dir1, id2, dir2);
        ptr->ordIDsSorted[0] = id1;
        ptr->ordIDsSorted[1] = id2;
        ptr->ordActionsSorted[0] = dir1;
        ptr->ordActionsSorted[1] = dir2;
    }
    else if (id1 > id2 || id1 == id2) {
        sprintf (ptr->key, "%d%c%d%c", id2, dir2, id1, dir1);
        ptr->ordIDsSorted[0] = id2;
        ptr->ordIDsSorted[1] = id1;
        ptr->ordActionsSorted[0] = dir2;
        ptr->ordActionsSorted[1] = dir1;
    }
    ptr->cost = distance[demand[id1].toStand][demand[id2].toStand];
    ptr->outs = outs;
    ptr->ordIDs[0] = id1;
    ptr->ordIDs[1] = id2;
    ptr->ordActions[0] = dir1;
    ptr->ordActions[1] = dir2;
    ptr->ordNumb = 2;
    nodeSize[lev]++;
}

void storeLeaves(int lev) {
    for (int c = 0; c < demandSize; c++)
      if (demand[c].id != -1)
        for (int d = 0; d < demandSize; d++)
          if (demand[d].id != -1) {
            // to situations: <1in, 1out>, <1out, 2out>
            if (c == d)  {
                // IN and OUT of the same passenger, we don't check bearing as they are probably distant stops
                addBranch(c, d, 'i', 'o', 1, lev);
            } else if (distance[demand[c].toStand][demand[d].toStand]
                        < distance[demand[d].fromStand][demand[d].toStand] * (100.0 + demand[d].maxLoss) / 100.0
                    && bearingDiff(stops[demand[c].toStand].bearing, stops[demand[d].toStand].bearing) < MAXANGLE
            ) {
              // TASK - this calculation above should be replaced by a redundant value in taxi_order - distance * loss
              addBranch(c, d, 'o', 'o', 2, lev);
            }
          }
}

void dive(int lev, int inPool, int numbThreads)
{
    if (lev > inPool + inPool - 3) { // lev >= 2*inPool-2, where -2 are last two levels
      storeLeaves(lev);
      return; // last two levels are "leaves"
    }
    dive(lev + 1, inPool, numbThreads);

    const int chunk = (int) (demandSize / numbThreads);
    if (numbThreads*chunk < demandSize) numbThreads++; // last thread will be the reminder of division

    for (int i = 0; i<numbThreads; i++) { // TASK: allocated orders might be spread unevenly -> count non-allocated and devide chunks ... evenly
        args[i]->i = i; 
        args[i]->chunk = chunk; 
        args[i]->lev = lev; 
        args[i]->inPool = inPool;
        nodeSizeSMP[i] = 0;
        if (pthread_create(&myThread[i], NULL, &iterate, args[i]) != 0) {
            printf("Err creating thread %d!\n", i);
        }
    }
    for (int i = 0; i<numbThreads; i++) {
        pthread_join(myThread[i], NULL); // Wait until thread is finished 
    }
    int idx = 0;
    for (int i = 0; i<numbThreads; i++) {
        if (idx + nodeSizeSMP[i] >= memSize[lev]) {
          printf("dive: allocated mem too low, level: %d\n", lev);
          break;
        }
        memcpy(&node[lev][idx], nodeSMP[i], nodeSizeSMP[i] * sizeof(Branch));
        idx += nodeSizeSMP[i];
    }
    nodeSize[lev] = idx;
    // removing duplicates which come from lev+1,
    // as for that level it does not matter which order is better in stages towards leaves
    //rmDuplicates(lev);
}

int bearingDiff(int a, int b) 
{
    int r = (a - b) % 360;
    if (r < -180.0) {
      r += 360.0;
    } else if (r >= 180.0) {
      r -= 360.0;
    }
    return abs(r);
}

boolean isTooLong(int wait, Branch *b)
{
    for (int i = 0; i < b->ordNumb; i++) {
        if (wait >  //distance[demand[b->ordIDs[i]].fromStand][demand[b->ordIDs[i]].toStand] 
                demand[b->ordIDs[i]].distance * (100.0 + demand[b->ordIDs[i]].maxLoss) / 100.0) 
                  return true;
        if (b->ordActions[i] == 'i' && wait > demand[b->ordIDs[i]].maxWait) return true;
        if (i + 1 < b->ordNumb) 
            wait += distance[b->ordActions[i] == 'i' ? demand[b->ordIDs[i]].fromStand : demand[b->ordIDs[i]].toStand]
                            [b->ordActions[i + 1] == 'i' ? demand[b->ordIDs[i + 1]].fromStand : demand[b->ordIDs[i + 1]].toStand];
    }
    return false;
}

int compare(const void * a, const void * b)
{
  Branch *brA = (Branch *)a;
  Branch *brB = (Branch *)b;
  return (strcmp(brA->key, brB->key));
}

int compareCost(const void * a, const void * b)
{
  Branch *brA = (Branch *)a;
  Branch *brB = (Branch *)b;
  return brA->cost - brB->cost;
}

void rmDuplicates(int lev) {
    int ordIDs[MAXORDID];
    char tempKey[MAXKEYLEN];
    Branch *arr = node[lev];
    int idx;

    if (lev == 0 || nodeSize[lev] < 1) { // lev==0: 1) there will be no more upper level - no need to remove
        // 2) we should not remove as the one with lower cost might not be feasible when added the cost of a cab
        return;
    }

    // removing duplicates from the previous stage
    // TASK: there might be more duplicates than one at lev==1 or 0 !!!!!!!!!!!!!
    qsort (arr, nodeSize[lev], sizeof(Branch), compare); // sort by key, not cost

    //printf ("LEV: %d, size before compressing: %d\n", lev, nodeSize[lev]);

    for (int i = 0; i < nodeSize[lev] - 1; i++) { // -1 as there is i+1 below
      if (arr[i].cost == -1) { // this -1 marker is set below
        continue;
      }
      if (strcmp(arr[i].key, arr[i+1].key) == 0) { // the same key, which costs less?
        if (arr[i].cost > arr[i+1].cost) {
          arr[i].cost = -1; // to be deleted
        } else {
          arr[i+1].cost = -1;
          i++; //just skip the 'if' in the next iteration
        }
      }
    }

    // recreating the key for the next level - the first element was unsorted, will go to the correct place now
    if (lev > 0) { // we don't need the keys any more after we have generated LEV==0
        int offset, c, len;
        char action;
        Branch *ptr;
        for (int i = 0; i < nodeSize[lev]; i++) 
          if (arr[i].cost != -1) {   
            ptr = arr + i;
            len = ptr->ordNumb; // that may be CONST for a level?
            c = ptr->ordIDsSorted[0];
            action = ptr->ordActionsSorted[0];
            // TODO: dumb sorting, maybe try bisection like bisect() in Python? or just qsort?
            for (int j = 1; j < len; j++) {
                if (ptr->ordIDsSorted[j] == c) {
                    offset = 0;
                    if (ptr->ordActionsSorted[j] == 'o') { // that means that branch.ordActionsSorted[0] == 'i'
                        offset = 1; // you should put 'c' (which is IN) in position j - 1
                    }
                    for (int k = 0; k < j - offset; k++) {
                        ptr->ordIDsSorted[k] = ptr->ordIDsSorted[k + 1];
                        ptr->ordActionsSorted[k] = ptr->ordActionsSorted[k + 1];
                    }
                    ptr->ordIDsSorted[j - offset] = c;
                    ptr->ordActionsSorted[j - offset] = action;
                    break;
                } else if (ptr->ordIDsSorted[j] > c) {
                    for (int k = 0; k < j - 1; k++) {
                        ptr->ordIDsSorted[k] = ptr->ordIDsSorted[k + 1];
                        ptr->ordActionsSorted[k] = ptr->ordActionsSorted[k + 1];
                    }
                    ptr->ordIDsSorted[j - 1] = c;
                    ptr->ordActionsSorted[j - 1] = action;
                    break;
                }
            }
            // regen key based on a sorted list
            for (int i = 0, l = 0; i < ptr->ordNumb; i++) {
                l += sprintf(ptr->key + l,"%d%c", ptr->ordIDsSorted[i], ptr->ordActionsSorted[i]); // sprintf returnes number of chars written
            }
          }
    }
}

int countNodeSize(int lev) {
  int count=0;
  Branch *arr = node[lev];
  for (int i=0; i<nodeSize[lev]; i++)
    if (arr[i].cost != -1 ) count++;
  return count;
}

void rmFinalDuplicates(char *json, int inPool) {
    int lev = 0;
    int cabIdx = -1;
    int from;
    int distCab;
    int size = nodeSize[lev];
    Branch *arr = node[lev];
    register Branch *ptr;

    if (nodeSize[lev] < 1) return;

    qsort(arr, size, sizeof(Branch), compareCost);
    
    for (int i = 0; i < size; i++) {
      ptr = arr + i;
      if (ptr->cost == -1) continue; // not dropped earlier or (!) later below
      from = demand[ptr->ordIDs[0]].fromStand;
      cabIdx = findNearestCab(from);
      if (cabIdx == -1) { // no more cabs
        // mark th rest of pools as dead
        // TASK: why? we won't use this information, node[0] will be garbage-collected
        printf("NO CAB\n");
        for (int j = i + 1; j < size; j++) arr[j].cost = -1;
        break;
      }
      distCab = distance[supply[cabIdx].location][from];
      if (distCab == 0 // constraints inside pool are checked while "diving" in recursion
              || constraintsMet(ptr, distCab)) {
        ptr->cab = cabIdx; // not supply[cabIdx].id as it is faster to reference it in Boot (than finding IDs)
        supply[ptr->cab].location = -1; // allocated
        saveInJson(json, ptr);
        // remove any further duplicates
        for (int j = i + 1; j < size; j++)
          if (arr[j].cost != -1 && isFound(ptr, arr+j, inPool+inPool-1)) // -1 as last action is always OUT
            arr[j].cost = -1; // duplicated; we remove an element with greater costs (list is pre-sorted)
      } else ptr->cost = -1; // constraints not met, mark as unusable
    } 
}

boolean constraintsMet(Branch *el, int distCab) {
  // TASK: distances in pool should be stored to speed-up this check
  int dist = 0;
  Order *o, *o2;
  for (int i = 0; i < el->ordNumb; i++) {
    o = &demand[el->ordIDs[i]];
    if (el->ordActions[i] == 'i' && dist + distCab > o->maxWait) {
      return false;
    }
    if (el->ordActions[i] == 'o' && dist > (1 + o->maxLoss/100.0) * o->distance) { // TASK: remove this calcul
      return false;
    }
    o2 = &demand[el->ordIDs[i + 1]];
    if (i < el->ordNumb - 1) {
      dist += distance[el->ordActions[i] == 'i' ? o->fromStand : o->toStand]
                      [el->ordActions[i + 1] == 'i' ? o2->fromStand : o2->toStand];
    }
  }
  return true;
}

void saveInJson(char *json, Branch *ptr) {
  int l = strlen(json);
  l += sprintf(json + l, "{\"cab\":%d,\"len\":%d,\"ids\":[", ptr->cab, ptr->ordNumb/2); 
  for (int i=0; i<ptr->ordNumb; i++) {
    if (l >= MAXJSON) {
      printf("JSON container too short");
      return;
    }
    l += sprintf(json + l, "%d,", ptr->ordIDs[i]);
    demand[ptr->ordIDs[i]].id = -1; // allocated
  }
  l--; // remove last comma
  l += sprintf(json + l, "],\"acts\":[");
  for (int i=0; i<ptr->ordNumb; i++) {
    if (l >= MAXJSON) {
      printf("JSON container too short");
      return;
    }
    l += sprintf(json + l, "\"%c\",", ptr->ordActions[i]);
  }
  l--; // remove last comma
  sprintf(json + l, "]},");
}

boolean isFound(Branch *br1, Branch *br2, int size) 
{   
    for (int x = 0; x < size; x++)
      if (br1->ordActions[x] == 'i') 
        for (int y = 0; y < size; y++) 
          if (br2->ordActions[y] == 'i' && br2->ordIDs[y] == br1->ordIDs[x])
            return true;
    return false;
}

int findNearestCab(int from) {
    int dist = 10000; // big enough
    int nearest = -1;
    for (int i = 0; i < cabsNumb; i++) {
      if (supply[i].location == -1) // allocated earlier to a pool
        continue;
      if (distance[supply[i].location][from] < dist) {
        dist = distance[supply[i].location][from];
        nearest = i;
      }
    }
    return nearest;
}

void findPool(int inPool, int numbThreads, char *json) {
    if (inPool > MAXINPOOL) {
      return;
    }
    for (int i=0; i<MAXNODE; i++) nodeSize[i] = 0;
    for (int i=0; i<NUMBTHREAD; i++) nodeSizeSMP[i] = 0;
    dive(0, inPool, numbThreads);
    //for (int i = 0; i < inPool + inPool - 1; i++)
    //    printf("node[%d].size: %d\n", i, countNodeSize(i));
    rmFinalDuplicates(json, inPool);
    printf("FINAL: inPool: %d, found pools: %d\n", inPool, countNodeSize(0));
}
