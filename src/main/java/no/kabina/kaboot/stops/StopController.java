package no.kabina.kaboot.stops;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin("*")
@RestController
public class StopController {
  private final Logger logger = LoggerFactory.getLogger(StopController.class);

  private final StopRepository stopRepository;
  private final StopService stopService;

  public StopController(StopRepository stopRepository, StopService stopService) {
    this.stopRepository = stopRepository;
    this.stopService = stopService;
  }

  @GetMapping("/stops")
  public List<Stop> all(Authentication auth) {
    logger.info("GET stops");
    return stopRepository.findAll();
  }

  /** controller for routing tables.

   * @param id stopId
   * @param auth authentication data
   * @return two lists
   */
  @GetMapping("/stops/{id}/traffic")
  public StopTraffic getTraffic(@PathVariable int id, Authentication auth) {
    logger.info("GET traffic for stop={}", id);
    StopTraffic ret = stopService.findTraffic(id);
    if (ret != null && ret.getRoutes() != null) {
      for (RouteWithEta route : ret.getRoutes()) {
        route.getRoute().getCab().setOrders(null);
        route.getRoute().setOrders(null);
      }
    }
    return ret;
  }
}
