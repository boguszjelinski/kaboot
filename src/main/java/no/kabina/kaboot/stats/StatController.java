package no.kabina.kaboot.stats;

import no.kabina.kaboot.cabs.CabRepository;
import no.kabina.kaboot.orders.TaxiOrderRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin("*")
@RestController
public class StatController {

  private final StatRepository statRepository;
  private final TaxiOrderRepository taxiOrderRepository;
  private final CabRepository cabRepository;

  /** constructor.
   */
  public StatController(StatRepository statRepository, TaxiOrderRepository taxiOrderRepository,
                        CabRepository cabRepository) {
    this.statRepository = statRepository;
    this.taxiOrderRepository = taxiOrderRepository;
    this.cabRepository = cabRepository;
  }

  /** API for a React app.

   * @return stats
   */
  @GetMapping(path = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
  public StatResponse getAllStats() {
    return new StatResponse(statRepository.findAll(),
                            taxiOrderRepository.countStatus(),
                            cabRepository.countStatus());
  }
}
