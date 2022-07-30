package no.kabina.kaboot.stats;


import no.kabina.kaboot.KabootApplication;
import no.kabina.kaboot.dispatcher.DistanceService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
public class StatsTests {

    StatService statService;

    @MockBean
    StatRepository statRepo;

    @MockBean
    DistanceService distanceService;

    @Before
    public void before() {
        statService = new StatService(statRepo);
        given(statRepo.findByName("key")).willReturn(new Stat("key",1));
        given(statRepo.save(any())).willReturn(new Stat("key",2));
    }

    @Test
    public void testAddAverageElement() {
        statService.addAverageElement("key",1L);
        statService.addAverageElement("key",3L);
        int avg = statService.countAverage("key");
        assertThat(avg).isEqualTo(2);
    }

    @Test
    public void testUpdateMaxInt_WhenKeyFound() {
        Stat stat = statService.updateMaxIntVal("key", 2);
        assertThat(stat.getIntVal()).isEqualTo(2);
    }

    @Test
    public void testUpdateMaxInt_WhenKeyNotFound() {
        given(statRepo.findByName("key")).willReturn(null);
        Stat stat = statService.updateMaxIntVal("key", 2);
        assertThat(stat.getIntVal()).isEqualTo(2);
    }

    @Test
    public void testAddToIntVal_WhenKeyFound() {
        Stat stat = statService.addToIntVal("key", 1);
        assertThat(stat.getIntVal()).isEqualTo(2);
    }

    @Test
    public void testAddToIntVal_WhenKeyNotFound() {
        given(statRepo.findByName("key")).willReturn(null);
        Stat stat = statService.addToIntVal("key", 1);
        assertThat(stat.getIntVal()).isEqualTo(2);
    }

    @Test
    public void testIncrementIntVal_WhenKeyFound() {
        Stat stat = statService.incrementIntVal("key");
        assertThat(stat.getIntVal()).isEqualTo(2);
    }

    @Test
    public void testIncrementIntVal_WhenKeyNotFound() {
        given(statRepo.findByName("key")).willReturn(null);
        Stat stat = statService.incrementIntVal("key");
        assertThat(stat.getIntVal()).isEqualTo(2);
    }

    @Test
    public void testUpdateIntVal_WnenKeyFound() {
        Stat stat = statService.updateIntVal("key", 2);
        assertThat(stat.getIntVal()).isEqualTo(2);
    }

    @Test
    public void testUpdateIntVal_WnenKeyNotFound() {
        given(statRepo.findByName("key")).willReturn(null);
        Stat stat = statService.updateIntVal("key", 2);
        assertThat(stat.getIntVal()).isEqualTo(2);
    }

    @Test
    public void testStat() {
        Stat stat = new Stat();
        stat.setName("name");
        stat.setIntVal(1);
        assertThat(stat.getName()).isEqualTo("name");
        assertThat(stat.getIntVal()).isEqualTo(1);
    }

}
