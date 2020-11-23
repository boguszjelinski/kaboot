import java.net.HttpURLConnection;
import java.util.Base64;
import java.net.URL;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Utils {

    public static void setAuthentication(HttpURLConnection con, String user, String passwd) {
        String auth = user + ":" + passwd;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        String authHeaderValue = "Basic " + encodedAuth;
        con.setRequestProperty("Authorization", authHeaderValue);
    }

    public static void saveJSON(String method, String entity, int cab_id, int rec_id, String json) {
        String user = "cab" + cab_id;
        String password = user;
        HttpURLConnection con = null;
        StringBuilder response = new StringBuilder();
        String urlStr = "http://localhost:8080/" + entity + "/";
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
    }

    public static String getEntityAsJson(int user_id, String urlStr) {
        String user = "cab" + user_id;
        StringBuilder result = new StringBuilder();
        HttpURLConnection con = null;
        try {
            // taxi_order will be updated with eta, cab_id and task_id when assigned
            URL url = new URL(urlStr); // assumption that one customer has one order
            con = (HttpURLConnection) url.openConnection();
            setAuthentication(con, user, user);
            result = getResponse(con);
        } catch(Exception e) { e.printStackTrace(); }
        finally { con.disconnect(); }
        return result.toString();
    }

    public static StringBuilder getResponse(HttpURLConnection con) throws UnsupportedEncodingException, IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }
        return response;
    }

    public static void waitSecs(int secs) {
        try { Thread.sleep(secs*1000); } catch (InterruptedException e) {} // one minute
    }

    public static void waitMins(int mins) {
        try { Thread.sleep(mins*60*1000); } catch (InterruptedException e) {} // one minute
    }

    public static enum CabStatus {
        ASSIGNED,
        FREE,
        CHARGING, // out of order, ...
    }

    public static Logger configureLogger(Logger logger, String file) {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");
        FileHandler fh;
        try {
            fh = new FileHandler(file);
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

    /*
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
    }*/
}
