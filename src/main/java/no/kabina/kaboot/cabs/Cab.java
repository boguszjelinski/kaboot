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

  private int location; // not used now

  protected Cab() { }

  public Cab(int loc) {
    this.location = loc;
  }

  @OneToMany
  @JoinColumn(name = "cab_id")
  private Set<TaxiOrder> items = new HashSet<>();

  public Long getId() {
    return id;
  }

  public int getLocation() {
    return location;
  }
}
