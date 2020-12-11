package no.kabina.kaboot.stats;


import no.kabina.kaboot.KabootApplication;
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

    @Before
    public void before() {
        statService = new StatService(statRepo);
        given(statRepo.findByName("key")).willReturn(new Stat("key",1,0));
        given(statRepo.save(any())).willReturn(new Stat("key",2,0));
    }

    @Test
    public void testAddAverageElement() {
        statService.addAverageElement("key",1L);
        statService.addAverageElement("key",3L);
        int avg = statService.countAverage("key");
        assertThat(avg == 2).isTrue();
    }

    @Test
    public void testUpdateMaxInt() {
        Stat stat = statService.updateMaxIntVal("key", 2);
        assertThat(stat.getIntVal() == 2).isTrue();
    }

    @Test
    public void testAddToIntVal() {
        Stat stat = statService.addToIntVal("key", 1);
        assertThat(stat.getIntVal() == 2).isTrue();
    }

    @Test
    public void testIncrementIntVal() {
        Stat stat = statService.incrementIntVal("key");
        assertThat(stat.getIntVal() == 2).isTrue();
    }

    @Test
    public void testUpdateIntVal() {
        Stat stat = statService.updateIntVal("key", 2);
        assertThat(stat.getIntVal() == 2).isTrue();
    }

    @Test
    public void testStat() {
        Stat stat = new Stat();
        stat.setName("name");
        stat.setIntVal(1);
        stat.setDblVal(1.0);
        assertThat("name".equals(stat.getName())).isTrue();
        assertThat(stat.getIntVal() == 1).isTrue();
        assertThat(stat.getDblVal() == 1.0).isTrue();
    }

}
