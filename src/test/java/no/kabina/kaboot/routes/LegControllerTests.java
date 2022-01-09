package no.kabina.kaboot.routes;

import no.kabina.kaboot.orders.TaxiOrderRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
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

    @MockBean
    private TaxiOrderRepository orderRepo;

    private String token;

    @Before
    public void createMock() {
        String auth = "cab0:cab0";
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        token = "Basic " + encodedAuth;
    }

    @Test
    public void whenUpdateInvalidId_thenReturns200() throws Exception {
        String body = "{\"status\": \"COMPLETED\"}";
        mvc.perform(put("/legs/1")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());
    }

    @Test
    public void whenUpdateValidId_thenReturns200() throws Exception {
        given(legRepo.findById(123L)).willReturn(java.util.Optional.of(new Leg()));
        String body = "{\"status\": \"COMPLETED\"}";
        mvc.perform(put("/legs/123")
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

    @Test
    public void whenPojoMeansBusiness() throws Exception {
        LegPojo leg = new LegPojo(Route.RouteStatus.COMPLETED);
        leg.setStatus(Route.RouteStatus.ASSIGNED);
        assertThat(leg.getStatus()).isSameAs(Route.RouteStatus.ASSIGNED);
    }

    @Test
    public void whenEntityMeansBusiness() throws Exception {
        Leg leg = new Leg(0,1,2, Route.RouteStatus.COMPLETED,0);
        leg.setRoute(null);
        leg.setId(0L);
        leg.setStatus(Route.RouteStatus.ASSIGNED);
        assertThat(leg.getStatus()).isSameAs(Route.RouteStatus.ASSIGNED);
        assertThat(leg.getFromStand()).isSameAs(0);
        assertThat(leg.getToStand()).isSameAs(1);
        assertThat(leg.getPlace()).isSameAs(2);
        assertThat(leg.getRoute()).isNull();
        assertThat(leg.getStatus()).isSameAs(Route.RouteStatus.ASSIGNED);
    }
}
