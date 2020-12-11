package no.kabina.kaboot.scheduler;

import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
public class GcmUtilTests {

    private Cab[] cabs;
    private TaxiOrder[] orders;
    private final int numbOfStands = 50;
    int [][] cost;

    @Before
    public void before() {
        cabs = new Cab[numbOfStands];
        orders = new TaxiOrder[numbOfStands];
        for (int i=0; i < numbOfStands; i++) {
            cabs[i] = new Cab(i, Cab.CabStatus.FREE);
            orders[i] = new TaxiOrder(i,numbOfStands - i == i ? 0 : numbOfStands - i,
                    10,10, true, TaxiOrder.OrderStatus.RECEIVED);
        }
        cost = LcmUtil.calculateCost(orders, cabs);
    }

    @Test
    public void testReduceSupply() {
        final int size = 10;
        Cab[] c = GcmUtil.reduceSupply(cost, cabs, size);
        for (int i = 0; i < size; i++)
            assertThat(c[i].getLocation()).isSameAs(numbOfStands - size + i);
    }

    @Test
    public void testReduceDemand() {
        final int size = 10;
        TaxiOrder[] o = GcmUtil.reduceDemand(cost, orders, size);
        for (int i = 0; i < size; i++)
            assertThat(o[i].getFromStand()).isSameAs(numbOfStands - size + i);
    }
}
