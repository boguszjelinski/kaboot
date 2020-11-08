package no.kabina.kaboot.scheduler;

import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;

public class TempModel {
    public Cab[] supply;
    public TaxiOrder[] demand;

    public TempModel(Cab[] supply, TaxiOrder[] demand) {
        this.supply = supply;
        this.demand = demand;
    }
}