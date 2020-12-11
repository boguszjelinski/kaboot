package no.kabina.kaboot.scheduler;


import no.kabina.kaboot.KabootApplication;
import no.kabina.kaboot.stats.Stat;
import no.kabina.kaboot.stats.StatRepository;
import no.kabina.kaboot.stats.StatService;
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
public class SchedulerServiceTests {

    @MockBean
    StatService statService;

    @MockBean
    StatRepository statRepo;

    @Before
    public void before() {
        given(statRepo.findByName("key")).willReturn(new Stat("key",1,0));
        given(statRepo.save(any())).willReturn(new Stat("key",2,0));
    }

    @Test
    public void testRunLcm() {
    }


}
