import java.util.logging.Logger;
import static java.lang.StrictMath.abs;

import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

class CustomerRunnable implements Runnable {

    final static Logger logger = Logger.getLogger("kaboot.simulator.cabgenerator");
   
    final static int MAX_WAIT_FOR_RESPONSE = 3; // minutes; might be random in taxi_demand.txt
    final static int MAX_WAIT_FOR_CAB = 10; // minutes; might be random in taxi_demand.txt
    final static int MAX_POOL_LOSS = 30; // %; might be random in taxi_demand.txt
    final static int MAX_TRIP_LOSS = 3; // minutes; just not be a jerk!
    final static int MAX_TRIP_LEN = 60; // cab driver is a human, can choose a wrong way :)

    private Demand tOrder;
    private ScriptEngine engine;

    public CustomerRunnable(int[] order) {
        this.tOrder = new Demand(order[0],order[1],order[2],order[3],order[4]);
        this.tOrder.setStatus(OrderStatus.RECEIVED);
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
            2. loop/wait for an assignment - do you like it ?
            3. wait for a cab
            4. take a trip
            5. mark the end
        */
        // send to dispatcher that we need a cab
        // order id returned

        logger.info("Is alive cust_id=" + d.id + ", from=" + d.from + ", to=" +d.to);
        Demand order = saveOrder("POST", d); //          order = d; but now ith has entity id
        if (order == null) {
            logger.info("Unable to request a cab, cust_id=" + d.id);
            return;
        }
      
        //Demand order = new Demand(113579, 10, 6, 0, 10);
        logger.info("Cab requested, order_id=" + order.id);
        // just give kaboot a while to think about it
        // pool ? cab? ETA ?
        
        for (int t=0; t<MAX_WAIT_FOR_RESPONSE; t++) {
            Utils.waitSecs(60); 
            order = getOrder(d.id, order.id);
            if (order == null) {
                logger.info("Serious error, order not found, d.id=" + d.id);
                return;
            }
            if (order.status == OrderStatus.ASSIGNED)  {
                break;
            }
            //else logger.info("NOT ASSIGNED: " + ret);
        }
        
        if (order == null || order.status != OrderStatus.ASSIGNED
            //|| ord.cab_id == -1
            ) { // Kaboot has not answered, too busy
            // complain
            logger.info("Waited in vain: cust_id=" + d.id);
            order.status = OrderStatus.CANCELLED; // just not to kill scheduler
            saveOrder("PUT", order); 
            return;
        }
        logger.info("Assigned: cust_id=" + d.id);
        /*if (order.eta > d.at + MAX_WAIT_FOR_CAB) {
            // complain
            logger.info("I can't wait that long: cust_id=" + d.id);
            return;
        }
        */
        order.status = OrderStatus.ACCEPTED;
        saveOrder("PUT", order); // PUT = update

        boolean arrived = false;
        for (int t=0; t<MAX_WAIT_FOR_CAB * 4 ; t++) { // *4 as 15 secs below
            Utils.waitSecs(15);
            Cab cab = getCab("cabs/", d.id, order.cab_id);
            if (cab.location == d.from) {
                arrived = true;
                break;
            }
        }
        if (!arrived) {
            // complain
            logger.info("Cab has not arrived: cust_id=" + d.id);
            order.status = OrderStatus.CANCELLED; // just not to kill scheduler
            saveOrder("PUT", order); 
            return;
        }
        // authenticate to the cab - open the door?
        order.status = OrderStatus.PICKEDUP;
        saveOrder("PUT", order); // PUT = update
        // take a trip
        int duration=0;
        for (; duration<MAX_TRIP_LEN *4; duration++) {
            Utils.waitSecs(15);
            /*order = getEntity("orders/", d.id, order.id);
            if (order.status == OrderStatus.COMPLETE && order.cab_id != -1)  {
                break;
            }*/
            Cab cab = getCab("cabs/", d.id, order.cab_id);
            if (cab.location == d.to) {
                logger.info("Arrived at " + d.to + ", cust_id=" +  d.id);
                order.status = OrderStatus.COMPLETE;
                saveOrder("PUT", order); 
                break;
            }
        }
        // POOL CHECK
        if (order.inPool) {
            if (duration > (int) (abs(d.from - d.to) * MAX_POOL_LOSS)) {
                // complain
                logger.info("Duration in pool was too long: cust_id=" + d.id);
            }
        } else { // not a carpool
            if (duration > (int) (abs(d.from - d.to) + MAX_TRIP_LOSS)) {
                // complain
                logger.info("Duration took too long: cust_id=" + d.id);
            }
        }
        if (order.status == OrderStatus.ASSIGNED) {
            order.status = OrderStatus.CANCELLED; // just not to kill scheduler
            saveOrder("PUT", order); 
        }
    }

