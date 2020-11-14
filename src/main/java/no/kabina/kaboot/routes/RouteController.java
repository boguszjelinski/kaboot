package no.kabina.kaboot.routes;

import java.util.List;
import no.kabina.kaboot.utils.AuthUtils;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RouteController {

  private final RouteRepository repository;
  private final LegRepository legRepo;

  public RouteController(RouteRepository repository, LegRepository legRepo) {
    this.repository = repository;
    this.legRepo = legRepo;
  }

  // curl -v --user cab0:cab0 http://localhost:8080/routes
  // [{"id":0,"status":"ASSIGNED"}]
  @GetMapping(path = "/routes", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<Route> getValidRouteByCab(Authentication auth) {
    Long cabId = AuthUtils.getUserId(auth, "ROLE_CAB");
    return retrieveByCabIdAndStatus(cabId, Route.RouteStatus.ASSIGNED);
  }

  List<Route> retrieveByCabIdAndStatus(Long cabId, Route.RouteStatus status) {
    List<Route> routes = repository.findByCabIdAndStatus(cabId, status);
    /*   for (Route r: routes) {
       r.setLegs(legRepo.findByRouteId(r.getId()));
    }*/
    return routes;
  }
}
