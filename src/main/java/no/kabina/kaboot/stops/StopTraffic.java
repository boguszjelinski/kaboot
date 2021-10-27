package no.kabina.kaboot.stops;

import java.util.List;

public class StopTraffic {
  private final Stop stop;
  private final List<RouteWithEta> routes;

  public StopTraffic(Stop stop, List<RouteWithEta> routes) {
    this.stop = stop;
    this.routes = routes;
  }

  public Stop getStop() {
    return stop;
  }

  public List<RouteWithEta> getRoutes() {
    return routes;
  }
}