    private Demand saveOrder(String method, Demand d) {
        String json = "{\"fromStand\":" + d.from + ", \"toStand\": " + d.to + ", \"status\":\"" + d.status
                            + "\", \"maxWait\":10, \"maxLoss\": 10, \"shared\": true}";
        json = Utils.saveJSON(method, "orders", "cust" + d.id, d.id, json); // TODO: FAIL, this is not customer id
        return getOrderFromJson(json);
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

    private Demand getOrder(int user_id, int order_id) {
        String json = Utils.getEntityAsJson("cust"+user_id, "orders/" + order_id);
        return getOrderFromJson(json);
    }

    private Cab getCab(String entityUrl, int user_id, int id) {
        String json = Utils.getEntityAsJson("cust"+user_id, entityUrl + id);
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
    
    public Utils.CabStatus getCabStatus (String stat) {
        switch (stat) {
            case "ASSIGNED": return Utils.CabStatus.ASSIGNED;
            case "FREE":     return Utils.CabStatus.FREE;
            case "CHARGING": return Utils.CabStatus.CHARGING;
        }
        return null;
    }

    private Demand getOrderFromJson(String str) {
        //"{"id":113579,"status":"ASSIGNED","fromStand":10,"toStand":6,"maxWait":10,"maxLoss":10,"shared":true,"eta":-1,"inPool":false,
        //"customer":{"id":1,"hibernateLazyInitializer":{}},
        //"leg":{"id":114461,"fromStand":10,"toStand":8,"place":1,"status":"ASSIGNED",
        //       "route":null, "hibernateLazyInitializer":{}},
        //"route":{"id":114459,"status":"ASSIGNED",
        //         "cab":{"id":907,"location":12,"status":"ASSIGNED","hibernateLazyInitializer":{}},
        //         "legs":null,"hibernateLazyInitializer":{}}}"
        Map map = getMap(str);
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
        Demand o = new Demand(  (int) map.get("id"),
                                (int) map.get("fromStand"),
                                (int) map.get("toStand"),
                                getOrderStatus((String) map.get("status")),
                                (boolean) map.get("inPool"),
                                cab_id);
        return o;
    }

    private OrderStatus getOrderStatus (String stat) {
        if (stat == null) {
            return null;
        }

        switch (stat) {
            case "ASSIGNED":  return OrderStatus.ASSIGNED;
            case "ABANDONED": return OrderStatus.ABANDONED;
            case "ACCEPTED":  return OrderStatus.ACCEPTED;
            case "CANCELLED": return OrderStatus.CANCELLED;
            case "COMPLETE":  return OrderStatus.COMPLETE;
            case "PICKEDUP":  return OrderStatus.PICKEDUP;
            case "RECEIVED":  return OrderStatus.RECEIVED;
            case "REFUSED":   return OrderStatus.REFUSED;
            case "REJECTED":  return OrderStatus.REJECTED;
        }
        return null;
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

    private class Demand {
        public int id, from, to, time, at;
        public int eta; // set when assigned
        public boolean inPool;
        public int cab_id;
        public OrderStatus status;

        public Demand (int id, int from, int to, OrderStatus status, boolean inPool, int cab_id) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.status = status;
            this.inPool = inPool;
            this.cab_id = cab_id;
        }

        public Demand (int id, int from, int to, int time, int at) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.at = at;
            this.time = time;
        }
        public void setStatus (OrderStatus stat) { this.status = stat; }
        public void setId(int id) { this.id = id; }
        public void setFrom(int fromStand) { this.from = fromStand; }
        public void setTo(int toStand) { this.to = toStand; }
        public void setEta(Integer eta) { this.eta = eta; }
        public void setInPool(Boolean inPool) { this.inPool = inPool; }
    }

    static enum OrderStatus {
        RECEIVED,  // sent by customer
        ASSIGNED,  // assigned to a cab, a proposal sent to customer with time-of-arrival
        ACCEPTED,  // plan accepted by customer, waiting for the cab
        CANCELLED, // cancelled before assignment
        REJECTED,  // proposal rejected by customer
        ABANDONED, // cancelled after assignment but before 'PICKEDUP'
        REFUSED,   // no cab available, cab broke down at any stage
        PICKEDUP,
        COMPLETE
    }
}
