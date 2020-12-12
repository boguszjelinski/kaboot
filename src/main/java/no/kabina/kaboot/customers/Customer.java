package no.kabina.kaboot.customers;

import java.util.HashSet;
import java.util.Set;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import no.kabina.kaboot.orders.TaxiOrder;

// MariaDB
// mysql -u kabina -p -D kabina < customer-just-inserts.sql

@Entity
public class Customer {
  @Id
  @GeneratedValue(strategy= GenerationType.AUTO)
  private Long id;

  @OneToMany
  @JoinColumn(name = "customer_id")
  private Set<TaxiOrder> items = new HashSet<>();

  protected Customer() { }

  public Long getId() {
    return id;
  }
}
