package no.kabina.kaboot.dispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import no.kabina.kaboot.orders.TaxiOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

// the only reason to have this service is the fact that Async needs a separate class
@Service
public class DynaPoolAsync {

  private TaxiOrder[] demand;

  public void setDemand(TaxiOrder[] demand) {
    this.demand = demand;
  }

  @Value("${kaboot.scheduler.max-angle}")
  private int maxAngle;

  private final DistanceService distSrvc;

  public DynaPoolAsync(DistanceService distanceService) {
    this.distSrvc = distanceService;
  }

  @Async
  public CompletableFuture<List<Branch>> checkCustRange(int from, int to, int lev, int inPool,
                                                                  List<Branch> branches) {
    List<Branch> list = new ArrayList<>();
    for (int c = from; c < to; c++) {
      for (Branch b : branches) {
        // we iterate over product of the stage further in the tree: +1
        storeBranchIfNotFoundDeeperAndNotTooLong(lev, c, b, inPool, list);
      }
    }
    return CompletableFuture.completedFuture(list);
  }

  private void storeBranchIfNotFoundDeeperAndNotTooLong(int lev, int c, Branch b, int inPool, List<Branch> list) {
    // isFoundInBranchOrTooLong(int c, Branch b)
    // two situations: c IN and c OUT
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
    int nextStop = b.custActions[0] == 'i'
            ? demand[b.custIDs[0]].fromStand : demand[b.custIDs[0]].toStand;
    if (!inFound
            && outFound
            && !isTooLong(distSrvc.getDistances()[demand[c].fromStand][nextStop], b)
            // TASK? if the next stop is OUT of passenger 'c' - we might allow bigger angle
            && PoolUtil.bearingDiff(distSrvc.bearing[demand[c].fromStand], distSrvc.bearing[nextStop]) < maxAngle
    ) {
      storeBranch('i', lev, c, b, inPool, list);
    }
    // c OUT
    if (lev > 0 // the first stop cannot be OUT
            && b.outs < inPool // numb OUT must be numb IN
            && !outFound // there is no such OUT later on
            && !isTooLong(distSrvc.getDistances()[demand[c].toStand][nextStop], b)
            && PoolUtil.bearingDiff(distSrvc.bearing[demand[c].toStand], distSrvc.bearing[nextStop]) < maxAngle
    ) {
      storeBranch('o', lev, c, b, inPool, list);
    }
  }

  private void storeBranch(char action, int lev, int c, Branch b, int inPool, List<Branch> list) {
    int len = inPool + inPool - lev;
    int[] custIDs = new int[len];
    int[] sortedIDs = new int[len];
    custIDs[0] = c;
    sortedIDs[0] = c;
    char[] actions = new char[len];
    char[] sortedActions = new char[len];
    actions[0] = action;
    sortedActions[0] = action;
    // !! System.arraycopy is slower
    for (int j = 0; j < len - 1; j++) { // further stage has one passenger less: -1
      custIDs[j + 1] = b.custIDs[j];
      actions[j + 1] = b.custActions[j];
      // j>0 is sorted by lev+1
      sortedIDs[j + 1] = b.custIDs[j];
      sortedActions[j + 1] = b.custActions[j];
    }
    Branch b2 = new Branch(new StringBuilder().append(c).append(action).append(b.key).toString(),
            // no sorting as we have to remove lev+1 duplicates eg. 1-4-5 and 1-5-4
            distSrvc.getDistances()[action == 'i' ? demand[c].fromStand : demand[c].toStand]
                    [b.custActions[0] == 'i' ? demand[b.custIDs[0]].fromStand
                    : demand[b.custIDs[0]].toStand] + b.cost,
            action == 'o' ? b.outs + 1 : b.outs, custIDs, actions, sortedIDs, sortedActions);
    list.add(b2);
  }

  // wait - distance from previous stop
  private boolean isTooLong(int wait, Branch b) {
    for (int i = 0; i < b.custIDs.length; i++) {
      if (wait > demand[b.custIDs[i]].getDistance()
              //distSrvc.getDistances()[demand[b.custIDs[i]].fromStand][demand[b.custIDs[i]].toStand]
              * (100.0 + demand[b.custIDs[i]].getMaxLoss()) / 100.0) {
        return true;
      }
      if (b.custActions[i] == 'i' && wait > demand[b.custIDs[i]].getMaxWait()) {
        return true;
      }
      if (i + 1 < b.custIDs.length) {
        wait += distSrvc.getDistances()[b.custActions[i] == 'i'
                ? demand[b.custIDs[i]].fromStand
                : demand[b.custIDs[i]].toStand]
                [b.custActions[i + 1] == 'i'
                ? demand[b.custIDs[i + 1]].fromStand
                : demand[b.custIDs[i + 1]].toStand];
      }
    }
    return false;
  }


}
