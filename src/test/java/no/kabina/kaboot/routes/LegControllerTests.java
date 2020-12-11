package no.kabina.kaboot.routes;

import org.junit.Before;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(LegController.class)
@ActiveProfiles("test")
public class LegControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private RouteRepository routeRepo;

    @MockBean
    private LegRepository legRepo;

    private String token;

    @Before
    public void createMock() {
        String auth = "cab0:cab0";
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        token = "Basic " + encodedAuth;
    }

    @Test
    public void whenUpdate_thenReturns200() throws Exception {
        String body = "{\"status\": \"COMPLETE\"}";
        mvc.perform(put("/legs/1")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());
    }

  /*  @Test
    public void whenGetRoutes_thenReturns200() throws Exception {
        mvc.perform(get("/routes")
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }*/
}
