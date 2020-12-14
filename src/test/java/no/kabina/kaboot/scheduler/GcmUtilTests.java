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

    @Test
    public void testFindMinDistancesForDemand() {
        int n = 2;
        Cab[] cabs2 = new Cab[n];
        TaxiOrder[] orders2 = new TaxiOrder[n];
        for (int i=0; i < n; i++) {
            cabs2[i] = new Cab(0, Cab.CabStatus.FREE);
            orders2[i] = new TaxiOrder(10,12, 10, 10, true, TaxiOrder.OrderStatus.RECEIVED);
        }
        int [][] cost2 = LcmUtil.calculateCost(orders2, cabs2);

        Integer[] o = GcmUtil.findMinDistancesForDemand(cost2);
        assertThat(o.length).isSameAs(n);
        for (int i= 0; i<o.length; i++)
            assertThat(o[i].intValue() - LcmUtil.BIG_COST).isZero();

        for (int i=0; i < n; i++) {
            cabs2[i] = new Cab(0, Cab.CabStatus.FREE);
            orders2[i] = new TaxiOrder(1,12, 10, 10, true, TaxiOrder.OrderStatus.RECEIVED);
        }
        cost2 = LcmUtil.calculateCost(orders2, cabs2);
        o = GcmUtil.findMinDistancesForDemand(cost2);
        assertThat(o.length).isSameAs(n);
        for (int i = 0; i<o.length; i++)
            assertThat(o[i].intValue()).isSameAs(1);
    }


}
