package no.kabina.kaboot.orders;

public class TaxiOrderPOJO { // because SonarLint complained

    protected TaxiOrderPOJO.OrderStatus status;
    protected int fromStand;
    protected int toStand;
    protected int maxWait; // how long can I wait for a cab
    protected int maxLoss; // [%] how long can I lose while in pool
    protected boolean shared; // can be in a pool ?

    protected TaxiOrderPOJO() {}

    public TaxiOrderPOJO(int fromStand, int toStand, int maxWait, int maxLoss, boolean shared) {
        this.fromStand = fromStand;
        this.toStand = toStand;
        this.maxWait = maxWait;
        this.maxLoss = maxLoss;
        this.shared = shared;
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

    public int getMaxLoss() {
        return maxLoss;
    }

    public boolean isShared() {
        return shared;
    }

    public enum OrderStatus {
        RECEIVED,  // sent by customer
        ASSIGNED,  // assigned to a cab, a proposal sent to customer with time-of-arrival
        ACCEPTED,  // plan accepted by customer, waiting for the cab
        CANCELLED, // cancelled before assignment
        REJECTED,  // proposal rejected by customer
        ABANDONED, // cancelled after assignment but before 'PICKEDUP'
        REFUSED,   // no cab available, cab broke down at any stage
        PICKEDUP,
        COMPLETE
    }
}
