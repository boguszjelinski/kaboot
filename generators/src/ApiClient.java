/*
 * Copyright 2020 Bogusz Jelinski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.net.URL;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class ApiClient {

    protected ScriptEngine engine;
    protected static Logger logger;

    public ApiClient() {
        ScriptEngineManager sem = new ScriptEngineManager();
        this.engine = sem.getEngineByName("javascript");
    }

    protected Demand saveOrder(String method, Demand d, int usrId) {
        String json = "{\"fromStand\":" + d.from + ", \"toStand\": " + d.to + ", \"status\":\"" + d.status
                            + "\", \"maxWait\":" + d.maxWait + ", \"maxLoss\": "+ d.maxLoss
                            + ", \"shared\": true";
        if (d.atTime != null) {
            json += ", \"atTime\": \"" + date2String(d.atTime) + "\"";
        }
        json += "}"; // TODO: hardcode, should be a parameter too
        json = saveJSON(method, "orders", "cust" + usrId, d.id, json); // TODO: FAIL, this is not customer id
        if (json == null || json.length() == 0) {
            logger.info("Method " + method + " on 'orders' rejected for cust_id=" + usrId + ", order_id=" + d.id);
            return null;
        }
        return getOrderFromJson(json);
    }

    private String date2String (LocalDateTime tm) {
        //  DateTimeFormatter.ISO_DATE_TIME;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return tm.format(formatter);
    }

    protected Demand getOrder(int userId, int orderId) {
        String json = getEntityAsJson("cust" + userId, "orders/" + orderId);
        return getOrderFromJson(json);
    }

    protected Cab getCab(String entityUrl, int userId, int id) {
        String json = getEntityAsJson("cust" + userId, entityUrl + id);
        return getCabFromJson(json);
    }

    protected Cab getCabAsCab(String entityUrl, int user_id, int id) {
        String json = getEntityAsJson("cab"+user_id, entityUrl + id);
        return getCabFromJson(json);
    }

    protected List<Stop> getStops(String entityUrl, int userId) {
        String json = getEntityAsJson("cab" + userId, entityUrl);
        return getStopsFromJson(json);
    }

    protected void updateCab(int cab_id, Cab cab) {
        String json = "{\"location\":\"" + cab.location + "\", \"status\": \""+ cab.status +"\"," +
                        "\"name\": \"A"+ cab_id +"\"}";
        log("cab", cab_id, json);
        saveJSON("PUT", "cabs", "cab" + cab_id, cab_id, json);
    }

    protected void updateRoute(int cab_id, Route r) {
        String json = "{\"status\":\"" + r.status +"\"}";
        log("route", r.id, json);
        saveJSON("PUT", "routes", "cab" + cab_id, r.id, json);
    }

    protected void updateLeg(int cab_id, Task t) {
        String json = "{\"status\":\"" + t.status +"\"}";
        log("leg", t.id, json);
        saveJSON("PUT", "legs", "cab" + cab_id, t.id, json);
    }

    protected Route getRoute(int cab_id) {
        String json = getEntityAsJson("cab"+cab_id, "routes");
        return getRouteFromJson(json);
    }

    private Route getRouteFromJson(String str) {
        //"{"id":114472,"status":"ASSIGNED",
        //  "legs":[{"id":114473,"fromStand":16,"toStand":12,"place":0,"status":"ASSIGNED"}]}"
        Map map = getMap(str, this.engine);
        if (map == null) {
            return null;
        }
        int id = (int) map.get("id");
        List<Map> legs = (List<Map>) map.get("legs");
        List<Task> tasks = new ArrayList<>();
        for (Map m : legs) {
            tasks.add(new Task( (int) m.get("id"),
                                (int) m.get("fromStand"),
                                (int) m.get("toStand"),
                                (int) m.get("place")));
        }
        return new Route(id, tasks);
    }

    private List<Stop> getStopsFromJson(String str) {
        //{"id":1, "no":"20001", "name":" ", "type":"No Shelter or Seating Available",
        //  "bearing":null,"latitude":36.2182242820001,"longitude":-115.247021856}
        List<Map> lista = getListOfMap(str, this.engine);
        if (lista == null) {
            return null;
        }
        List<Stop> stops = new ArrayList<>();
        for (Map m : lista) {
            stops.add(new Stop( (int) m.get("id"),
                                (String) m.get("no"),
                                (String) m.get("name"),
                                (String) m.get("type"),
                                (String) m.get("place"),
                                (double) m.get("latitude"),
                                (double) m.get("longitude")));
        }
        return stops;
    }

    private Cab getCabFromJson(String json) {
        Map map = getMap(json, this.engine);
        //"{"id":0,"location":1,"status":"FREE"}"
        if (map == null) {
            return null;
        }
        return new Cab( (int) map.get("id"),
                        (int) map.get("location"),
                        getCabStatus((String) map.get("status")),
                        (String) map.get("name"));
    }

    protected Demand getOrderFromJson(String str) {
        if (str == null || str.startsWith("OK")) {
            return null;
        }
        Map map = getMapFromJson(str, this.engine);
        if (map == null) {
            logger.info("getMapFromJson returned NULL, json:" + str);
            return null;
        }
        try {
            return new Demand(  (int) map.get("id"),
                                (int) map.get("fromStand"),
                                (int) map.get("toStand"),
                                (int) map.get("maxWait"),
                                (int) map.get("maxLoss"),
                                getOrderStatus((String) map.get("status")),
                                (boolean) map.get("inPool"),
                                (int) map.get("cab_id"),
                                (int) map.get("eta"), 
                                (int) map.get("distance") 
                            );
        } catch (NullPointerException npe) {
            logger.info("NPE in getMapFromJson, json:" + str);
            return null;
        }
    }

    private static void setAuthentication(HttpURLConnection con, String user, String passwd) {
        String auth = user + ":" + passwd;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        String authHeaderValue = "Basic " + encodedAuth;
        con.setRequestProperty("Authorization", authHeaderValue);
    }

    protected static String saveJSON(String method, String entity, String user, int rec_id, String json) {
        String password = user;
        HttpURLConnection con = null;
        StringBuilder response = new StringBuilder();
        String urlStr = "http://192.168.10.176:8080/" + entity + "/";
        if ("PUT".equals(method)) {
            urlStr += rec_id;
        }
        try {
            URL url = new URL(urlStr);
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(method);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);
            // Basic auth
            setAuthentication(con, user, password);

            try (OutputStream os = con.getOutputStream()) {
                byte[] input = json.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            response = getResponse(con);
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage() + "; "+ e.getCause() + "; " + e.getStackTrace().toString());
        }
        finally {
            con.disconnect();
        }
        // we don't need any feedback // getCabFromJson(response.toString());
        return response.toString();
    }

    private static String getEntityAsJson(String user, String urlStr) {
        StringBuilder result = new StringBuilder();
        HttpURLConnection con = null;
        try {
            // taxi_order will be updated with eta, cab_id and task_id when assigned
            URL url = new URL("http://192.168.10.176:8080/" + urlStr); // assumption that one customer has one order
            con = (HttpURLConnection) url.openConnection();
            setAuthentication(con, user, user);
            result = getResponse(con);
            if (!"routes".equals(urlStr) && (result == null || result.toString().length() == 0)) {
               logger.info("http conn to " + urlStr+ " returned null or empty string");
            }
        } catch(Exception e) { e.printStackTrace(); }
        finally { con.disconnect(); }
        return result.toString();
    }

    private static StringBuilder getResponse(HttpURLConnection con) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }
        return response;
    }

    private static Map getMapFromJson(String str, ScriptEngine engine) {
        //"{"id":113579,"status":"ASSIGNED","fromStand":10,"toStand":6,"maxWait":10,"maxLoss":10,"shared":true,"eta":-1,"inPool":false,
        //"customer":{"id":1,"hibernateLazyInitializer":{}},
        //"leg":{"id":114461,"fromStand":10,"toStand":8,"place":1,"status":"ASSIGNED",
        //       "route":null, "hibernateLazyInitializer":{}},
        //"route":{"id":114459,"status":"ASSIGNED",
        //         "cab":{"id":907,"location":12,"status":"ASSIGNED","hibernateLazyInitializer":{}},
        //         "legs":null,"hibernateLazyInitializer":{}}}"
        if ("OK".equals(str)) { // PUT
            return null;
        }
        Map map = getMap(str, engine);
        if (map == null) {
            return null;
        }
        Map route = (Map) map.get("route");
        Map cab = null;
        int cab_id = -1;
        if (route != null) {
            cab = (Map) route.get("cab");
            if (cab != null) {
                cab_id = (int) cab.get("id");
            }
        }
        map.put("cab_id", cab_id);
        return map;
    }

    private static Map getMap(String json, ScriptEngine engine) {
        try {
            String script = "Java.asJSONCompatible(" + json + ")";
            Object result = engine.eval(script);
            return (Map) result;
        } catch (ScriptException se) {
            return null;
        }
    }

    
    private static List<Map> getListOfMap(String json, ScriptEngine engine) {
        try {
            String script = "Java.asJSONCompatible(" + json + ")";
            Object result = engine.eval(script);
            return (List<Map>) result;
        } catch (ScriptException se) {
            return null;
        }
    }

    public static void waitSecs(int secs) {
        try { Thread.sleep(secs*1000); } catch (InterruptedException e) {} // one minute
    }

    public static void waitMins(int mins) {
        try { Thread.sleep(mins*60*1000); } catch (InterruptedException e) {} // one minute
    }

    protected void log(String msg, int cabId, int routeId) {
        logger.info(msg + ", cab_id=" + cabId + ", route_id=" + routeId + ",");
    }

    protected void log(String msg, int from, int to, int cabId, int taskId) {
        logger.info(msg + ", from=" + from + ", to=" + to + ", cab_id=" + cabId + ", leg_id=" + taskId + ",");
    }

    protected void log(String entity, int id, String json) {
        logger.info("Saving " + entity +"=" + id + ", JSON=" + json);
    }

    protected static void logCust(String msg, int custId, int orderId){
        logger.info(msg + ", cust_id=" + custId+ ", order_id=" + orderId +",");
    }

    protected static void log(String msg, int custId, int orderId, int cabId){
        logger.info(msg + ", cust_id=" + custId+ ", order_id=" + orderId +", cab_id=" + cabId + ",");
    }

    public static CabStatus getCabStatus (String stat) {
        switch (stat) {
            case "ASSIGNED": return CabStatus.ASSIGNED;
            case "FREE":     return CabStatus.FREE;
            case "CHARGING": return CabStatus.CHARGING;
            default: return null;
        }
    }

    public static OrderStatus getOrderStatus (String stat) {
        if (stat == null) {
            return null;
        }
        switch (stat) {
            case "ASSIGNED":  return OrderStatus.ASSIGNED;
            case "ABANDONED": return OrderStatus.ABANDONED;
            case "ACCEPTED":  return OrderStatus.ACCEPTED;
            case "CANCELLED": return OrderStatus.CANCELLED;
            case "COMPLETED":  return OrderStatus.COMPLETED;
            case "PICKEDUP":  return OrderStatus.PICKEDUP;
            case "RECEIVED":  return OrderStatus.RECEIVED;
            case "REFUSED":   return OrderStatus.REFUSED;
            case "REJECTED":  return OrderStatus.REJECTED;
            default: return null;
        }
    }

    public enum RouteStatus {
        PLANNED,   // proposed by Pool
        ASSIGNED,  // not confirmed, initial status
        ACCEPTED,  // plan accepted by customer, waiting for the cab
        REJECTED,  // proposal rejected by customer(s)
        ABANDONED, // cancelled after assignment but before 'PICKEDUP'
        STARTED,
        COMPLETED
    }

    public enum OrderStatus {
        RECEIVED,  // sent by customer
        ASSIGNED,  // assigned to a cab, a proposal sent to customer with time-of-arrival
        ACCEPTED,  // plan accepted by customer, waiting for the cab
        CANCELLED, // cancelled before assignment
        REJECTED,  // proposal rejected by customer
        ABANDONED, // cancelled after assignment but before 'PICKEDUP'
        REFUSED,   // no cab available, cab broke down at any stage
        PICKEDUP,
        COMPLETED
    }

    public enum CabStatus {
        ASSIGNED,
        FREE,
        CHARGING, // out of order, ...
    }

    public static Logger configureLogger(Logger loggr, String file) {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");
        FileHandler fh;
        try {
            fh = new FileHandler(file);
            loggr.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }
        return loggr;
    }

    public static int getFromYaml(String path, String key) {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"))) {
            String curLine;
            while ((curLine = bufferedReader.readLine()) != null){
                if (curLine.contains(key)) {
                    int idx = curLine.indexOf(':');
                    if (idx != -1) {
                        return Integer.parseInt(curLine.substring(idx+1).trim());
                    }
                    return -1;
                }
            }
        }
        catch (IOException ioe) { }
        return -1;
    }

    protected int getDistance(List<Stop> stops, int from, int to) {
        Stop f = stops.stream().filter(s -> from == s.id).findAny().orElse(null);
        Stop t = stops.stream().filter(s -> to == s.id).findAny().orElse(null);
        if (f == null || t == null) return -1; // ERR
        return (int) dist(f.latitude, f.longitude, t.latitude, t.longitude, 'K');
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
