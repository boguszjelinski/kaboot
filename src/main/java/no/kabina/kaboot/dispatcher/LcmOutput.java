package no.kabina.kaboot.dispatcher;

import java.util.List;

public class LcmOutput {
  private List<LcmPair> pairs;
  private int minVal;

  public LcmOutput(List<LcmPair> pairs, int minVal) {
    this.pairs = pairs;
    this.minVal = minVal;
  }

  public List<LcmPair> getPairs() {
    return pairs;
  }

  public int getMinVal() {
    return minVal;
  }
}
