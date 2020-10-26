package no.kabina.kaboot.cabs

import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.http.HttpStatus.*

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc

import no.kabina.kaboot.orders.TaxiOrderRepository
import no.kabina.kaboot.orders.TaxiOrderService
import no.kabina.kaboot.routes.RouteRepository
import no.kabina.kaboot.routes.TaskRepository
import spock.lang.Specification
import spock.mock.DetachedMockFactory

@WebMvcTest
class CabControlerTests extends Specification {
    def cabController = new CabController()
    MockMvc mockMvc = standaloneSetup(cabController).build()
    def cabService = Mock(CabRepository)

    def setup() {
        cabController.repository = cabService
    }

    def "when get is performed and no record is found then the response has status 200 and content is ''"() {
        given:
        def request = get('/cabs/0');
        cabService.findById(0) >> null

        when:
        def response = mockMvc.perform(request).andReturn().response

        then: "Status is 200 and the response is ''"
        response.status == OK.value()
        response.contentAsString == "" // "{\"id\":0,\"location\":0,\"status\":\"ASSIGNED\"}"
    }

  def "when get is performed and a record is found then the response has status 200 and content is '{...}'"() {
    given:
      def request = get('/cabs/1');
      def cab = new Cab(10, Cab.CabStatus.ASSIGNED)
      cab.setId(1)
      cabService.findById(1) >> cab

    when:
      def response = mockMvc.perform(request).andReturn().response

    then: "Status is 200 and the response is '{...}'"
      response.status == OK.value()
      response.contentAsString == "{\"id\":1,\"location\":10,\"status\":\"ASSIGNED\"}"
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
