package no.kabina.kaboot.stops;

import no.kabina.kaboot.routes.Route;

public class RouteWithEta {

  private final int eta;

  private final Route route;

  public RouteWithEta(int eta, Route route) {
    this.eta = eta;
    this.route = route;
  }

  public int getEta() {
    return eta;
  }

  public Route getRoute() {
    return route;
  }
}
