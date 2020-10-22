package no.kabina.kaboot.cabs

import org.junit.Ignore
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.http.HttpStatus.*

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc

import no.kabina.kaboot.orders.TaxiOrderRepository
import no.kabina.kaboot.orders.TaxiOrderService
import no.kabina.kaboot.routes.RouteRepository
import no.kabina.kaboot.routes.TaskRepository
import spock.lang.Specification
import spock.mock.DetachedMockFactory

@AutoConfigureMockMvc
@WebMvcTest
class CabControlerTests extends Specification {
    def cabController = new CabController()
    MockMvc mockMvc = standaloneSetup(cabController).build()
    def cabService = Mock(CabRepository)

   /* @Autowired
    CabRepository cabRepository*/

    def setup() {
        cabController.repository = cabService
    }

    def "when get is performed and no record is found then the response has status 200 and content is '[]'"() {
        given:
        def request = get('/cabs/0');
        //request.param("blah", "blah")
        cabService.findById(0) >> null

        when:
        def response = mockMvc.perform(request).andReturn().response

        then: "Status is 200 and the response is 'Hello world!'"
        response.status == OK.value()
        response.contentAsString == "" // "{\"id\":0,\"location\":0,\"status\":\"ASSIGNED\"}"
    }

    @TestConfiguration
    static class MockConfig {
        def detachedMockFactory = new DetachedMockFactory()

        @Bean
        CabRepository cabService() {
            return detachedMockFactory.Stub(CabRepository)
        }

        @Bean
        TaxiOrderRepository taxiOrderRepository() {
          return detachedMockFactory.Stub(TaxiOrderRepository)
        }

        @Bean
        TaxiOrderService taxiOrderService() {
          return detachedMockFactory.Stub(TaxiOrderService)
        }

        @Bean
        RouteRepository routeRepository() {
          return detachedMockFactory.Stub(RouteRepository)
        }

        @Bean
        TaskRepository taskRepository() {
          return detachedMockFactory.Stub(TaskRepository)
        }

    }
}
