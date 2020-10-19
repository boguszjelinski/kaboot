package no.kabina.kaboot.orders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

@RunWith(SpringRunner.class)
@WebMvcTest(TaxiOrderController.class)
public class TaxiOrderControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TaxiOrderRepository repository;

    @MockBean
    private TaxiOrderService service;

    private String token;

    @Before
    public void createMock() {
        String auth = "cust0:cust0";
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        token = "Basic " + encodedAuth;
    }

    @Test
    public void whenInsert_thenReturns200() throws Exception {
        String body = "{\"fromStand\":1, \"toStand\": 2, \"maxWait\":10, \"maxLoss\": 10, \"shared\": true}";
        mvc.perform(post("/orders", 0L) // cab0
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());
    }

    @Test
    public void whenGetNonExistingOrder_thenReturns200() throws Exception {
        mvc.perform(get("/orders/0", 0L) // cab0
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }


}
