/*  Author: Bogusz Jelinski
    Project: Kabina/Kaboot
    Date: 2020
*/
import com.google.gson.Gson;

import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import static java.lang.StrictMath.abs;

public class CustomerGenerator {
    //final static String DEMAND_FILE = "C:\\Users\\dell\\TAXI\\GIT\\simulations\\taxi_demand.txt";
    final static String DEMAND_FILE = "/home/m91127/GITHUB/taxidispatcher/taxi_demand.txt";
    final static int MAX_WAIT_FOR_RESPONSE = 3; // minutes; might be random in taxi_demand.txt
    final static int MAX_WAIT_FOR_CAB = 10; // minutes; might be random in taxi_demand.txt
    final static int MAX_POOL_LOSS = 30; // %; might be random in taxi_demand.txt
    final static int MAX_TRIP_LOSS = 3; // minutes; just not be a jerk!
    final static int MAX_TRIP_LEN = 100; // cab driver is a human, can choose a wrong way :)

    static long demandCount=0;
    static Demand[] demand;
    static int maxTime = 0;

    private static class CustomerRunnable implements Runnable {
        private Demand demand;
        public CustomerRunnable(Demand d) { this.demand = d; }
        public void run() {
            live(demand);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        readDemand();
        Logger log = getLogger();
        log.info("Start");

        for (int t = 0; t < maxTime; t++) { // time axis
            System.out.print("\nTIME: " + t + ", IDs: ");
            // filter out demand for this time point
            for (int i=0; i< demand.length; i++) {
                if (demand[i].at == t) { 
                    final Demand d = demand[i];
                    System.out.print(d.id + ", ");
                    (new Thread(new CustomerRunnable(d))).start();
                }
            }
            TimeUnit.SECONDS.sleep(10);
        }
    }

    private static void live(Demand d) {
        /*
            1. request a cab
            2. loop/wait for an assignment - do you like it ?
            3. wait for a cab
            4. take a trip
            5. mark the end
        */
        // send to dispatcher that we need a cab
        // order id returned
        Demand order = saveOrder("POST", d); // order = d; but now ith has entity id
        // just give kaboot a while to think about it
         // pool ? cab? ETA ?
        for (int t=0; t<MAX_WAIT_FOR_RESPONSE; t++) {
            waitSecs(60);
            order = getEntity("orders/", d.id, order.id);
            if (order.status == Demand.OrderStatus.ASSIGNED && order.cab_id != -1)  {
                break;
            }
        }
        if (order.status != Demand.OrderStatus.ASSIGNED || order.cab_id == -1) { // Kaboot has not answered, too busy
            // complain
            return;
        }
        if (order.eta > d.at + MAX_WAIT_FOR_CAB) {
            // complain
            return;
        }
        order.status = Demand.OrderStatus.ACCEPTED;
        order = saveOrder("PUT", order); // PUT = update

        boolean arrived = false;
        for (int t=0; t<MAX_WAIT_FOR_CAB; t++) {
            waitSecs(60);
            Cab cab = getEntity("cabs/", d.id, order.cab_id);
            if (cab.location == d.from) {
                arrived = true;
                break;
            }
        }
        if (!arrived) {
            // complain
            return;
        }
        // authenticate to the cab - open the door?
        order.status = Demand.OrderStatus.PICKEDUP;
        order = saveOrder("PUT", order); // PUT = update
        // take a trip
        int duration=0;
        for (; duration<MAX_TRIP_LEN; duration++) {
            waitSecs(60);
            order = getEntity("orders/", d.id, order.id);
            if (order.status == Demand.OrderStatus.COMPLETE && order.cab_id != -1)  {
                break;
            }
        }
        // POOL CHECK
        if (order.inPool) {
            if (duration > (int) (abs(d.from - d.to) * MAX_POOL_LOSS)) {
                // complain
            }
        } else { // not a carpool
            if (duration > (int) (abs(d.from - d.to) + MAX_TRIP_LOSS)) {
                // complain
            }
        }
    }

    private static Demand saveOrder(String method, Demand d) {
        try {
            String user = "cust" + d.id;
            String password = user;
            URL url = new URL("http://localhost:8080/orders");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(method);
            con.setRequestProperty("Content-Type", "application/json; utf-8");
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
            Demand ret=null;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                Gson g = new Gson();
                ret = g.fromJson(response.toString(), Demand.class);
            }
            con.disconnect();
            return ret;
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage() + "; "+ e.getCause() + "; " + e.getStackTrace().toString());
            return null;
        }
    }

    private static Demand getAssignment(Demand d) {
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

    private static <T> T getEntity(String entityUrl, int user_id, int id) {
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
            Gson g = new Gson();
            dem = g.fromJson(result.toString(), dem.getClass());
            //Route r = covertFromJsonToObject(result.toString(), Route.class);
        } catch( Exception e) {
            e.printStackTrace();
        }
        finally {
            con.disconnect();
            return (T)dem;
        }
    }

    private static void setAuthentication(HttpURLConnection con, String user, String passwd) {
        String auth = user + ":" + passwd;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        String authHeaderValue = "Basic " + encodedAuth;
        con.setRequestProperty("Authorization", authHeaderValue);
    }

    private static void readDemand() {
		try {
			Path path = Path.of(DEMAND_FILE);
			demandCount = Files.lines(path).count();
			demand = new Demand[(int)demandCount];
			BufferedReader reader = new BufferedReader(new FileReader(DEMAND_FILE));
			String line = reader.readLine();
			int count=0;
		    while (line != null) {
		        if (line == null) break;
		        line = line.substring(1, line.length()-1); // get rid of '('
		        String[] lineVector = line.split(",");
		        demand[count] = new Demand(
		        		Integer.parseInt(lineVector[0]), // ID
				        Integer.parseInt(lineVector[1]), // from
				        Integer.parseInt(lineVector[2]),  // to
				        Integer.parseInt(lineVector[3]), // time we learn about customer
                        Integer.parseInt(lineVector[4])); // time at which he/she wants the cab
                if (maxTime<Integer.parseInt(lineVector[4])) {
                    maxTime = Integer.parseInt(lineVector[4]);
                }
		        count++;
		        line = reader.readLine();
		    }
		    reader.close();
		}
		catch (IOException e) {}
    }

    private class Cab {
        public int id;
        public int location;
    }
    private static class Demand {
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
        public enum OrderStatus {
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
        public void setStatus (OrderStatus stat) { this.status = stat; }
    }

    private static Logger getLogger() {
        Logger logger = Logger.getLogger("my");
        FileHandler fh;
        try {
            fh = new FileHandler("customer.log");
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return logger;
    }

    private static void waitSecs(int secs) {
        try { Thread.sleep(secs*1000); } catch (InterruptedException e) {} // one minute
    }
}
