package no.kabina.kaboot.dispatcher;

import no.kabina.kaboot.KabootApplication;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = KabootApplication.class)
@ActiveProfiles("test")
public class GcmUtilTests {

    @Autowired
    LcmUtil lcmUtil;

    @MockBean
    DistanceService distanceService;

    private Cab[] cabs;
    private TaxiOrder[] orders;
    private final int numbOfStands = 50;
    int [][] cost;

    @Before
    public void before() {
        given(distanceService.getDistances()).willReturn(PoolUtil.setCosts(numbOfStands));
        cabs = new Cab[numbOfStands];
        orders = new TaxiOrder[numbOfStands];
        for (int i=0; i < numbOfStands; i++) {
            cabs[i] = new Cab(i, "", Cab.CabStatus.FREE);
            orders[i] = new TaxiOrder(i,numbOfStands - i == i ? 0 : numbOfStands - i,
                    10,10, true, TaxiOrder.OrderStatus.RECEIVED, null);
        }
        cost = lcmUtil.calculateCost("glpk.mod", "out.txt", orders, cabs);
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
            cabs2[i] = new Cab(0, "",  Cab.CabStatus.FREE);
            orders2[i] = new TaxiOrder(10,12, 10, 10, true, TaxiOrder.OrderStatus.RECEIVED, null);
        }
        int [][] cost2 = lcmUtil.calculateCost("glpk.mod", "out.txt", orders2, cabs2);

        Integer[] o = GcmUtil.findMinDistancesForDemand(cost2);
        assertThat(o.length).isSameAs(n);
        for (Integer value : o) assertThat(value.intValue()).isSameAs(10);

        for (int i=0; i < n; i++) {
            cabs2[i] = new Cab(0, "", Cab.CabStatus.FREE);
            orders2[i] = new TaxiOrder(1,12, 10, 10, true, TaxiOrder.OrderStatus.RECEIVED, null);
        }
        cost2 = lcmUtil.calculateCost("glpk.mod", "out.txt", orders2, cabs2);
        o = GcmUtil.findMinDistancesForDemand(cost2);
        assertThat(o.length).isSameAs(n);
        for (Integer integer : o) assertThat(integer.intValue()).isSameAs(1);
    }
}
