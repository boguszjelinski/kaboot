package no.kabina.kaboot.cabs;

import java.util.HashSet;
import java.util.Set;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import no.kabina.kaboot.orders.TaxiOrder;

@Entity
public class Cab {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  private int location; // updated while in route
  private CabStatus status;

  protected Cab() { }

  public Cab(int loc, CabStatus status) {
    this.location = loc;
    this.status = status;
  }

  @OneToMany
  @JoinColumn(name = "cab_id")
  private Set<TaxiOrder> items = new HashSet<>();

  public Long getId() {
    return id;
  }
  public void setId(Long id) {
    this.id = id;
  }

  public int getLocation() {
    return location;
  }

  public CabStatus getStatus() {
    return status;
  }

  public void setStatus(CabStatus stat) {
    this.status = stat;
  }

  public enum CabStatus {
    ASSIGNED,
    FREE,
    CHARGING, // out of order, ...
  }
}
