package no.kabina.kaboot.orders;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class TaxiOrderPojo { // because SonarLint complained

  protected TaxiOrder.OrderStatus status;
  protected int fromStand;
  protected int toStand;
  protected int maxWait; // how long can I wait for a cab
  protected int maxLoss; // [%] how long can I lose while in pool
  protected boolean shared; // can be in a pool ?

  @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
  protected LocalDateTime atTime;

  protected TaxiOrderPojo() {}

  public TaxiOrderPojo(int fromStand, int toStand, int maxWait, int maxLoss, boolean shared, LocalDateTime atTime) {
    this.fromStand = fromStand;
    this.toStand = toStand;
    this.maxWait = maxWait;
    this.maxLoss = maxLoss;
    this.shared = shared;
    this.atTime = atTime;
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

  public TaxiOrder.OrderStatus getStatus() {
    return status;
  }

  public boolean isShared() {
    return shared;
  }

  public void setStatus(TaxiOrder.OrderStatus status) {
    this.status = status;
  }

  public void setFromStand(int fromStand) {
    this.fromStand = fromStand;
  }

  public void setToStand(int toStand) {
    this.toStand = toStand;
  }

  public void setMaxWait(int maxWait) {
    this.maxWait = maxWait;
  }

  public void setMaxLoss(int maxLoss) {
    this.maxLoss = maxLoss;
  }

  public void setShared(boolean shared) {
    this.shared = shared;
  }

  public LocalDateTime getAtTime() {
    return atTime;
  }

  public void setAtTime(LocalDateTime atTime) {
    this.atTime = atTime;
  }
}
