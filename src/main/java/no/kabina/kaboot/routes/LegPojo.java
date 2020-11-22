package no.kabina.kaboot.routes;

public class LegPojo {

  private Route.RouteStatus status;

  public LegPojo(Route.RouteStatus status) {
    this.status = status;
  }

  public LegPojo() {
    super();
  }

  public void setStatus(Route.RouteStatus status) {
    this.status = status;
  }

  public Route.RouteStatus getStatus() {
    return status;
  }
}
