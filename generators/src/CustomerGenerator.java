/*  Author: Bogusz Jelinski
    Project: Kabina/Kaboot
    Date: 2020
*/

// javac CustomerGenerator.java 

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class CustomerGenerator {
    static Logger logger = Logger.getLogger("kaboot.simulator.customergenerator");
    //final static String DEMAND_FILE = "C:\\Users\\dell\\TAXI\\GIT\\simulations\\taxi_demand.txt";
   // final static String DEMAND_FILE = "/home/m91127/GITHUB/taxidispatcher/simulations/taxi_demand.txt";
    final static String DEMAND_FILE = "C:\\Users\\dell\\TAXI\\GIT\\simulations\\taxi_demand.txt";
    static long demandCount=0;
    static int [][] demand = new int[100000][5];
    static int maxTime =  120;

    public static void main(String[] args) throws InterruptedException {
        logger = Utils.configureLogger(logger, "customer.log");

        readDemand();
        if (demand.length == 0) {
            logger.info("Error reading demand file");
            return;
        }
        logger.info("Orders in total: " + demand.length);

        for (int t = 0; t < maxTime; t++) { // time axis
            // filter out demand for this time point
            for (int i = 0; i < demand.length; i++) {
                if (demand[i][4] == t
                    && demand[i][1] != demand[i][2]) { // part of the array is empty, that would not work for t==0
                    if (i % 3 != 0) { // just to reduce scheduler load
                        final int[] d = demand[i];
                        (new Thread(new CustomerRunnable(d))).start();
                        try { Thread.sleep(5); // so that to disperse them a bit and not to kill backend
                        } catch (InterruptedException e) {}
                    }
                }
            }
            TimeUnit.SECONDS.sleep(60); // 120min
        }
        
       /* int[] de = new int[5];
        de[0] = 113570;
        de[1] = 11;
        de[2] = 12;
        de[3] = 0;
        de[4] = 0;
        final int[] d = de;
        (new Thread(new CustomerRunnable(d))).start();*/
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
