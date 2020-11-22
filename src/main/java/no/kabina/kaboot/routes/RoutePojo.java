package no.kabina.kaboot.routes;

public class RoutePojo {

  private Route.RouteStatus status;

  public RoutePojo(Route.RouteStatus status) {
    this.status = status;
  }

  public RoutePojo() {
    super();
  }

  public void setStatus(Route.RouteStatus status) {
    this.status = status;
  }

  public Route.RouteStatus getStatus() {
    return status;
  }
}
