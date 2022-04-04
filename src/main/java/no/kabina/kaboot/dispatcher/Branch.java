package no.kabina.kaboot.dispatcher;

import org.apache.commons.lang3.builder.HashCodeBuilder;

class Branch implements Comparable<Branch> {
    public String key; // used to remove duplicates and search in hashmap
    public int cost;
    public int outs; // number of OUT nodes, so that we can guarantee enough IN nodes
    // TASK wrong naming - order id
    public int[] custIDs; // we could get rid of it to gain on memory (key stores this too); but we would lose time on parsing
    public char[] custActions;

    // to create "key" effectively
    public int[] custIDsSorted;
    public char[] custActionsSorted;

    // constructor for leavs
    Branch(int cost, int outs, int[] ids, char[] actions, int[] idsSorted, char[] actionsSorted) {
        this.cost = cost;
        this.outs = outs;
        this.custIDs = ids;
        this.custActions = actions;
        this.custIDsSorted = idsSorted;
        this.custActionsSorted = actionsSorted;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < idsSorted.length; i++) {
            buf.append(idsSorted[i]).append(actionsSorted[i]);
        }
        this.key = buf.toString();
    }

    // constructor for non-leaves
    Branch(String key, int cost, int outs, int[] ids, char[] actions, int[] idsSorted,
           char[] actionsSorted) {
        this.key = key;
        this.cost = cost;
        this.outs = outs;
        this.custIDs = ids;
        this.custActions = actions;
        this.custIDsSorted = idsSorted;
        this.custActionsSorted = actionsSorted;
    }

    @Override
    public int compareTo(Branch pool) {
        return this.key.compareTo(pool.key);
    }

    @Override
    public boolean equals(Object pool) {
        if (pool == null || this.getClass() != pool.getClass()) {
            return false;
        }
        return this.key.equals(((Branch) pool).key);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(key)
                .append(cost)
                .append(custIDs[0])
                .append(custIDs[1])
                .toHashCode();
    }
}