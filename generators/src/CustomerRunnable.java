/*  Author: Bogusz Jelinski
    Project: Kabina/Kaboot
    Date: 2020
*/

import java.util.logging.Logger;
import static java.lang.StrictMath.abs;

import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

class CustomerRunnable implements Runnable {

    static final Logger logger = Logger.getLogger("kaboot.simulator.customergenerator");
   
    static final int MAX_WAIT_FOR_RESPONSE = 3; // minutes; might be random in taxi_demand.txt
    static final int MAX_WAIT_FOR_CAB = 10; // minutes; might be random in taxi_demand.txt
    static final double MAX_POOL_LOSS = 1.30; // %; might be random in taxi_demand.txt
    static final int MAX_TRIP_LOSS = 4; // minutes; just not be a jerk!
    static final int MAX_TRIP_LEN = 30; // check gen_demand.py, +/- 4min, 
    static final int CHECK_INTERVAL = 15; // secs

    private Demand tOrder;
    private ScriptEngine engine;

    public CustomerRunnable(int[] order) {
        this.tOrder = new Demand(order[0], order[1], order[2], order[3], order[4]);
        this.tOrder.setStatus(Utils.OrderStatus.RECEIVED);
        ScriptEngineManager sem = new ScriptEngineManager();
        this.engine = sem.getEngineByName("javascript");
    }
    public void run() {
        System.setProperty("java.util.logging.SimpleFormatter.format","%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");
        live(tOrder);
    }

    private void live(Demand d) {
        /*
            1. request a cab
            2. wait for an assignment - do you like it ?
            3. wait for a cab
            4. take a trip
            5. mark the end
        */
        // send to dispatcher that we need a cab
        // order id returned
        int custId = d.id; 
        logger.info("Is alive cust_id=" + custId + ", from=" + d.from + ", to=" +d.to);
        Demand order = saveOrder("POST", d, custId); //          order = d; but now ith has entity id
        if (order == null) {
            logger.info("Unable to request a cab, cust_id=" + custId);
            return;
        }
        int orderId = order.id;

        log("Cab requested", custId, orderId);
        // just give kaboot a while to think about it
        // pool ? cab? ETA ?
        Utils.waitSecs(90); // just give the solver some time

        order = waitForAssignment(custId, orderId);
        
        if (order == null || order.status != Utils.OrderStatus.ASSIGNED
            //|| ord.cab_id == -1
            ) { // Kaboot has not answered, too busy
            // complain
            if (order == null) {
                log("Waited in vain, no answer", custId, orderId);
            } else {
                log("Waited in vain, no assignment", custId, orderId);
                order.status = Utils.OrderStatus.CANCELLED; // just not to kill scheduler
                saveOrder("PUT", order, custId); 
            }
            return;
        }

        log("Assigned", custId, orderId, order.cab_id);
        
        // TASK: check order.eta > d.at + MAX_WAIT_FOR_CAB) 
        
        order.status = Utils.OrderStatus.ACCEPTED;
        saveOrder("PUT", order, custId); // PUT = update
        log("Accepted, waiting for that cab", custId, orderId, order.cab_id);
        
        if (!hasArrived(custId, order.cab_id, d.from)) {
            // complain
            log("Cab has not arrived", custId, orderId, order.cab_id);
            order.status = Utils.OrderStatus.CANCELLED; // just not to kill scheduler
            saveOrder("PUT", order, custId); 
            return;
        }
       
        takeATrip(custId, order); 
      
        if (order.status != Utils.OrderStatus.COMPLETE) {
            order.status = Utils.OrderStatus.CANCELLED; // just not to kill scheduler
            log("Status is not COMPLETE, cancelling the trip", custId, orderId, order.cab_id);
            saveOrder("PUT", order, custId); 
        }
    }

    private Demand waitForAssignment(int custId, int orderId) {
        Demand order = null;
        for (int t = 0; t < MAX_WAIT_FOR_RESPONSE * 2; t++) {
            Utils.waitSecs(30); 
            order = getOrder(custId, orderId);
            if (order == null) {
                log("Serious error, order not found", custId, orderId);
                return null;
            }
            if (order.status == Utils.OrderStatus.ASSIGNED)  {
                break;
            }
        }
        return order;
    }

