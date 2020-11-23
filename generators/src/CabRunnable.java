/*  Author: Bogusz Jelinski
    Project: Kabina/Kaboot
    Date: 2020
*/

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class CabRunnable implements Runnable {
    
    final static Logger logger = Logger.getLogger("kaboot.simulator.cabgenerator");

    final static int maxTime = 1; // minutes 120
    final static int maxStand = 50; // TODO: read from application.yml
    
    private ScriptEngine engine;

    private int cab_id;
    public CabRunnable(int id) { 
        this.cab_id = id; 
        ScriptEngineManager sem = new ScriptEngineManager();
        this.engine = sem.getEngineByName("javascript");
    }
    
    public void run() {
        live(cab_id);
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
            logger.warning("Cab not activated, cab=" + cab_id);
            return;
        }
        /* in simulations start with reseting the cabs here:
        logger.info("Creating cab=" + cab_id);
        String json = "{\"location\":\"" + (cab_id % maxStand) + "\", \"status\": \""+ Utils.CabStatus.FREE +"\"}";
        json = "{\"location\":"+ cab_id +", \"status\": \"0\"}";
        logger.info("JSON=" + json);
        cab = Utils.saveJSON("PUT", cab_id, json);
        */
        for (int t=0; t< maxTime; t++) {
            Route route = getRoute(cab_id); // TODO: status NULL
            if (route != null) { // this cab has been assigned a job
                logger.info("New route to run, cab=" + cab_id + ", route=" + route.getId());
                // go to first task - virtual walk, just wait this amount of time
                Task task = route.getTasks().get(0);
                // maybe these 2 lines are not needed - the first leg will be cab's move to pickup the first customer
                // check logs afterwards to confirm this
                if (task.fromStand != cab.location) { 
                    // strange - scheduler did not know cab's location
                    logger.info("Moving from " +task.fromStand+ " to " +cab.location+ ", cab=" + cab_id);
                    Utils.waitMins(Math.abs(cab.location - task.fromStand)); // cab is moving
                    cab.location = task.fromStand;
                    // inform that cab is at the stand -> update Cab entity, 'complete' previous Task
                    updateCab(cab.id, cab);
                }
                Utils.waitMins(1); // wait 1min
                int tasksNumb = route.getTasks().size();
                for (int i=0; i < tasksNumb; i++) {
                    // go from where you are to task.stand
                    Task tsk = route.getTasks().get(i);
                    logger.info("Moving from " +tsk.fromStand+ " to " +tsk.toStand+ ", cab=" + cab_id);
                    Utils.waitMins(Math.abs(tsk.fromStand - tsk.toStand)); // cab is moving
                    tsk.status = RouteStatus.COMPLETE;
                    updateTask(cab.id, tsk);
                    cab.location = task.toStand;
                    // inform sheduler / customer
                    if (i == tasksNumb - 1) {
                        cab.status = Utils.CabStatus.FREE;
                    }
                    updateCab(cab.id, cab); // such call should 'complete' tasks; at the last task -> 'complete' route and 'free' that cab
                    // !! update leg here -> completed
                    Utils.waitMins(1); // wait 1min: pickup + dropout
                }
            } 
            route.status = RouteStatus.COMPLETE;
            updateRoute(cab.id, route);
            Utils.waitSecs(30);
        }
//        System.out.println("First task stand: " + r[0].getTasks().get(0).getStand());
    }
  
    private void updateCab(int cab_id, Cab cab) {
        String json = "{\"location\":\"" + cab.location + "\", \"status\": \""+ cab.status +"\"}";
        logger.info("Saving cab=" + cab_id + ", JSON=" + json);
        Utils.saveJSON("PUT", "cabs", cab_id, cab_id, json);
    }

    private void updateRoute(int cab_id, Route r) {
        String json = "{\"status\":\"" + r.status +"\"}";
        logger.info("Saving route=" + r.id + ", JSON=" + json);
        Utils.saveJSON("PUT", "routes", cab_id, r.id, json);
    }

    private void updateTask(int cab_id, Task t) {
        String json = "{\"status\":\"" + t.status +"\"}";
        logger.info("Saving leg=" + t.id + ", JSON=" + json);
        Utils.saveJSON("PUT", "legs", cab_id, t.id, json);
    }
 
    private Route getRoute(int cab_id) {
        String json = Utils.getEntityAsJson(cab_id, "http://localhost:8080/routes");
        return getRouteFromJson(json);
    }
    
    private Cab getCab(String entityUrl, int user_id, int id) {
        String json = Utils.getEntityAsJson(user_id, "http://localhost:8080/" + entityUrl + id);
        return getCabFromJson(json);
    }

    private Cab getCabFromJson(String str) {
        Map map = getMap(str);
        //"{"id":0,"location":1,"status":"FREE"}"
        if (map == null) {
            return null;
        }
        return new Cab( (int) map.get("id"),
                        (int) map.get("location"),
                        getCabStatus((String) map.get("status")));
    }

    private Route getRouteFromJson(String str) {
        //"{"id":114472,"status":"ASSIGNED",
        //  "legs":[{"id":114473,"fromStand":16,"toStand":12,"place":0,"status":"ASSIGNED"}]}" 
        Map map = getMap(str);
        if (map == null) {
            return null;
        }
        int id = (int) map.get("id");
        List<Map> legs = (List<Map>) map.get("legs");
        List<Task> tasks = new ArrayList<>();
        for (Map m : legs) {
            tasks.add(new Task( (int) m.get("id"), 
                                (int) m.get("fromStand"), 
                                (int) m.get ("toStand"),
                                (int) m.get("place")));
        }
        return new Route(id, tasks); 
    }
    
    public Utils.CabStatus getCabStatus (String stat) {
        switch (stat) {
            case "ASSIGNED": return Utils.CabStatus.ASSIGNED;
            case "FREE": return Utils.CabStatus.FREE;
            case "CHARGING": return Utils.CabStatus.CHARGING;
        }
        return null;
    }
   
    private class Route {
        private int id;
        RouteStatus status;
        List<Task> tasks;
        public Route(int id, List<Task> tasks) { 
            this.id = id;
            this.tasks = tasks;
        }
        public List<Task> getTasks() { return tasks; }
        public void setTasks(List<Task> tasks) { this.tasks = tasks; }
        public void setId(int id) { this.id = id; }
        public int getId() { return id; }
    }

    private class Task {
        //[{"id":114473,"fromStand":16,"toStand":12,"place":0,"status":"ASSIGNED"}]}" 
        public int id, fromStand, toStand, place;
        public RouteStatus status;

        public Task(int id, int fromStand, int toStand, int place) {
            this.id = id;
            this.fromStand = fromStand;
            this.toStand = toStand;
            this.place = place;
        }
        public int getFromStand() { return this.fromStand; }
        public void setFromStand(int stand) { this.fromStand = stand; }
        public int getToStand() { return this.toStand; }
        public void setToStand(int stand) { this.toStand = stand; }
        public int getPlace() { return place; }
        public void setPlace(int order) { this.place = order; }
    }

    private class Cab {
        public Cab(int i, int l, Utils.CabStatus s) {
            this.id = i;
            this.location = l;
            this.status = s;
        }
        public int id;
        public int location;
        public Utils.CabStatus status;
        public void setId(int id) { this.id = id; }
        public void setLocation(int l) { this.location = l; }
        public void setStatus(Utils.CabStatus s) { this.status = s; }
    }
    
    public static enum RouteStatus {
        PLANNED,   // proposed by Pool
        ASSIGNED,  // not confirmed, initial status
        ACCEPTED,  // plan accepted by customer, waiting for the cab
        REJECTED,  // proposal rejected by customer(s)
        ABANDONED, // cancelled after assignment but before 'PICKEDUP'
        COMPLETE
    }
     
    private Map getMap(String json) {
        try {
            String script = "Java.asJSONCompatible(" + json + ")";
            Object result = this.engine.eval(script);
            return (Map) result;
        } catch (ScriptException se) {
            return null;
        }
    }
}
