package no.kabina.kaboot.scheduler;

public class PoolElement implements Comparable<PoolElement> {
    public int [] cust;
    public int cost;

    PoolElement () {}

    @Override
    public int compareTo(PoolElement pool) {
        return this.cost - pool.cost;
    }
}