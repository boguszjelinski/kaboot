package no.kabina.kaboot.dispatcher;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import no.kabina.kaboot.orders.TaxiOrder;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class SmpPool {
  private int numbOfThreads;
  private int numbOfStands;

  public SmpPool(int numbOfThreads, int numbOfStands) {
    this.numbOfThreads = numbOfThreads;
    this.numbOfStands = numbOfStands;
  }

  /**
  * Threaded Pool discoverer
  * @param dem demand
  * @param inPool number of passnegers in pool
  * @return pool proposal
  */
  public PoolElement[] findSmpPool(TaxiOrder[] dem, int inPool) throws InterruptedException, ExecutionException {

    CompletableFuture<PoolElement[]>[] arr = new CompletableFuture[numbOfThreads];
    int step = (dem.length / numbOfThreads) + 1; // +1 as we could miss the rest of division in last thread; the last thread will be slightly under-loaded
    for (int i = 0, start = 0; i < numbOfThreads; i++, start += step) {
      arr[i] = findPoolAsync(dem, inPool, start, Math.min(start + step, dem.length));
    }
    CompletableFuture.allOf(arr);
    PoolElement[] ret = null;
    for (int i = 0; i < numbOfThreads; i++) {
      ret = ArrayUtils.addAll(ret, arr[i].get());
    }
    return PoolUtil.removeDuplicates(ret, inPool);
  }

  @Async
  private CompletableFuture<PoolElement[]> findPoolAsync(TaxiOrder[] demand, int inPool, int start, int stop) {
    if (demand == null || start >= demand.length) {
      return CompletableFuture.completedFuture(new PoolElement[0]);
    }
    PoolUtil util = new PoolUtil(numbOfStands);
    util.poolList = new ArrayList<>();
    util.demand = demand;

    util.checkPool(0, demand.length, start, stop, inPool);
    PoolElement[] results = util.poolList.toArray(new PoolElement[0]);
    return CompletableFuture.completedFuture(results);
  }
}
