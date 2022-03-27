package no.kabina.kaboot.routes;

public class LegPojo {

  private Route.RouteStatus status;

  public LegPojo(Route.RouteStatus status) {
    this.status = status;
  }

  public LegPojo() { // controller tests need this
    super();
  }

  public void setStatus(Route.RouteStatus status) {
    this.status = status;
  }

  public Route.RouteStatus getStatus() {
    return status;
  }
}
