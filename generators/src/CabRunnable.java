/*  Author: Bogusz Jelinski
    Project: Kabina/Kaboot
    Date: 2020
*/

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class CabRunnable implements Runnable {
    
    static final Logger logger = Logger.getLogger("kaboot.simulator.cabgenerator");

    static final int MAX_TIME = 120; // minutes 120
    static final int MAX_STAND = 50; // TODO: read from application.yml
    
    private ScriptEngine engine;

    private int cabId;
    public CabRunnable(int id) { 
        this.cabId = id; 
        ScriptEngineManager sem = new ScriptEngineManager();
        this.engine = sem.getEngineByName("javascript");
    }
    
    public void run() {
        live(cabId);
    }

    private void live(int cab_id) {
        logger.info("I am alive, cab=" + cab_id);
        /*
            1. check if any valid route
            2. if not - wait 30sec
            3. if yes - get the route with tasks (legs), mark cab as 'not available' (sheduler's job)
            4. execute route - go from stand to stand, "go" = wait this ammount of time, virtual trip
            5. report stoping at stands (Kaboot must notify customers)
            6. report 'cab is free'
        */
        // let's begin with cab's location
        Cab cab = getCab("cabs/", cab_id, cab_id);
        if (cab == null) { 
            // cab should be activated by an admin
            logger.warning("Cab=" +  cab_id + " not activated");
            return;
        }
        logger.info("Updating cab=" + cab_id + ", free at: " + (cab_id % MAX_STAND));
        String json = "{\"location\":\"" + (cab_id % MAX_STAND) + "\", \"status\": \""+ Utils.CabStatus.FREE +"\"}";
        Utils.saveJSON("PUT", "cabs", "cab" + cab_id, cab_id, json);

        for (int t = 0; t < MAX_TIME; t++) {
            Route route = getRoute(cab_id); // TODO: status NULL
            if (route != null) { // this cab has been assigned a job
                log("New route to run", cab_id,  route.getId());
                // go to first task - virtual walk, just wait this amount of time
                List<Task> legs = route.getTasks();
                if (legs == null || legs.size() == 0) {
                    log("Error - route has no legs", cab_id, route.getId());
                    continue;
                }
                // sorting legs by place 
                // TODO: index i DB
                Collections.sort(legs, (Task t1, Task t2) -> t1.place - t2.place);
                cab.status = Utils.CabStatus.ASSIGNED;
                Task task = legs.get(0);
                // maybe these lines below are not needed - the first leg will be cab's move to pickup the first customer
                // check logs afterwards to confirm this
                if (task.fromStand != cab.location) { 
                    // strange - scheduler did not know cab's location
                    log("Error, first leg does not start at cabs location. Moving", task.fromStand, cab.location, cab_id, + task.id);
                    Utils.waitMins(Math.abs(cab.location - task.fromStand)); // cab is moving
                    cab.location = task.fromStand;
                    // inform that cab is at the stand -> update Cab entity, 'complete' previous Task
                    updateCab(cab.id, cab);
                }
                Utils.waitMins(1); // wait 1min so that passengers can get in
                deliverPassengers(legs, cab);
                route.status = Utils.RouteStatus.COMPLETE;
                updateRoute(cab.id, route);
            } 
            Utils.waitSecs(30);
        }
    }
  
    private void deliverPassengers(List<Task> legs, Cab cab) {
        for (int i=0; i < legs.size(); i++) {
            // go from where you are to task.stand
            Task task = legs.get(i);
            log("Moving", task.fromStand, task.toStand, this.cabId, task.id);

            Utils.waitMins(Math.abs(task.fromStand - task.toStand)); // cab is moving
            task.status = Utils.RouteStatus.COMPLETE;
            updateTask(cab.id, task);
            cab.location = task.toStand;
            // inform sheduler / customer
            if (i == legs.size() - 1) {
                cab.status = Utils.CabStatus.FREE;
            }
            updateCab(cab.id, cab); // such call should 'complete' tasks; at the last task -> 'complete' route and 'free' that cab
            // !! update leg here -> completed
            Utils.waitMins(1); // wait 1min: pickup + dropout
        }
    }

    private void log (String msg, int cabId, int routeId) {
        logger.info(msg + ", cab_id=" + cabId + ", route_id=" + routeId + ",");
    }

    private void log (String msg, int from, int to, int cabId, int taskId) {
        logger.info(msg + ", from=" + from + ", to=" + to + ", cab_id=" + cabId + ", leg_id=" + taskId + ",");
    }

    private void log(String entity, int id, String json) {
        logger.info("Saving " + entity +"=" + id + ", JSON=" + json);
    }

    private void updateCab(int cab_id, Cab cab) {
        String json = "{\"location\":\"" + cab.location + "\", \"status\": \""+ cab.status +"\"}";
        log("cab", cab_id, json);
        Utils.saveJSON("PUT", "cabs", "cab" + cab_id, cab_id, json);
    }

    private void updateRoute(int cab_id, Route r) {
        String json = "{\"status\":\"" + r.status +"\"}";
        log("route", r.id, json);
        Utils.saveJSON("PUT", "routes", "cab" + cab_id, r.id, json);
    }

    private void updateTask(int cab_id, Task t) {
        String json = "{\"status\":\"" + t.status +"\"}";
        log("leg", t.id, json);
        Utils.saveJSON("PUT", "legs", "cab" + cab_id, t.id, json);
    }
 
    private Route getRoute(int cab_id) {
        String json = Utils.getEntityAsJson("cab"+cab_id, "routes");
        return getRouteFromJson(json);
    }
    
    private Cab getCab(String entityUrl, int user_id, int id) {
        String json = Utils.getEntityAsJson("cab"+user_id, entityUrl + id);
        return getCabFromJson(json);
    }

    private Cab getCabFromJson(String str) {
        Map map = Utils.getMap(str, this.engine);
        //"{"id":0,"location":1,"status":"FREE"}"
        if (map == null) {
            return null;
        }
        return new Cab( (int) map.get("id"),
                        (int) map.get("location"),
                        Utils.getCabStatus((String) map.get("status")));
    }

    private Route getRouteFromJson(String str) {
        //"{"id":114472,"status":"ASSIGNED",
        //  "legs":[{"id":114473,"fromStand":16,"toStand":12,"place":0,"status":"ASSIGNED"}]}" 
        Map map = Utils.getMap(str, this.engine);
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
}
