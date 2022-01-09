package no.kabina.kaboot.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import no.kabina.kaboot.cabs.Cab;
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

@RunWith(SpringRunner.class)
@WebMvcTest(RouteController.class)
@ActiveProfiles("test")
public class RouteControllerTests {

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
    public void whenUpdateInvalidRoute_thenReturns200() throws Exception {
        String body = "{\"status\": \"COMPLETED\"}";
        mvc.perform(put("/routes/1")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());
    }

    @Test
    public void whenUpdateValidRoute_thenReturns200() throws Exception {
        Route r = new Route();
        Cab c = new Cab(1, "", Cab.CabStatus.ASSIGNED);
        c.setId(0L);
        r.setCab(c);
        given(routeRepo.findById(123L)).willReturn(java.util.Optional.of(r));
        String body = "{\"status\": \"COMPLETED\"}";
        mvc.perform(put("/routes/123")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());
    }

    @Test
    public void whenGetRoutes_thenReturns200() throws Exception {
        mvc.perform(get("/routes")
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    public void whenPojoMeansBusiness() throws Exception {
        RoutePojo pojo = new RoutePojo();
        pojo.setStatus(Route.RouteStatus.COMPLETED);
        assertThat(pojo.getStatus()).isSameAs(Route.RouteStatus.COMPLETED);
        pojo = new RoutePojo(Route.RouteStatus.ASSIGNED);
        assertThat(pojo.getStatus()).isSameAs(Route.RouteStatus.ASSIGNED);
    }

    @Test
    public void whenEntityMeansBusiness() throws Exception {
        Route pojo = new Route();
        pojo.setStatus(Route.RouteStatus.COMPLETED);
        pojo.setLegs(null);
        pojo.setCab(null);
        assertThat(pojo.getStatus()).isSameAs(Route.RouteStatus.COMPLETED);
        assertThat(pojo.getLegs()).isNull();
        assertThat(pojo.getCab()).isNull();
    }
}
