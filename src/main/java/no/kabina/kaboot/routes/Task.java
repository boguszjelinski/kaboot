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
public class Task { // a leg of a route
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  private int fromStand; // go to
  private int toStand;
  private int place; // place in line; ID of the next would be better, but we don't have this id while creating 'entities' in JPA
  private Route.RouteStatus status;

  protected Task() { }

  public Task(int fromStand, int toStand, int place, Route.RouteStatus status) {
      this.fromStand = fromStand;
      this.toStand = toStand;
      this.place = place;
      this.status = status;
  }

  @OneToMany
  @JoinColumn(name = "task_id")
  private Set<TaxiOrder> items = new HashSet<>();

  @ManyToOne(optional = true, fetch = FetchType.LAZY)
  @JoinColumn(name = "route_id", nullable = true)
  private Route route;

  public Long getId() {
    return id;
  }

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
}
