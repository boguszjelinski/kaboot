package no.kabina.kaboot.scheduler;

import no.kabina.kaboot.orders.TaxiOrder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
public class PoolUtilTests {

    private TaxiOrder[] orders;
    private final int numbOfStands = 30;

    @Before
    public void before() {
        orders = new TaxiOrder[numbOfStands-1];
        for (int i = 0; i < numbOfStands-1; i++) {
            orders[i] = new TaxiOrder(i %2, (i+1)%2,20,20, true, TaxiOrder.OrderStatus.RECEIVED);
        }
    }

    @Test
    public void testPool() {
        PoolUtil util = new PoolUtil();
        PoolElement[] pool = util.findPool(orders, 3);
        assertThat(pool.length).isSameAs(9);
    }

    @Test
    public void testPool4() {
        PoolUtil util = new PoolUtil();
        PoolElement[] pool = util.findPool(orders, 4);
        assertThat(pool.length).isSameAs(7);
    }
}
