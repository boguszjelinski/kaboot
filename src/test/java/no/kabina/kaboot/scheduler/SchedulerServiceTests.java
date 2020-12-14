package no.kabina.kaboot.scheduler;


import no.kabina.kaboot.KabootApplication;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;
import no.kabina.kaboot.stats.Stat;
import no.kabina.kaboot.stats.StatRepository;
import no.kabina.kaboot.stats.StatService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = KabootApplication.class)
@ActiveProfiles("test")
public class SchedulerServiceTests {

    @MockBean
    StatService statService;

    @MockBean
    StatRepository statRepo;

    @Autowired
    SchedulerService service;

    @Before
    public void before() {
        given(statRepo.findByName("key")).willReturn(new Stat("key",1,0));
        given(statRepo.save(any())).willReturn(new Stat("key",2,0));
    }

    @Test
    public void testRunLcmAndSolver() {
        TaxiOrder[] orders;
        Cab[] cabs;
        final int numbOfStands = 50;
        orders = new TaxiOrder[numbOfStands-1];
        cabs = new Cab[numbOfStands-1];
        for (int i = 0; i < numbOfStands-1; i++) {
            cabs[i] = new Cab(i, Cab.CabStatus.FREE);
            cabs[i].setId((long)i);
            orders[i] = new TaxiOrder(i %2, (i+1)%2,20,20, true, TaxiOrder.OrderStatus.RECEIVED);
            orders[i].setId((long)i);
        }
        PoolUtil util = new PoolUtil();
        PoolElement[] pool = util.findPool(orders, 3);
        assertThat(pool.length).isSameAs(16);
        TaxiOrder[] demand = PoolUtil.findFirstLegInPoolOrLone(pool, orders);
        assertThat(demand.length).isSameAs(17);
        int [][] cost = LcmUtil.calculateCost(demand, cabs);
        assertThat(cost.length).isSameAs(49);
        TempModel tempModel = service.runLcm(cabs, demand, cost, pool);
        assertThat(tempModel.getDemand().length).isSameAs(1);
        assertThat(tempModel.getSupply().length).isSameAs(33);
        // test solver by this occasion
        try {
            service.runSolver(cabs, demand, cost, pool);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int[] x = service.readSolversResult(cost.length);
        assertThat(x.length - 2401).isSameAs(0);
    }

    @Test
    public void testGeneratePool() {
        final int numbOfStands = 20;
        TaxiOrder[] orders = new TaxiOrder[numbOfStands-1];
        for (int i = 0; i < numbOfStands-1; i++) {
            orders[i] = new TaxiOrder(i %2, (i+1)%2,20,20, true, TaxiOrder.OrderStatus.RECEIVED);
            orders[i].setId((long) i);
        }
        PoolElement[] pool = service.generatePool(orders);
        assertThat(pool.length).isSameAs(5);
    }

    @Test
    public void testPlan() {
        service.findPlan();
    }


}
