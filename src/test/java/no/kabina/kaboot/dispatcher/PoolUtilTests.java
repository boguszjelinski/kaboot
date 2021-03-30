package no.kabina.kaboot.dispatcher;

import no.kabina.kaboot.orders.TaxiOrder;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.test.context.ActiveProfiles;
import java.util.Random;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@ActiveProfiles("test")
public class PoolUtilTests {


    private TaxiOrder[] orders;
    private final int numbOfStands = 50;
    private final int numbOfOrders = 75;
    Random rand = new Random(10L);
    final int MAX_TRIP = 4;
    final int MAX_LOSS = 50;

    @Before
    public void before() {

        orders = new TaxiOrder[numbOfOrders];
        for (int i = 0; i < numbOfOrders; i++) {
            //int from = rand.nextInt(numbOfStands);
            orders[i] = new TaxiOrder(i%45 == numbOfStands -1 ? 0 : i%45, // from
                                    Math.min((i+1)%45,numbOfStands-1), // randomTo(from, numbOfStands)
                                    10,
                                    MAX_LOSS,
                                    true,
                                    TaxiOrder.OrderStatus.RECEIVED,
                                    null);
            orders[i].setId((long)i);
        }
    }

    @Test
    public void testPool() {
        PoolUtil util = new PoolUtil(numbOfStands);
        PoolElement[] pool = util.findPool(orders, 3);
        assertThat(pool.length).isSameAs(16);
    }

    @Test
    public void testPool4() {
        PoolUtil util = new PoolUtil(numbOfStands);
        PoolElement[] pool = util.findPool(orders, 4);
        assertThat(pool.length).isSameAs(15);
        assertThat(poolIsValid(pool)).isSameAs(0);
    }

    @Test
    public void testDynaPool4() {
        DynaPool util = new DynaPool(numbOfStands);
        PoolElement[] pool = util.findPool(orders, 4);
        assertThat(pool.length).isSameAs(15);
        assertThat(poolIsValid(pool)).isSameAs(0);
    }

    @Test
    public void testDynaPool3() {
        DynaPool util = new DynaPool(numbOfStands);
        PoolElement[] pool = util.findPool(orders, 3);
        assertThat(pool.length).isSameAs(16);
        assertThat(poolIsValid(pool)).isSameAs(0);
    }

    private int poolIsValid(PoolElement[] pool) {
        boolean valid = true;
        // first checking if acceptably long
        int failCount = 0;
        for (PoolElement e : pool) {
            boolean isOk = true;
            int cost = 0;
            int j=0;
            for (; j < e.getNumbOfCust()-1; j++) {  // pickup
                cost += DistanceService.getDistance(e.getCust()[j].fromStand, e.getCust()[j+1].fromStand);
            }
            cost += DistanceService.getDistance(e.getCust()[j].fromStand, e.getCust()[j+1].toStand);
            j++;
            for (;j < 2 * e.getNumbOfCust()-1; j++) {
                if (cost > DistanceService.getDistance(e.getCust()[j].fromStand, e.getCust()[j].toStand) * (100.0+MAX_LOSS)/100.0) {
                    isOk = false;
                    break;
                }
                cost += DistanceService.getDistance(e.getCust()[j].toStand, e.getCust()[j+1].toStand);
            }
            if (cost > DistanceService.getDistance(e.getCust()[j].fromStand, e.getCust()[j].toStand) * (100.0+MAX_LOSS)/100.0) {
                isOk = false;
            }
            if (!isOk) failCount++;
        }

        // if a passenger does not appear in two pools

        return failCount;
    }

    @Test
    public void testSmpPool3() {
        SmpPool util = new SmpPool(8, numbOfStands);
        PoolElement[] pool = new PoolElement[0];
        try {
            pool = util.findSmpPool(orders, 3);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        assertThat(pool.length).isSameAs(16);
    }

    @Test
    public void testSmpPool4() {
        SmpPool util = new SmpPool(8, numbOfStands);
        PoolElement[] pool = new PoolElement[0];
        try {
            pool = util.findSmpPool(orders, 4);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        assertThat(pool.length).isSameAs(15);
    }

    @Test
    public void testExternPool4() {
        ExternPool util = new ExternPool("findpool", "pool-in.csv", "pool-out.csv", 8);
        PoolElement[] pool = new PoolElement[0];
        pool = util.findPool(orders, 4);
        assertThat(pool.length).isSameAs(15);
        assertThat(poolIsValid(pool)).isSameAs(0);
    }

    @Test
    public void testPoolElement() {
        PoolElement el = new PoolElement(null, 0, 0);
        el.setCust(null);
        el.setNumbOfCust(1);
        assertThat(el.getCust()).isNull();
        assertThat(el.getNumbOfCust()).isSameAs(1);
        assertThat(el.equals(null)).isNotEqualTo(true);
        assertThat(el.equals(new PoolElement(null, 0, 1))).isNotEqualTo(true);
        assertThat(el.hashCode()).isNotSameAs(-1);

    }

    private int randomTo(int from, int maxStand) {
        int diff = rand.nextInt(MAX_TRIP * 2) - MAX_TRIP;
        if (diff == 0) diff = 1;
        int to = 0;
        if (from + diff > maxStand -1 ) to = from - diff;
        else if (from + diff < 0) to = 0;
        else to = from + diff;
        return to;
    }
}
