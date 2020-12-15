package no.kabina.kaboot.dispatcher;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(TestController.class)
@ActiveProfiles("test")
public class TestControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    SchedulerService service;

    @Test
    public void TestController_whenAuthorised() throws Exception {
        String auth = "adm0:adm0";
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        String tok = "Basic " + encodedAuth;
        mvc.perform(get("/schedulework")
                .header("Authorization", tok)
                .contentType(MediaType.ALL))
                .andExpect(status().isOk());
    }
}
