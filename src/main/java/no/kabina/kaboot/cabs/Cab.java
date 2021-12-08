package no.kabina.kaboot.cabs;

import java.util.HashSet;
import java.util.Set;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import no.kabina.kaboot.orders.TaxiOrder;

/**
 * pg_dump --host localhost --port 5432 --username kabina --format plain --verbose --file "cab.sql" --table public.cab kabina
 * pg_dump --username kabina -d kabina -t cab > cab2.sql
 * pg_dump -U kabina --table=cab --data-only --column-inserts kabina > cab-insert.sql
 * psql -U kabina kabina < cab.sql
 * CREATE TABLE cab AS TABLE cab2;
 */

@Entity
public class Cab {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  private int location; // updated while in route
  private String name;
  private CabStatus status;

  public Cab() {}

  public Cab(int loc, String name, CabStatus status) {
    this.location = loc;
    this.status = status;
    this.name = name;
  }

  @OneToMany(fetch = FetchType.LAZY)
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setItems(Set<TaxiOrder> i) {
    this.items = i;
  }

  public Set<TaxiOrder> getItems() {
    return items;
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
