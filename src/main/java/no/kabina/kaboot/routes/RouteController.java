package no.kabina.kaboot.routes;

import java.util.List;
import java.util.Optional;
import no.kabina.kaboot.orders.TaxiOrder;
import no.kabina.kaboot.orders.TaxiOrderRepository;
import no.kabina.kaboot.utils.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class RouteController {

  private final Logger logger = LoggerFactory.getLogger(RouteController.class);

  private final RouteRepository repository;
  private final TaxiOrderRepository taxiOrderRepository;

  public RouteController(RouteRepository repository, TaxiOrderRepository taxiOrderRepository) {
    this.repository = repository;
    this.taxiOrderRepository = taxiOrderRepository;
  }

  /**
   *  curl -v --user cab0:cab0 http://localhost:8080/routes
   *  {"id":0,"status":"ASSIGNED"}
   * @param auth auth
   * @return one route
   */
  @GetMapping(path = "/routes", produces = MediaType.APPLICATION_JSON_VALUE)
  public Route getValidRouteByCab(Authentication auth) {
    Long cabId = AuthUtils.getUserId(auth, "ROLE_CAB");
    return getFirstRoute(retrieveByCabIdAndStatus(cabId, Route.RouteStatus.ASSIGNED));
  }

  @CrossOrigin("http://localhost:3000")
  @GetMapping(path = "/routeswithorders", produces = MediaType.APPLICATION_JSON_VALUE)
  public RouteWithOrders getValidRouteWithOrdersByCab(Authentication auth) {
    Long cabId = AuthUtils.getUserId(auth, "ROLE_CAB");
    Route r = getFirstRoute(retrieveByCabIdAndStatus(cabId, Route.RouteStatus.ASSIGNED));
    return new RouteWithOrders(r, taxiOrderRepository.findByRoute(r));
  }

  private Route getFirstRoute(List<Route> routes) {
    if (routes == null || routes.isEmpty()) {
      return null;
    } else {
      return routes.get(0);
    }
  }

  private Route clearRoute(List<Route> routes) {
    if (routes == null || routes.isEmpty()) {
      return null;
    } else {
      Route r = routes.get(0);
      for (Leg l : r.getLegs()) {
        l.setRoute(null);
      }
      return r; // just the first route
    }
  }

  // mainly to mark COMPLETED and to bill the customer
  @PutMapping(value = "/routes/{id}", consumes = "application/json")
  public String updateRoute(@PathVariable Long id, @RequestBody RoutePojo route, Authentication auth) {
    logger.info("PUT route={}", id);
    Route r = null;
    Optional<Route> ro = repository.findById(id);
    if (ro.isEmpty()) {
      logger.info("No such route={}", id);
      return null;
    }
    r = ro.get();

    Long usrId = AuthUtils.getUserId(auth, "ROLE_CAB");
    if (usrId.longValue() != r.getCab().getId()) { // now it is that simple - cab_id == usr_id
      return null;
    }
    r.setStatus(route.getStatus());
    repository.save(r);
    return "OK";
  }

  List<Route> retrieveByCabIdAndStatus(Long cabId, Route.RouteStatus status) {
    return repository.findByCabIdAndStatus(cabId, status);
  }
}
