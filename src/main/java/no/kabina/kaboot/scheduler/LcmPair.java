package no.kabina.kaboot.scheduler;

public class LcmPair {
  private int cab;
  private int clnt;

  public LcmPair(int cab, int clnt) {
    this.cab = cab;
    this.clnt = clnt;
  }

  public int getCab() {
    return cab;
  }

  public int getClnt() {
    return clnt;
  }
}