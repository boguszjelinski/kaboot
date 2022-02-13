package no.kabina.kaboot.dispatcher;

import java.util.List;
import no.kabina.kaboot.stops.Stop;
import no.kabina.kaboot.stops.StopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("singleton")
public class DistanceService {
  private final Logger logger = LoggerFactory.getLogger(DistanceService.class);
  public int [][] distance; // DB IDs will be used to address this table, so beware with IDs in the DB
  public int [] bearing;

  public DistanceService() { }

  // for non-integration tests
  public DistanceService(int[][] dists, int[] bearing) {
    this.distance = dists;
    this.bearing = bearing;
  }

  public void initDistance(StopRepository stopRepository) {
    logger.info("initDistance START");
    List<Stop> stops = stopRepository.findAll();
    distance = new int[stops.size()][stops.size()];
    bearing = new int[stops.size()];
    for (int i = 0; i < stops.size(); i++) {
      distance[i][i] = 0;
      bearing[i] = stops.get(i).getBearing();
      for (int j = i + 1; j < stops.size(); j++) {
        Stop from  = stops.get(i);
        Stop to  = stops.get(j);
        double d = dist(from.getLatitude(), from.getLongitude(),
                        to.getLatitude(), to.getLongitude(),
                    'K');
        int ii = from.getId().intValue();
        int jj = to.getId().intValue();
        distance[ii][jj] = (int) d; // TASK: we might need a better precision - meters/seconds
        distance[jj][ii] = distance[ii][jj];
      }
    }
    logger.info("initDistance STOP");
    // max=55, avg=12 for Las Vegas
  }

  public int[][] getDistances() {
    return this.distance;
  }

  // https://dzone.com/articles/distance-calculation-using-3
  private double dist(double lat1, double lon1, double lat2, double lon2, char unit) {
    double theta = lon1 - lon2;
    double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1))
                  * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
    dist = Math.acos(dist);
    dist = rad2deg(dist);
    dist = dist * 60 * 1.1515;
    if (unit == 'K') {
      dist = dist * 1.609344;
    } else if (unit == 'N') {
      dist = dist * 0.8684;
    }
    return (dist);
  }

  /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
  /*::  This function converts decimal degrees to radians             :*/
  /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
  private double deg2rad(double deg) {
    return (deg * Math.PI / 180.0);
  }

  /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
  /*::  This function converts radians to decimal degrees             :*/
  /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
  private double rad2deg(double rad) {
    return (rad * 180.0 / Math.PI);
  }
}
