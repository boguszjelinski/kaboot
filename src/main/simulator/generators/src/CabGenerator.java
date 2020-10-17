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
            3. if yes - get the route with tasks, mark cab as 'not available' (sheduler's job)
            4. execute route - go from task to task
            5. report stoping at stands (Kaboot must notify customers)
            6. report 'cab is free'
        */
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {}

        // let's begin with cab's location
        Cab cab = getEntity("cabs/", cab_id, cab_id);

        for (int t=0; t< maxTime; t++) {
            Route[] r = getRoute(cab_id);
            if (r!= null && r.length > 0) { // this cab has been assigned a task
                // go to first task - virtual walk, just wait this amount of time
                Task task = r[0].getTasks().get(0);
                waitMins(abs(cab.location - task.fromStand)); // cab is moving
                cab.location = task.fromStand;
                cab.status = Cab.CabStatus.ASSIGNED;
                // inform that cab is at the stand -> update Cab entity, 'complete' previous Task
                saveCab("PUT", cab);
                waitMins(1); // wait 1min
                int tasksNumb = r[0].getTasks().length;
                for (int i=0; i < tasksNumb; i++) {
                    // go from where you are to task.stand
                    Task tsk = r[0].getTasks()[i];
                    waitMins(abs(tsk.fromStand - tsk.toStand)); // cab is moving
                    cab.location = task.toStand;
                    // inform sheduler / customer
                    if (i == tasksNumb - 1) {
                        cab.status = Cab.CabStatus.FREE;
                    }
                    saveCab("PUT", cab); // such call should 'complete' tasks; at the last task -> 'complete' route and 'free' that cab
                    waitMins(1); // wait 1min
                }
            } else {
                waitSecs(30);
            }
        }
//        System.out.println("First task stand: " + r[0].getTasks().get(0).getStand());
    }

    private waitSecs(int secs) {
        try { Thread.sleep(secs*1000); } catch (InterruptedException e) {} // one minute
    }

    private waitMins(int mins) {
        try { Thread.sleep(mins*60*1000); } catch (InterruptedException e) {} // one minute
    }

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

    private static Cab saveCab(String method, Cab cab) {
        try {
            String user = "cab" + cab.id;
            String password = user;
            URL url = new URL("http://localhost:8080/cabs");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(method);
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);
            // Basic auth
            setAuthentication(con, user, passwd);

            String jsonInputString = "{\"location\":" + cab.location + ", \"status\": \""+ cab.status +"\"}";
            //System.out.println("JSON: " + jsonInputString);
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            Cab ret=null;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                Gson g = new Gson();
                ret = g.fromJson(response.toString(), Cab.class);
            }
            con.disconnect();
            return ret;
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage() + "; "+ e.getCause() + "; " + e.getStackTrace().toString());
            return null;
        }
    }

    private static <T> T getEntity(String entityUrl, int user_id, int id) {
        String user = "cab" + user_id;
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

    private class Route {
        List<Task> tasks;
        public Route(List<Task> tasks) { this.tasks = tasks;}
        public List<Task> getTasks() { return tasks; }
        public void setTasks(List<Task> tasks) { this.tasks = tasks; }

    }

    private class Task {
        int fromStand, toStand, place;
        public Task(int fromStand, int toStand, int place) {
            this.fromStand = fromStand;
            this.toStand = toStand;
            this.place = place;
        }
        public int getFromStand() { return this.fromStand; }
        public void setFromStand(int stand) { this.fromStand = stand; }
        public int getToStand() { return this.toStand; }
        public void setToStand(int stand) { this.toStand = stand; }
        public int getPlace() { return place; }
        public void setPlace(int order) { this.place = place; }
    }

    private class Cab {
        public int id;
        public int location;
        public CabStatus status;

        public enum CabStatus {
            ASSIGNED,
            FREE,
            CHARGING, // out of order, ...
        }
    }

    // static public <T> T covertFromJsonToObject(String json, Class<T> var) throws IOException{
    //     ObjectMapper mapper = new ObjectMapper();
    //     return mapper.readValue(json, var);//Convert Json into object of Specific Type
    // }
}
