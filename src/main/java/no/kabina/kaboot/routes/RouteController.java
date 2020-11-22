package no.kabina.kaboot.routes;

import java.util.List;
import no.kabina.kaboot.utils.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class RouteController {

  private Logger logger = LoggerFactory.getLogger(RouteController.class);

  private final RouteRepository repository;
  private final LegRepository legRepo;

  public RouteController(RouteRepository repository, LegRepository legRepo) {
    this.repository = repository;
    this.legRepo = legRepo;
  }

  // curl -v --user cab0:cab0 http://localhost:8080/routes
  // {"id":0,"status":"ASSIGNED"}
  @GetMapping(path = "/routes", produces = MediaType.APPLICATION_JSON_VALUE)
  public Route getValidRouteByCab(Authentication auth) {
    Long cabId = AuthUtils.getUserId(auth, "ROLE_CAB");
    List<Route> routes = retrieveByCabIdAndStatus(cabId, Route.RouteStatus.ASSIGNED);
    if (routes == null || routes.isEmpty()) {
      return null;
    } else {
      Route r = routes.get(0);
      r.setCab(null);
      for (Leg l : r.getLegs()) {
        l.setRoute(null);
      }
      return r; // just the first route
    }
  }

  // mainly to mark COMPLETED and to bill the customer
  @PutMapping(value = "/routes/{id}", consumes = "application/json")
  public Route updateLeg(@PathVariable Long id, @RequestBody RoutePojo route, Authentication auth) {
    logger.info("PUT route={}", id);
    Route r = repository.findById(id).get();

    Long usrId = AuthUtils.getUserId(auth, "ROLE_CAB");
    if (usrId.longValue() != r.getCab().getId()) { // now it is that simple - cab_id == usr_id
      return null;
    }
    r.setStatus(route.getStatus());
    return repository.save(r);
  }

  List<Route> retrieveByCabIdAndStatus(Long cabId, Route.RouteStatus status) {
    List<Route> routes = repository.findByCabIdAndStatus(cabId, status);
    /*   for (Route r: routes) {
       r.setLegs(legRepo.findByRouteId(r.getId()));
    }*/
    return routes;
  }
}
