package no.kabina.kaboot.cabs;

public class CabPojo {

  private int location;
  private String name;
  private Cab.CabStatus status;

  private CabPojo() { }

  public CabPojo(int loc, String name, Cab.CabStatus status) {
    this.location = loc;
    this.name = name;
    this.status = status;
  }

  public int getLocation() {
    return location;
  }

  public String getName() {
    return name;
  }

  public Cab.CabStatus getStatus() {
    return status;
  }

  public void setStatus(Cab.CabStatus stat) {
    this.status = stat;
  }

}
