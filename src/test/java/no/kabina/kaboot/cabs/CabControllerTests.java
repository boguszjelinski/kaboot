package no.kabina.kaboot.cabs;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RunWith(SpringRunner.class)
@WebMvcTest(CabController.class)
public class CabControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

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
}
