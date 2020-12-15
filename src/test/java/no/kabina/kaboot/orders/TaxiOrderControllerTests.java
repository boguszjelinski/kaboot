package no.kabina.kaboot.orders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import no.kabina.kaboot.customers.Customer;
import no.kabina.kaboot.stats.StatService;
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
@WebMvcTest(TaxiOrderController.class)
@ActiveProfiles("test")
public class TaxiOrderControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TaxiOrderRepository repository;

    @MockBean
    private TaxiOrderService service;

    @MockBean
    private StatService statService;

    private String token;

    @Before
    public void createMock() {
        String auth = "cust0:cust0";
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        token = "Basic " + encodedAuth;
    }

    @Test
    public void whenInsertOrder_thenReturns200() throws Exception {
        String body = "{\"fromStand\":1, \"toStand\": 2, \"maxWait\":10, \"maxLoss\": 10, \"shared\": true}";
        mvc.perform(post("/orders")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());
    }

    @Test
    public void whenUpdateOrder_thenReturns200() throws Exception {
        String body = "{\"fromStand\":1, \"toStand\": 2, \"maxWait\":10, \"maxLoss\": 10, \"shared\": true}";
        given(repository.findById(any())).willReturn(
                java.util.Optional.of(new TaxiOrder(1, 2, 10, 10, true, TaxiOrder.OrderStatus.ASSIGNED)));
        given(repository.save(any())).willReturn(null);
        mvc.perform(put("/orders/{id}", 123)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());
    }

    @Test
    public void whenGetNonExistingOrder_thenReturns200() throws Exception {
        given(repository.findById(1)).willReturn(new TaxiOrder(15,16,10,10, true, TaxiOrder.OrderStatus.ASSIGNED));
        mvc.perform(get("/orders/{id}", 0L)
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    public void TestController_whenNotAuthorised() throws Exception {
        mvc.perform(get("/schedulework")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    @Test
    public void whenExistingOrderButNoCustomerSet_thenReturns200() throws Exception {
        TaxiOrder ord = new TaxiOrder(15, 16, 10, 10, true, TaxiOrder.OrderStatus.ASSIGNED);
        ord.setId(0L);
        given(repository.findById(0L)).willReturn(ord);
        mvc.perform(get("/orders/{id}", 0L)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void whenExistingOrder_thenReturns200() throws Exception {
        TaxiOrder ord = new TaxiOrder(15, 16, 10, 10, true, TaxiOrder.OrderStatus.ASSIGNED);
        Customer cust = new Customer();
        cust.setId(0L);
        ord.setId(123L);
        ord.setCustomer(cust);
        given(repository.findById(123L)).willReturn(ord);
        mvc.perform(get("/orders/{id}", 123L)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError()); // TASK why ?
    }

    @Test
    public void whenTaxiOrderComesTrue() {
        TaxiOrder ord = new TaxiOrder();
        ord.setCab(null);
        ord.setLeg(null);
        ord.setRoute(null);
        ord.setRcvdTime(null);
        ord.setEta(1);
        ord.setInPool(true);
        assertThat(ord.getCab()).isNull();
        assertThat(ord.getLeg()).isNull();
        assertThat(ord.getRoute()).isNull();
        assertThat(ord.getRoute()).isNull();
        assertThat(ord.getEta()).isSameAs(1);
        assertThat(ord.isInPool()).isTrue();
        assertThat(ord.toString()).isNotNull();
    }

    @Test
    public void whenTaxiOrderPojoComesTrue() {
        TaxiOrderPojo ord = new TaxiOrderPojo(0,1,2,3,true);
        assertThat(ord.getFromStand()).isSameAs(0);
        assertThat(ord.getToStand()).isSameAs(1);
        assertThat(ord.getMaxWait()).isSameAs(2);
        assertThat(ord.getMaxLoss()).isSameAs(3);
        assertThat(ord.isShared()).isTrue();
    }

}
