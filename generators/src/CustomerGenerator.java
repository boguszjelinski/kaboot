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
    final static Logger logger = Logger.getLogger("kaboot.simulator.cabgenerator");
    //final static String DEMAND_FILE = "C:\\Users\\dell\\TAXI\\GIT\\simulations\\taxi_demand.txt";
   // final static String DEMAND_FILE = "/home/m91127/GITHUB/taxidispatcher/simulations/taxi_demand.txt";
    final static String DEMAND_FILE = "C:\\Users\\dell\\TAXI\\GIT\\simulations\\taxi_demand.txt";
    static long demandCount=0;
    static int [][] demand = new int[100000][5];
    static int maxTime = 0;

    public static void main(String[] args) throws InterruptedException {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");
        readDemand();
        if (demand.length == 0) {
            logger.info("Error reading demand file");
            return;
        }
        logger.info("Orders in total: " + demand.length);
        configureLogger();
        logger.info("Start");

        for (int t = 0; t < maxTime; t++) { // time axis
            // filter out demand for this time point
            for (int i = 0; i < demand.length; i++) {
                if (demand[i][4] == t
                    && demand[i][1] != demand[i][2]) { // part of the array is empty, that would not work for t==0
                    final int[] d = demand[i];
                    (new Thread(new CustomerRunnable(d))).start();
                }
            }
            TimeUnit.SECONDS.sleep(10);
            break; // just t==0
        }
    }

    private static Logger configureLogger() {
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

    private static void readDemand() {
        try {
            Path path = Path.of(DEMAND_FILE);
            demandCount = Files.lines(path).count();
            BufferedReader reader = new BufferedReader(new FileReader(DEMAND_FILE));
            String line = reader.readLine();
            int count=0;
            while (line != null) {
                if (line == null) break;
                line = line.substring(1, line.length()-1); // get rid of '('
                String[] lineVector = line.split(",");
                demand[count][0] = Integer.parseInt(lineVector[0]); // ID
                demand[count][1] = Integer.parseInt(lineVector[1]); // from
                demand[count][2] = Integer.parseInt(lineVector[2]);  // to
                demand[count][3] = Integer.parseInt(lineVector[3]); // time we learn about customer
                demand[count][4] = Integer.parseInt(lineVector[4]); // time at which he/she wants the cab
                //demand[count].setStatus(OrderStatus.RECEIVED);
                if (maxTime<Integer.parseInt(lineVector[4])) {
                    maxTime = Integer.parseInt(lineVector[4]);
                }
                count++;
                line = reader.readLine();
            }
            reader.close();
        }
        catch (IOException e) {
            logger.info("Exception: " + e.getMessage());
        }
    }
}
