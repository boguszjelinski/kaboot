package no.kabina.kaboot.stats;

import java.util.List;

public class StatResponse {
  private final List<Stat> kpis;
  private final List<Object[]> orders;
  private final List<Object[]> cabs;

  /** constructor.
   *
   */
  public StatResponse(List<Stat> kpis, List<Object[]> orders, List<Object[]> cabs) {
    this.kpis = kpis;
    this.orders = orders;
    this.cabs = cabs;
  }

  public List<Stat> getKpis() {
    return kpis;
  }

  public List<Object[]> getOrders() {
    return orders;
  }

  public List<Object[]> getCabs() {
    return cabs;
  }
}
