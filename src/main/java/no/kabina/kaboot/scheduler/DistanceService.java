package no.kabina.kaboot.scheduler;

import static java.lang.StrictMath.abs;

public class DistanceService {

  private DistanceService() {} // hiding public

  public static int getDistance(int from, int to) {
    return abs(from - to);
  }
}
