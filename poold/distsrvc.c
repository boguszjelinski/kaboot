#include <stdio.h>
#include <math.h>
#include "dynapool.h"

extern Stop stops[MAXSTOPSNUMB];
extern int stopsNumb;

short distance[MAXSTOPSNUMB][MAXSTOPSNUMB];
const double m_pi_180 = M_PI / 180.0;
const double rev_m_pi_180 = 180.0 / M_PI;

double deg2rad(double deg) { return (deg * m_pi_180); }
double rad2deg(double rad) { return (rad * rev_m_pi_180); }

// https://dzone.com/articles/distance-calculation-using-3
double dist(double lat1, double lon1, double lat2, double lon2) {
    double theta = lon1 - lon2;
    double dist = sin(deg2rad(lat1)) * sin(deg2rad(lat2)) + cos(deg2rad(lat1))
                  * cos(deg2rad(lat2)) * cos(deg2rad(theta));
    dist = acos(dist);
    dist = rad2deg(dist);
    dist = dist * 60 * 1.1515;
    dist = dist * 1.609344;
    return dist;
}

void initDistance(void) {
    for (int i = 0; i < stopsNumb; i++) {
        distance[i][i] = 0;
        for (int j = i + 1; j < stopsNumb; j++) {
            double d = dist(stops[i].latitude, stops[i].longitude, stops[j].latitude, stops[j].longitude);
            distance[stops[i].id][stops[j].id] = (int) d; // TASK: we might need a better precision - meters/seconds
            distance[stops[j].id][stops[i].id] = distance[stops[i].id][stops[j].id];
        }
    }
}



