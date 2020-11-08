package no.kabina.kaboot.scheduler;

import no.kabina.kaboot.orders.TaxiOrder;

public class PoolElement implements Comparable<PoolElement> {
    public TaxiOrder[] cust;
    public int numbOfCust; // pools with 2,3 or 4 passengers
    public int cost;

    PoolElement () {}

    @Override
    public int compareTo(PoolElement pool) {
        return this.cost - pool.cost;
    }
}