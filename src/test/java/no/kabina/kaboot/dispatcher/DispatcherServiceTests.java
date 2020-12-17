package no.kabina.kaboot.dispatcher;


import no.kabina.kaboot.KabootApplication;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.cabs.CabRepository;
import no.kabina.kaboot.orders.TaxiOrder;
import no.kabina.kaboot.orders.TaxiOrderRepository;
import no.kabina.kaboot.routes.LegRepository;
import no.kabina.kaboot.routes.RouteRepository;
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

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = KabootApplication.class)
@ActiveProfiles("test")
public class DispatcherServiceTests {

    @MockBean
    StatService statService;

    @MockBean
    StatRepository statRepo;

    @MockBean
    TaxiOrderRepository orderRepo;

    @MockBean
    CabRepository cabRepo;

    @MockBean
    RouteRepository routeRepo;

    @MockBean
    LegRepository legRepo;

    @Autowired
    DispatcherService service;

    @Before
    public void before() {
        given(statRepo.findByName("key")).willReturn(new Stat("key",1,0));
        given(statRepo.save(any())).willReturn(new Stat("key",2,0));
    }

    @Test
    public void testRunLcmAndSolver() {
        TempModel model = genModel(50);
        TaxiOrder[] orders = model.getDemand();
        Cab[] cabs = model.getSupply();

        PoolUtil util = new PoolUtil();
        PoolElement[] pool = util.checkPool(orders, 3);
        assertThat(pool.length).isSameAs(16);
        TaxiOrder[] demand = PoolUtil.findFirstLegInPoolOrLone(pool, orders);
        assertThat(demand.length).isSameAs(17);
        int [][] cost = LcmUtil.calculateCost(demand, cabs);
        assertThat(cost.length).isSameAs(49);
        TempModel tempModel = service.runLcm(cabs, demand, cost, pool);
        assertThat(tempModel.getDemand().length).isSameAs(17);
        assertThat(tempModel.getSupply().length).isSameAs(49);
        // test solver by this occasion
        try {
            service.runSolver(cabs, demand, cost, pool);
        } catch (Exception e) {
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
    public void testPlan1() {
        service.findPlan();
        assertThat(service.hashCode()>0).isTrue();
    }

    @Test
    public void testPlan2() {
        TempModel model = genModel(10);
        TaxiOrder[] orders = model.getDemand();
        Cab[] cabs = model.getSupply();
        given(orderRepo.findByStatus(any())).willReturn(Arrays.asList(orders));
        given(cabRepo.findByStatus(any())).willReturn(Arrays.asList(cabs));
        service.findPlan();
        assertThat(service.hashCode()>0).isTrue();
    }

    private TempModel genModel(int size) {
        TaxiOrder[] orders;
        Cab[] cabs;
        final int numbOfStands = size;
        orders = new TaxiOrder[numbOfStands-1];
        cabs = new Cab[numbOfStands-1];
        for (int i = 0; i < numbOfStands-1; i++) {
            cabs[i] = new Cab(i, Cab.CabStatus.FREE);
            cabs[i].setId((long)i);
            orders[i] = new TaxiOrder(i %2, (i+1)%2,20,20, true, TaxiOrder.OrderStatus.RECEIVED);
            orders[i].setId((long)i);
        }
        return new TempModel(cabs, orders);
    }
}
