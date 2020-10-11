/*  Author: Bogusz Jelinski
    Project: Kabina/Kaboot
    Date: 2020
*/
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;

public class CustomerGenerator {
    final static String DEMAND_FILE = "C:\\Users\\dell\\TAXI\\GIT\\simulations\\taxi_demand.txt";
    static long demandCount=0;
    static Demand[] demand;
    static int maxTime = 0;

    private static class Demand {
        public int id, from, to, time, at;
        public Demand (int id, int from, int to, int time, int at) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.at = at;
            this.time = time;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        readDemand();

        ExecutorService executor= Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        boolean end = false;
        for (int t = 0; t < maxTime && !end; t++) { // time axis
            System.out.print("\nTIME: " + t + ", IDs: ");
            // filter out demand for this time point
            for (int i=0; i< demand.length && !end; i++) {
                if (demand[i].at == t) { 
                    final Demand d = demand[i];
                    Runnable thread = new Runnable() {
                        public void run() {
                            live(d);
                        }
                    };
                    executor.execute(thread);
                    //end = true; // just one cab is enough
                }
            }
            TimeUnit.MINUTES.sleep(1);
        }
        executor.shutdown();
    }

    private static void live(Demand d) {
        /*
            1. request a cab
            2. loop/wait for an assignment - do you like it ?
            3. wait for a cab
            4. take a trip
            5. mark the end
        */ 
        requestCab(d);
    }

    private static void requestCab(Demand d) {
        try {
            String user = "cust" + d.id;
            String password = user;
            URL url = new URL("http://localhost:8080/orders");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);
            // Basic auth
            String auth = user + ":" + password;
            //System.out.println("AUTH: " + auth);
            //byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.UTF_8));
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
                System.out.print(d.id + ", ");
            }
            con.disconnect();
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage() + "; "+ e.getCause() + "; " + e.getStackTrace().toString());
        }
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
}
