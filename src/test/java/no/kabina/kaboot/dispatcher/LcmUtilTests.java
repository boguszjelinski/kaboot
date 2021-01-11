package no.kabina.kaboot.dispatcher;

import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
public class LcmUtilTests {

    private Cab[] cabs;
    private TaxiOrder[] orders;
    private final int numbOfStands = 50;
    int [][] cost;

    @Before
    public void before() {
        cabs = new Cab[numbOfStands];
        orders = new TaxiOrder[numbOfStands];
        for (int i = 0; i < numbOfStands; i++) {
            cabs[i] = new Cab(i, Cab.CabStatus.FREE);
            orders[i] = new TaxiOrder(i, numbOfStands - i == i ? 0 : numbOfStands - i,
                    10,10, true, TaxiOrder.OrderStatus.RECEIVED, null);
        }
        cost = LcmUtil.calculateCost("glpk.mod", "out.txt", orders, cabs);
    }

    @Test
    public void testLcm() {
        final int size = 10;
        LcmOutput c = LcmUtil.lcm(cost, size);
        assertThat(c.getPairs().size()).isSameAs(size);
        for (int i = 0; i< size; i++) {
            assertThat(c.getPairs().get(i).getCab()).isSameAs(c.getPairs().get(i).getClnt());
            if (i > 0) {
                assertThat(c.getPairs().get(i).getCab()).isNotSameAs(c.getPairs().get(i-1).getCab());
                assertThat(c.getPairs().get(i).getClnt()).isNotSameAs(c.getPairs().get(i-1).getClnt());
            }
        }
    }

    @Test
    public void testRemoveCols() {
        final int cab = 1;
        final int clnt = 2;
        assertThat(cost[cab][clnt] < LcmUtil.BIG_COST).isTrue();
        LcmUtil.removeColsAndRows(cost, cab,clnt);
        assertThat(cost[cab][clnt] - LcmUtil.BIG_COST).isSameAs(0);
    }

    @Test
    public void testGetRidOfDistandCabs() {
        // these aren't distant
        Cab[] c = LcmUtil.getRidOfDistantCabs(orders, cabs);
        TaxiOrder[] o = LcmUtil.getRidOfDistantCustomers(orders, cabs);
        assertThat(c.length).isSameAs(numbOfStands);
        assertThat(o.length).isSameAs(numbOfStands);
        // these are distant
        for (int i = 0; i < numbOfStands; i++) {
            cabs[i] = new Cab(0, Cab.CabStatus.FREE);
            orders[i] = new TaxiOrder(numbOfStands, numbOfStands - 1,
                    10,10, true, TaxiOrder.OrderStatus.RECEIVED, null);
        }
        c = LcmUtil.getRidOfDistantCabs(orders, cabs);
        o = LcmUtil.getRidOfDistantCustomers(orders, cabs);
        assertThat(c.length).isSameAs(0);
        assertThat(o.length).isSameAs(0);
    }

    @Test
    public void testDemandForSolver() {
        List<LcmPair> lcmPairList = new ArrayList<>();
        lcmPairList.add(new LcmPair(0,0));
        lcmPairList.add(new LcmPair(1,1));
        TaxiOrder[] demand = new TaxiOrder[5];
        for (int i = 0; i< demand.length; i++) {
            demand[i] = new TaxiOrder();
            demand[i].setId((long) i);
        }
        TaxiOrder[] r = LcmUtil.demandForSolver(lcmPairList, demand);
        assertThat(r.length).isSameAs(3);
        for (int i=0; i< r.length; i++) {
            assertThat(r[i].getId()).isNotSameAs(0L);
            assertThat(r[i].getId()).isNotSameAs(1L);
        }
    }
}
