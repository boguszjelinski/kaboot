import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Logger;
import static java.lang.StrictMath.abs;

class CustomerRunnable implements Runnable {

    final static Logger logger = Logger.getLogger("kaboot.simulator.cabgenerator");

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
    final static int MAX_WAIT_FOR_RESPONSE = 3; // minutes; might be random in taxi_demand.txt
    final static int MAX_WAIT_FOR_CAB = 10; // minutes; might be random in taxi_demand.txt
    final static int MAX_POOL_LOSS = 30; // %; might be random in taxi_demand.txt
    final static int MAX_TRIP_LOSS = 3; // minutes; just not be a jerk!
    final static int MAX_TRIP_LEN = 60; // cab driver is a human, can choose a wrong way :)

    private Demand order;

    public CustomerRunnable(int[] order) {
        this.order = new Demand(order[0],order[1],order[2],order[3],order[4]);
        this.order.setStatus(OrderStatus.RECEIVED);
    }
    public void run() {
        System.setProperty("java.util.logging.SimpleFormatter.format","%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");
//        configureLogger();
        live(order);
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
      /*  Demand order = saveOrder("POST", d); //          order = d; but now ith has entity id
        if (order == null) {
            logger.info("Unable to request a cab, cust_id=" + d.id);
            return;
        }
        */
        logger.info("Cab requested, order_id=" + order.id);
        // just give kaboot a while to think about it
        // pool ? cab? ETA ?
        String ret = null;
        for (int t=0; t<MAX_WAIT_FOR_RESPONSE; t++) {
            waitSecs(60);
            ret = getEntityString("orders/", d.id, order.id);
            if (ret == null) {
                logger.info("Serious error, order not found, order_id=" + order.id);
                return;
            }
            if (ret.contains("ASSIGNED" /*&& ord.cab_id != -1*/))  {
                break;
            }
            //else logger.info("NOT ASSIGNED: " + ret);
        }
        TaxiOrder ord = null;
        if (ord == null || ord.status != OrderStatus.ASSIGNED
            //|| ord.cab_id == -1
            ) { // Kaboot has not answered, too busy
            // complain
            logger.info("Waited in vain: cust_id=" + d.id);
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
            waitSecs(15);
            Cab cab = getEntity("cabs/", d.id, order.cab_id);
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
            waitSecs(15);
            /*order = getEntity("orders/", d.id, order.id);
            if (order.status == OrderStatus.COMPLETE && order.cab_id != -1)  {
                break;
            }*/
            Cab cab = getEntity("cabs/", d.id, order.cab_id);
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

    private void saveOrder(String method, Demand d) {
        try {
            String user = "cust" + d.id;
            String password = user;
            URL url = new URL("http://localhost:8080/orders");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(method);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);
            // Basic auth
            String auth = user + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            String authHeaderValue = "Basic " + encodedAuth;
            con.setRequestProperty("Authorization", authHeaderValue);

            String jsonInputString = "{\"fromStand\":" + d.from + ", \"toStand\": " + d.to + ", \"maxWait\":10, \"maxLoss\": 10, \"shared\": true}";
            //System.out.println("JSON: " + jsonInputString);
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            try (BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }
            con.disconnect();
            return;
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage() + "; "+ e.getCause() + "; " + e.getStackTrace().toString());
            return;
        }
    }

/*    private Demand getAssignment(Demand d) {
        String user = "cust" + d.id;
        StringBuilder result = new StringBuilder();
        HttpURLConnection con = null;
        Demand dem = null;
        try {
            // taxi_order will be updated with eta, cab_id and task_id when assigned
            URL url = new URL("http://localhost:8080/orders/" + d.id); // assumption that one customer has one order
            con = (HttpURLConnection) url.openConnection();
            setAuthentication(con, user, user);
            InputStream in = new BufferedInputStream(con.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            Gson g = new Gson();
            if (result != null)
                dem = g.fromJson(result.toString(), Demand.class);
            //Route r = covertFromJsonToObject(result.toString(), Route.class);
        } catch( Exception e) {
            e.printStackTrace();
        }
        finally {
            con.disconnect();
            return dem;
        }
    }
*/
    private <T> T getEntity(String entityUrl, int user_id, int id) {
        String user = "cust" + user_id;
        StringBuilder result = new StringBuilder();
        HttpURLConnection con = null;
        Class<T> dem = null;
        try {
            // taxi_order will be updated with eta, cab_id and task_id when assigned
            URL url = new URL("http://localhost:8080/" + entityUrl + id); // assumption that one customer has one order
            con = (HttpURLConnection) url.openConnection();
            setAuthentication(con, user, user);
            InputStream in = new BufferedInputStream(con.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        } catch( Exception e) {
            e.printStackTrace();
        }
        finally {
            con.disconnect();
            //dem = covertFromJsonToObject(result.toString(), dem.getClass());
            return (T)dem;
        }
    }

    private String getEntityString(String entityUrl, int user_id, int id) {
        String user = "cust" + user_id;
        StringBuilder result = new StringBuilder();
        HttpURLConnection con = null;
        try {
            // taxi_order will be updated with eta, cab_id and task_id when assigned
            URL url = new URL("http://localhost:8080/" + entityUrl + id); // assumption that one customer has one order
            con = (HttpURLConnection) url.openConnection();
            setAuthentication(con, user, user);
            InputStream in = new BufferedInputStream(con.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            //Route r = covertFromJsonToObject(result.toString(), Route.class);
        } catch( Exception e) {
            e.printStackTrace();
        }
        finally {
            con.disconnect();
            return result.toString();
        }
    }

    private  void setAuthentication(HttpURLConnection con, String user, String passwd) {
        String auth = user + ":" + passwd;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        String authHeaderValue = "Basic " + encodedAuth;
        con.setRequestProperty("Authorization", authHeaderValue);
    }
    private class Cab {
        public int id;
        public int location;
    }

    private class TaxiOrder {
        public Long id;
        public OrderStatus status;
        public int fromStand;
        public int toStand;
        public int maxWait; // how long can I wait for a cab
        public int maxLoss; // [%] how long can I lose while in pool
        public boolean shared; // can be in a pool ?
        public Integer eta; // set when assigned
        public Boolean inPool; // was actually in pool
        public Object cab;  // an order can be serviced by ONE cab only, but one cab can service MANY orders throughout the day
        public Object customer;  // an order can be serviced by ONE cab only, but one cab can service MANY orders throughout the day
        public Object task;  // an order can be pick-up by one task, but one task can pick up MANY orders/customers
        public Object route;  // an order can be serviced by ONE cab only, but one cab can service MANY orders throughout the day

        public void setId(Long id) {            this.id = id;        }
        public void setStatus(OrderStatus status) { this.status = status; }
        public void setFromStand(int fromStand) { this.fromStand = fromStand; }
        public void setToStand(int toStand) { this.toStand = toStand; }
        public void setMaxWait(int maxWait) { this.maxWait = maxWait; }
        public void setMaxLoss(int maxLoss) { this.maxLoss = maxLoss; }
        public void setShared(boolean shared) { this.shared = shared; }
        public void setEta(Integer eta) { this.eta = eta; }
        public void setInPool(Boolean inPool) { this.inPool = inPool; }
        public void setCab(Object cab) { this.cab = cab; }
        public void setCustomer(Object customer) { this.customer = customer; }
        public void setTask(Object task) { this.task = task; }
        public void setRoute(Object route) { this.route = route; }
    }

    private class Demand {
        public int id, from, to, time, at;
        public int eta; // set when assigned
        public boolean inPool;
        public int cab_id;
        public OrderStatus status;

        public Demand (int id, int from, int to, int time, int at) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.at = at;
            this.time = time;
        }
        public void setStatus (OrderStatus stat) { this.status = stat; }
    }

    private static void waitSecs(int secs) {
        try { Thread.sleep(secs*1000); } catch (InterruptedException e) {} // one minute
    }


}