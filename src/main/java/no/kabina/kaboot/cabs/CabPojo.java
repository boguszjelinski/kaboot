package no.kabina.kaboot.cabs;

public class CabPojo {

  private int location;
  private Cab.CabStatus status;

  private CabPojo() { }

  public CabPojo(int loc, Cab.CabStatus status) {
    this.location = loc;
    this.status = status;
  }

  public int getLocation() {
    return location;
  }

  public Cab.CabStatus getStatus() {
    return status;
  }

  public void setStatus(Cab.CabStatus stat) {
    this.status = stat;
  }

}
