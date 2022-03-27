package no.kabina.kaboot.dispatcher;

import no.kabina.kaboot.orders.TaxiOrder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class PoolElement implements Comparable<PoolElement> {
  private TaxiOrder[] cust;
  public char[] custActions; // DynaPool2: is it IN or OUT
  private int numbOfCust; // pools with 2,3 or 4 passengers
  private int cost;

  /** Defines a route with pickups and drop-offs
  *
  * @param cust customers in the right order - pickups & dropp-offs
  * @param numbOfCust 4, 3 or 2
  * @param cost the cost of route
  */
  public PoolElement(TaxiOrder[] cust, int numbOfCust, int cost) {
    this.cust = cust;
    this.numbOfCust = numbOfCust;
    this.cost = cost;
  }

  /** constructor.
   */
  //DynaPool2
  public PoolElement(TaxiOrder[] cust, char[] custActions, int numbOfCust, int cost) {
    this.cust = cust;
    this.custActions = custActions;
    this.numbOfCust = numbOfCust;
    this.cost = cost;
  }

  public TaxiOrder[] getCust() {
    return cust;
  }

  public void setCust(TaxiOrder[] cust) {
    this.cust = cust;
  }

  public int getNumbOfCust() {
    return numbOfCust;
  }

  public void setNumbOfCust(int numbOfCust) {
    this.numbOfCust = numbOfCust;
  }

  public int getCost() {
    return cost;
  }

  public void setCost(int cost) {
    this.cost = cost;
  }

  @Override
  public int compareTo(PoolElement pool) {
    return this.cost - pool.cost;
  }

  @Override
  public boolean equals(Object pool) {
    if (pool == null || this.getClass() != pool.getClass()) {
      return false;
    }
    return this.cost == ((PoolElement) pool).cost;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(numbOfCust).append(cost).toHashCode();
  }
}