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
import javax.persistence.OrderBy;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;

@Entity
public class Route {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  private RouteStatus status;

  protected Route() { }

  public Route(RouteStatus status) {
    this.status = status;
  }

  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  @JoinColumn(name = "cab_id", nullable = false)
  private Cab cab;

  @OneToMany(fetch = FetchType.LAZY) // mappedBy = "route", cascade = CascadeType.ALL
  @JoinColumn(name = "route_id")
  @OrderBy("place")
  private Set<Leg> legs = new HashSet<>();

  @OneToMany(fetch = FetchType.LAZY) // many customers that can ride within one route (pool)
  @JoinColumn(name = "route_id")
  private Set<TaxiOrder> orders = new HashSet<>();

  public enum RouteStatus {
        PLANNED,   // proposed by Pool
        ASSIGNED,  // not confirmed, initial status
        ACCEPTED,  // plan accepted by customer, waiting for the cab
        REJECTED,  // proposal rejected by customer(s)
        ABANDONED, // cancelled after assignment but before 'PICKEDUP'
        STARTED,   // status needed by legs
        COMPLETED
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) { // for testing only
    this.id = id;
  }

  public void setStatus(RouteStatus stat) {
    this.status = stat;
  }

  public RouteStatus getStatus() {
    return status;
  }

  public void setCab(Cab c) {
    this.cab = c;
  }

  public Cab getCab() {
    return cab;
  }

  public void setLegs(Set<Leg> legs) {
    this.legs = legs;
  }

  public Set<Leg> getLegs() {
    return legs;
  }

  public Set<TaxiOrder> getOrders() {
    return orders;
  }

  public void setOrders(Set<TaxiOrder> orders) {
    this.orders = orders;
  }
}
