package no.kabina.kaboot.dispatcher;

import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;
import org.junit.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
public class ModelTests {

    @Test
    public void testLcmPair() throws Exception {
        LcmPair pair = new LcmPair(1,0);
        assertThat(pair).isNotNull();
    }

    @Test
    public void testLcmOutput() throws Exception {
        LcmOutput pair = new LcmOutput(new ArrayList<>(),0);
        assertThat(pair).isNotNull();
    }

    @Test
    public void testTempModel() throws Exception {
        TempModel pair = new TempModel(new Cab[0], new TaxiOrder[0]);
        assertThat(pair).isNotNull();
    }

    @Test
    public void testPoolElement() throws Exception {
        PoolElement pair = new PoolElement(null,0,0);
        assertThat(pair).isNotNull();
    }

    @Test
    public void testGetDistance() throws Exception {
        assertThat(DistanceService.getDistance(0,1)).isSameAs(1);
    }
}
