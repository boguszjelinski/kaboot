package no.kabina.kaboot.scheduler;

import static java.lang.StrictMath.abs;

public class DistanceService {

  public static int getDistance(int from, int to) {
    return abs(from - to);
  }
}
