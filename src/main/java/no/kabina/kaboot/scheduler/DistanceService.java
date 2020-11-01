package no.kabina.kaboot.scheduler;

import static java.lang.StrictMath.abs;

import org.springframework.stereotype.Service;

@Service
public class DistanceService {

  public int getDistance(int from, int to) {
    return abs(from - to);
  }
}
