/*  Author: Bogusz Jelinski
    Project: Kabina/Kaboot
    Date: 2020
*/
// javac -cp ../lib/gson-2.8.6.jar CabGenerator.java CabRunnable.java
// java -cp ../lib/gson-2.8.6.jar;. CabGenerator

import java.util.logging.Logger;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

public class CabGenerator {
    final static int maxCabs = 1000;
    final static Logger logger = Logger.getLogger("kaboot.simulator.cabgenerator");

    public static void main(String[] args) throws InterruptedException {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");
        configureLogger();
        try {
            FileHandler handler = new FileHandler("cab.log", true); // true = append
            logger.addHandler(handler);
        } catch (IOException ioe) {
            logger.warning("Error opening log: " +ioe.getMessage());
            return;
        }
        for (int c = 0; c < maxCabs; c++) {
            final int id = c;
            (new Thread(new CabRunnable(id))).start();
            try { Thread.sleep(10); // so that to disperse them a bit and not to kill backend
             } catch (InterruptedException e) {}
        }
    }

    private static Logger configureLogger() {
        FileHandler fh;
        try {
            fh = new FileHandler("cab.log");
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
}
