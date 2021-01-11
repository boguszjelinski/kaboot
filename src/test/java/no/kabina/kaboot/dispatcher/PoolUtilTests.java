package no.kabina.kaboot.dispatcher;

import no.kabina.kaboot.orders.TaxiOrder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
public class PoolUtilTests {

    private TaxiOrder[] orders;
    private final int numbOfStands = 50;
    private final int numbOfOrders = 75;

    @Before
    public void before() {
        orders = new TaxiOrder[numbOfOrders];
        for (int i = 0; i < numbOfOrders; i++) {
            orders[i] = new TaxiOrder(i%45 == numbOfStands -1 ? 0 : i%45,
                                    Math.min((i+1)%45,numbOfStands-1),
                                    20,
                                    20,
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
        assertThat(pool.length).isSameAs(13);
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
}
