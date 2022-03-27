package no.kabina.kaboot.stops;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.cabs.CabRepository;
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
  private final CabRepository cabRepository;
  private final DistanceService distanceService;

  /** constructor.
   */
  public StopService(StopRepository stopRepository, LegRepository legRepository,
                     CabRepository cabRepository, DistanceService distanceService) {
    this.stopRepository = stopRepository;
    this.legRepository = legRepository;
    this.cabRepository = cabRepository;
    this.distanceService = distanceService;
  }

  /** a service that finds routes and cabs for a stop.

   * @param standId stop id
   * @return two lists
   */
  public StopTraffic findTraffic(int standId) {
    // we need one status more - STARTED. As now ASSIGNED and not COMPLETED means STARTED
    List<Leg> fromLegs = legRepository.findByFromStandAndStatus(
                                            standId, Route.RouteStatus.ASSIGNED);
    // toLegs as well as this can be the last leg in a route - !! mark as END STOP in frontend
    List<Leg> toLegs = legRepository.findByToStandAndStatus(standId, Route.RouteStatus.ASSIGNED);
    return findTrafficForLegs(standId, Stream.concat(fromLegs.stream(),
                              toLegs.stream()).collect(Collectors.toList()));
  }

  private StopTraffic findTrafficForLegs(int standId, List<Leg> legs) {
    List<Route> temp = new ArrayList<>();
    // estimate ETA
    for (Leg l : legs) {
      temp.add(l.getRoute());
    }
    List<RouteWithEta> routes = new ArrayList<>();
    for (int i = 0; i < temp.size(); i++) {
      Route r = temp.get(i);
      // find duplicates
      boolean found = false;
      for (int j = i + 1; j < temp.size(); j++) {
        if (r.getId().equals(temp.get(j).getId())) {
          found = true;
          break;
        }
      }
      if (!found) {
        int eta = calculateEta(standId, r);
        routes.add(new RouteWithEta(eta, r));
      }
    }
    // the nearest cab should appear first
    routes.sort(Comparator.comparing(RouteWithEta::getEta));
    Stop s = null;
    Optional<Stop> o = stopRepository.findById((long) standId);
    if (o.isPresent()) {
      s = o.get();
    }
    // finally find free cabs standing at the stop and waiting for assignments
    List<Cab> cabs = cabRepository.findByLocationAndStatus(standId, Cab.CabStatus.FREE);
    return new StopTraffic(s, routes, cabs);
  }

  private int calculateEta(int standId, Route route) {
    Leg[] legs = new Leg[route.getLegs().size()];
    route.getLegs().toArray(legs);
    int idx = 0;
    int eta = 0;
    for (; idx < legs.length; idx++) {
      if (legs[idx].getFromStand() == standId) {
        break; // if standId happens to be toStand in the last leg and
        // this break never occurs - that is just OK
      }
      // there are two situations - active (currently executed) leg and legs waiting for pick-up
      int distance = distanceService.distance[legs[idx].getFromStand()][legs[idx].getToStand()];
      if (legs[idx].getStatus().equals(Route.RouteStatus.STARTED)) {
        if (legs[idx].getStarted() == null) { // some error
          eta += distance;
        } else {
          int minutes = (int) ChronoUnit.MINUTES.between(legs[idx].getStarted(),
                        LocalDateTime.now());
          eta += Math.max(distance - minutes, 0);
          // it has taken longer than planned
          // TASK: assumption 1km = 1min, see also CabRunnable: waitMins(getDistance
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
