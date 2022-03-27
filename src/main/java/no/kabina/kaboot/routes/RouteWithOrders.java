package no.kabina.kaboot.routes;

import java.util.List;
import no.kabina.kaboot.orders.TaxiOrder;

public class RouteWithOrders {

  private final List<TaxiOrder> orders;
  private final Route route;

  /** constructor.
   */
  public RouteWithOrders(Route route, List<TaxiOrder> orders) {
    this.orders = orders;
    this.route = route;
    for (TaxiOrder o : orders) {
      o.setRoute(null);
    }
  }

  public Route getRoute() {
    return route;
  }

  public List<TaxiOrder> getOrders() {
    return orders;
  }
}
