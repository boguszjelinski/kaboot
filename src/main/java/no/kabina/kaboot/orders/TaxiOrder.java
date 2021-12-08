package no.kabina.kaboot.orders;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.customers.Customer;
import no.kabina.kaboot.routes.Leg;
import no.kabina.kaboot.routes.Route;

// insert into taxi_order (id, from_stand, to_stand, max_wait, max_loss, shared)
// values (0,1,2,10,30,true)

@Entity
public class TaxiOrder {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  public Long id;

  protected TaxiOrder.OrderStatus status;
  public int fromStand;
  public int toStand;
  protected int maxWait; // how long can I wait for a cab [min]
  protected int maxLoss; // [%] how long can I lose while in pool
  protected boolean shared; // can be in a pool ?
  private LocalDateTime rcvdTime;

  @Column(nullable = true)
  private LocalDateTime atTime; // ASAP if not set

  @Column(nullable = true)
  protected Integer eta; // set when assigned

  @Column(nullable = true)
  protected Boolean inPool; // was actually in pool

  public TaxiOrder() {}

  /**
   *
   * @param fromStand
   * @param toStand
   * @param maxWait
   * @param maxLoss
   * @param shared
   * @param status
   */
  public TaxiOrder(int fromStand, int toStand, int maxWait, int maxLoss, boolean shared, OrderStatus status,
                   LocalDateTime atTime) {
    this.fromStand = fromStand;
    this.toStand = toStand;
    this.maxWait = maxWait;
    this.maxLoss = maxLoss;
    this.shared = shared;
    this.status = status;
    this.rcvdTime = LocalDateTime.now();
    this.atTime = atTime;
  }

  // asigned cab
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cab_id", nullable = true)
  private Cab cab;  // an order can be serviced by ONE cab only, but one cab can service MANY orders throughout the day

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = true)
  private Customer customer;

  // also for data integrity checks; the pick-up task; null if cab was already there
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "leg_id", nullable = true)
  private Leg leg;  // an order can be pick-up by one task, but one task can pick up MANY orders/customers
  // this foreign key will give the driver information whom to pick up and where to drop-off

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "route_id", nullable = true)
  private Route route;

  @Override
  public String toString() {
    return String.format("Order[id=%d, from='%s', to='%s']", id, fromStand, toStand);
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public int getFromStand() {
    return fromStand;
  }

  public int getToStand() {
    return toStand;
  }

  public int getMaxWait() {
    return maxWait;
  }

  public void setMaxWait(int mw) {
    this.maxWait = mw;
  }

  public int getMaxLoss() {
    return maxLoss;
  }

  public boolean isShared() {
    return shared;
  }

  public void setStatus(OrderStatus status) {
    this.status = status;
  }

  public OrderStatus getStatus() {
    return this.status;
  }

  public enum OrderStatus {
        RECEIVED,  // sent by customer
        ASSIGNED,  // assigned to a cab, a proposal sent to customer with time-of-arrival
        ACCEPTED,  // plan accepted by customer, waiting for the cab
        CANCELLED, // cancelled by customer before assignment
        REJECTED,  // proposal rejected by customer
        ABANDONED, // cancelled after assignment but before 'PICKEDUP'
        REFUSED,   // no cab available, cab broke down at any stage
        PICKEDUP,
        COMPLETED
  }

  public void setCustomer(Customer cust) {
    this.customer = cust;
  }

  public int getEta() {
    return eta;
  }

  public void setEta(int eta) {
    this.eta = eta;
  }

  public boolean isInPool() {
    return inPool;
  }

  public void setInPool(boolean inPool) {
    this.inPool = inPool;
  }

  public Customer getCustomer() {
    return customer;
  }

  public void setCab(Cab cab) {
    this.cab = cab;
  }

  public Cab getCab() {
    return this.cab;
  }

  public Route getRoute() {
    return this.route;
  }

  public Leg getLeg() {
    return this.leg;
  }

  public void setLeg(Leg leg) {
    this.leg = leg;
  }

  public void setRoute(Route r) {
    this.route = r;
  }

  public LocalDateTime getRcvdTime() {
    return rcvdTime;
  }

  public void setRcvdTime(LocalDateTime rcvdTime) {
    this.rcvdTime = rcvdTime;
  }

  public LocalDateTime getAtTime() {
    return atTime;
  }

  public void setAtTime(LocalDateTime atTime) {
    this.atTime = atTime;
  }

}
