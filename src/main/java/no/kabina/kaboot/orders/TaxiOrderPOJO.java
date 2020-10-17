package no.kabina.kaboot.orders;

public class TaxiOrderPOJO { // because SonarLint complained

    protected TaxiOrder.OrderStatus status;
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
}