    private boolean hasArrived(int custId, int cabId, int from) {
        for (int t=0; t<MAX_WAIT_FOR_CAB * 4 ; t++) { // *4 as 15 secs below
            Utils.waitSecs(15);
            Cab cab = getCab("cabs/", custId, cabId);
            if (cab.location == from) {
                return true;
            }
        }
        return false;
    }

    private void takeATrip(int custId, Demand order) {
        // authenticate to the cab - open the door?
        log("Picked up", custId, order.id, order.cab_id);
        order.status = Utils.OrderStatus.PICKEDUP;
        saveOrder("PUT", order, custId); // PUT = update

        int duration = 0; 
       
        for (; duration<MAX_TRIP_LEN * (60/CHECK_INTERVAL); duration++) {
            Utils.waitSecs(CHECK_INTERVAL);
            /*order = getEntity("orders/", cust_id, order_id);
            if (order.status == OrderStatus.COMPLETE && order.cab_id != -1)  {
                break;
            }*/
            Cab cab = getCab("cabs/", custId, order.cab_id);
            if (cab.location == order.to) {
                log("Arrived at " + order.to, custId, order.id, order.cab_id);
                order.status = Utils.OrderStatus.COMPLETE;
                saveOrder("PUT", order, custId); 
                break;
            }
        }
          
        if (duration >= MAX_TRIP_LEN * (60/CHECK_INTERVAL)) {
            log("Something wrong - customer has never reached the destination", custId, order.id, order.cab_id);
        } else {
            if (order.inPool) {
                if (duration/(60/CHECK_INTERVAL) > (int) (abs(order.from - order.to) * MAX_POOL_LOSS + MAX_TRIP_LOSS)) {
                    // complain
                    log("Duration in pool was too long", custId, order.id, order.cab_id);
                }
            } else { // not a carpool
                if (duration/(60/CHECK_INTERVAL) > (int) (abs(order.from - order.to) + MAX_TRIP_LOSS)) {
                    // complain
                    log("Duration took too long", custId, order.id, order.cab_id);
                }
            }
        }
    }

    public static void log(String msg, int custId, int orderId){
        logger.info(msg + ", cust_id=" + custId+ ", order_id=" + orderId +",");
    }

    public static void log(String msg, int custId, int orderId, int cabId){
        logger.info(msg + ", cust_id=" + custId+ ", order_id=" + orderId +", cab_id=" + cabId + ",");
    }

    private Demand saveOrder(String method, Demand d, int usr_id) {
        String json = "{\"fromStand\":" + d.from + ", \"toStand\": " + d.to + ", \"status\":\"" + d.status
                            + "\", \"maxWait\":20, \"maxLoss\": 10, \"shared\": true}";
        json = Utils.saveJSON(method, "orders", "cust" + usr_id, d.id, json); // TODO: FAIL, this is not customer id
        return getOrderFromJson(json);
    }

    private Demand getOrder(int userId, int orderId) {
        String json = Utils.getEntityAsJson("cust" + userId, "orders/" + orderId);
        return getOrderFromJson(json);
    }

    private Cab getCab(String entityUrl, int userId, int id) {
        String json = Utils.getEntityAsJson("cust" + userId, entityUrl + id);
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

    private Demand getOrderFromJson(String str) {
        Map map = Utils.getMapFromJson(str, this.engine);
        if (map == null) {
            logger.info("getMapFromJson returned NULL, json:" + str);
            return null;
        }
        try {
            int id = (int) map.get("id");
            int fromStand = (int) map.get("fromStand");
            int toStand = (int) map.get("toStand");
            Utils.OrderStatus status = Utils.getOrderStatus((String) map.get("status"));
            boolean inPool = (boolean) map.get("inPool");
            int cabId = (int) map.get("cab_id");
            return new Demand(id, fromStand, toStand, status, inPool, cabId);
        } catch (NullPointerException npe) {
            logger.info("NPE in getMapFromJson, json:" + str);
            return null;
        }
    }
}
