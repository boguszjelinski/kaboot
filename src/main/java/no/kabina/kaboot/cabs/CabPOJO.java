package no.kabina.kaboot.cabs;

import no.kabina.kaboot.orders.TaxiOrder;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

public class CabPOJO {

  private int location;
  private Cab.CabStatus status;

  protected CabPOJO() { }

  public CabPOJO(int loc, Cab.CabStatus status) {
    this.location = loc;
    this.status = status;
  }

  public int getLocation() {
    return location;
  }

  public Cab.CabStatus getStatus() {
    return status;
  }

  public void setStatus(Cab.CabStatus stat) {
    this.status = stat;
  }

}
