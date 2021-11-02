package no.kabina.kaboot.stops;

import java.util.List;
import no.kabina.kaboot.cabs.Cab;

public class StopTraffic {
  private final Stop stop;
  private final List<RouteWithEta> routes;
  private final List<Cab> cabs;

  public StopTraffic(Stop stop, List<RouteWithEta> routes, List<Cab> cabs) {
    this.stop = stop;
    this.routes = routes;
    this.cabs = cabs;
  }

  public Stop getStop() {
    return stop;
  }

  public List<RouteWithEta> getRoutes() {
    return routes;
  }

  public List<Cab> getCabs() {
    return cabs;
  }
}
