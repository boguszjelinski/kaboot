package no.kabina.kaboot.stops;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.kabina.kaboot.dispatcher.DistanceService;
import no.kabina.kaboot.routes.Leg;
import no.kabina.kaboot.routes.LegRepository;
import no.kabina.kaboot.routes.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StopService {
  private final Logger logger = LoggerFactory.getLogger(StopService.class);
  private final StopRepository stopRepository;
  private final LegRepository legRepository;
  private final DistanceService distanceService;

  public StopService(StopRepository stopRepository, LegRepository legRepository, DistanceService distanceService) {
    this.stopRepository = stopRepository;
    this.legRepository = legRepository;
    this.distanceService = distanceService;
  }

  public StopTraffic findTraffic(int standId) {
    // we need one status more - STARTED. As now ASSIGNED and not COMPLETED means STARTED
    List<Leg> fromLegs = legRepository.findByFromStandAndStatus(standId, Route.RouteStatus.ASSIGNED);
    // toLegs as well as this can be the last leg in a route - !! mark as END STOP in frontend
    List<Leg> toLegs = legRepository.findByToStandAndStatus(standId, Route.RouteStatus.ASSIGNED);
    return findTrafficForLegs(standId, Stream.concat(fromLegs.stream(), toLegs.stream()).collect(Collectors.toList()));
  }

  private StopTraffic findTrafficForLegs(int standId, List<Leg> legs) {
    List<RouteWithEta> ret = new ArrayList<>();
    // estimate ETA
    for (Leg l : legs) {
      int eta = calculateEta(standId, l.getRoute());
      if (eta == -1) {
        continue;
      }
      ret.add(new RouteWithEta(eta, l.getRoute()));
    }
    // the nearest cabs should appear first
    ret.sort(Comparator.comparing(RouteWithEta::getEta));
    Stop s = null;
    Optional<Stop> o = stopRepository.findById((long) standId);
    if (o.isPresent()) {
      s = o.get();
    }
    return new StopTraffic(s, ret);
  }

  private int calculateEta(int standId, Route route) {
    Leg[] legs = new Leg[route.getLegs().size()];
    route.getLegs().toArray(legs);
    int idx = 0;
    int eta = 0;
    for (; idx < legs.length; idx++) {
      if (legs[idx].getFromStand() == standId) {
        break; // if standId happens to be toStand in the last leg and this break never occurs - that is just OK
      }
      // there are two situations - active (currently executed) leg and legs waiting for pick-up
      int distance = distanceService.distance[legs[idx].getFromStand()][legs[idx].getToStand()];
      if (legs[idx].getStatus().equals(Route.RouteStatus.STARTED)) {
        if (legs[idx].getStarted() == null) { // some error
          eta += distance;
        } else {
          int minutes = (int) ChronoUnit.MINUTES.between(legs[idx].getStarted(), LocalDateTime.now());
          eta += distance - minutes; // TASK: assumption 1km = 1min, see also CabRunnable: waitMins(getDistance
        }
      } else if (legs[idx].getStatus().equals(Route.RouteStatus.ASSIGNED)) {
        eta += distance;
      } else {
        logger.warn("Leg {} is in not STARTED, nor ASSIGNED {}", legs[idx].getId(), route.getId());
      }
    }
    return eta;
  }
}
