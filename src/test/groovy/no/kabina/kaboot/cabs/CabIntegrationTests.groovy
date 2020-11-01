package no.kabina.kaboot.cabs

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

import spock.lang.Specification

@SpringBootTest
class CabIntegrationTests extends Specification {

  @Autowired
  CabRepository cabRepository;

  def "when findById is performed and no record is found then content is null"() {
    when:
      Cab cab = cabRepository.findById(0)

    then:
      cab == null
  }
}
