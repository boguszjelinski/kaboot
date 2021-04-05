package no.kabina.kaboot.cabs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

//@RunWith(SpringRunner.class)
//@SpringBootTest(classes = KabootApplication.class)
//@WebAppConfiguration
////

@RunWith(SpringRunner.class)
@WebMvcTest(CabController.class)
@ActiveProfiles("test")
public class ControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CabRepository cabRepo;

    private String token;

    @Before
    public void createMock() {
        String auth = "cab0:cab0";
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        token = "Basic " + encodedAuth;
    }

    @Test
    public void whenNonExistingEntityForUpdate_thenReturns200() throws Exception {
        String body = "{\"location\":1, \"status\": \"ASSIGNED\"}";
        mvc.perform(put("/cabs/{id}", 0L) // cab0
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());
        //          .andExpect(content().string(containsString("{\"medsignering\":true,\"redirect\":\"redirectUrl\"}")));
    }

    @Test
    public void whenPost_thenReturns200() throws Exception {
        String body = "{\"location\":1, \"status\": \"ASSIGNED\"}";
        mvc.perform(post("/cabs/")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());
        //          .andExpect(content().string(containsString("{\"medsignering\":true,\"redirect\":\"redirectUrl\"}")));
    }


    @Test
    public void whenGetNonExistingCab_thenReturns200() throws Exception {
        given(cabRepo.findById(1)).willReturn(new Cab(0, "A1", Cab.CabStatus.ASSIGNED));
        mvc.perform(get("/cabs/1", 1L) // cab1 by cab0
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    public void testPojo() throws Exception {
        CabPojo pojo = new CabPojo(1, "A1", Cab.CabStatus.ASSIGNED);
        assertThat(pojo).isNotNull();
        assertThat(pojo.getStatus()).isSameAs(Cab.CabStatus.ASSIGNED);
    }

}
