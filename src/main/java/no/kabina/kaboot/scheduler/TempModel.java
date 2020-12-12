package no.kabina.kaboot.scheduler;

import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;

public class TempModel {
  private Cab[] supply;
  private TaxiOrder[] demand;

  public TempModel(Cab[] supply, TaxiOrder[] demand) {
    this.supply = supply;
    this.demand = demand;
  }

  public Cab[] getSupply() {
    return supply;
  }

  public void setSupply(Cab[] supply) {
    this.supply = supply;
  }

  public TaxiOrder[] getDemand() {
    return demand;
  }

  public void setDemand(TaxiOrder[] demand) {
    this.demand = demand;
  }
}