/*  Author: Bogusz Jelinski
    Project: Kabina/Kaboot
    Date: 2020
*/
// javac -cp ../lib/gson-2.8.6.jar CabGenerator.java
// java -cp ../lib/gson-2.8.6.jar;. CabGenerator

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Base64;
//import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

public class CabGenerator {
    final static int maxTime = 120; // minutes
    final static int maxCabs = 1000;
    long start = System.currentTimeMillis();
    long end = System.currentTimeMillis();
     //(end - start) / 1000d // " seconds"

    private static class CabRunnable implements Runnable {
        private int cab_id;
        public CabRunnable(int id) { this.cab_id = id; }
        public void run() {
            live(cab_id);
        }
    }

    public static void main(String[] args) throws InterruptedException {

        // generating cabs
        for (int c = 0; c < maxCabs; c++) {
            final int id = c;
            (new Thread(new CabRunnable(id))).start();
            break;
        }
    }

    private static void live(int cab_id) {
        System.out.println("live");
        /*
            1. check if any valid route
            2. if not - wait 30sec
            3. if yes - get the route with tasks, mark cab as 'not available'
            4. execute route - go from task to task
            5. report stoping at stands (Kaboot must notify customers)
            6. report 'cab is free'
        */
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {}

        System.out.println("Blah");
        Route[] r = getRoute(cab_id);
        System.out.println("First task stand: " + r[0].getTasks().get(0).getStand());
    }

   /* private static void requestCab(Demand d) {
        try {
            String user = "cust" + d.id;
            URL url = new URL("http://localhost:8080/orders");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);
            // Basic auth
            setAuthentication(con, user, user);

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
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage() + "; "+ e.getCause() + "; " + e.getStackTrace().toString());
        }
    }*/


    private static Route[] getRoute(int cab_id) {
        String user = "cab" + cab_id;
        StringBuilder result = new StringBuilder();
        HttpURLConnection con = null;
        try {
            URL url = new URL("http://localhost:8080/routes");
            con = (HttpURLConnection) url.openConnection();
            setAuthentication(con, user, user);
            InputStream in = new BufferedInputStream(con.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            
            Gson g = new Gson();
            Route[] r = g.fromJson(result.toString(), Route[].class);
            //Route r = covertFromJsonToObject(result.toString(), Route.class);
            return r;
        } catch( Exception e) {
            e.printStackTrace();
        }
        finally {
            con.disconnect();
        }
        return null;
    }

    private static void setAuthentication(HttpURLConnection con, String user, String passwd) {
        String auth = user + ":" + passwd;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        String authHeaderValue = "Basic " + encodedAuth;
        con.setRequestProperty("Authorization", authHeaderValue);
    }

    private class Route {
        List<Task> tasks;
        public Route(List<Task> tasks) { this.tasks = tasks;}
        public List<Task> getTasks() { return tasks; }
        public void setTasks(List<Task> tasks) { this.tasks = tasks; }

    }

    private class Task {
        int stand, place;
        public Task(int stand, int place) {
            this.stand = stand;
            this.place = place;
        }
        public int getStand() { return stand; }
        public void setStand(int stand) { this.stand = stand; }
        public int getPlace() { return place; }
        public void setPlace(int order) { this.place = place; }
    }

    // static public <T> T covertFromJsonToObject(String json, Class<T> var) throws IOException{
    //     ObjectMapper mapper = new ObjectMapper();
    //     return mapper.readValue(json, var);//Convert Json into object of Specific Type
    // }
}
