package no.kabina.kaboot.routes;

import java.util.HashSet;
import java.util.Set;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import no.kabina.kaboot.orders.TaxiOrder;

@Entity
public class Leg { // a leg of a route
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  private int fromStand; // go to
  private int toStand;
  private int place; // place in line; ID of the next would be better, but we don't have this id while creating 'entities' in JPA
  private Route.RouteStatus status;

  protected Leg() { }

  public Leg(int fromStand, int toStand, int place, Route.RouteStatus status) {
      this.fromStand = fromStand;
      this.toStand = toStand;
      this.place = place;
      this.status = status;
  }

  @OneToMany(fetch = FetchType.LAZY)
  @JoinColumn(name = "leg_id")
  private Set<TaxiOrder> orders = new HashSet<>();

  @ManyToOne(optional = true, fetch = FetchType.LAZY)
  @JoinColumn(name = "route_id", nullable = true)
  private Route route;

  public Long getId() { return id; }
  public void setId (Long id) { this.id = id; }

  public int getFromStand() {
    return fromStand;
  }

  public int getToStand() {
    return toStand;
  }

  public int getPlace() {
    return place;
  }

  public Route.RouteStatus getStatus() {
    return status;
  }

  public void setStatus(Route.RouteStatus status) {
    this.status = status;
  }

  public void setRoute(Route route) {
    this.route = route;
  }
  public Route getRoute() {
    return this.route;
  }
}
