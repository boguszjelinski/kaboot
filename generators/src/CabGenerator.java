/*  Author: Bogusz Jelinski
    Project: Kabina/Kaboot
    Date: 2020
*/
// javac CabGenerator.java
// java CabGenerator

import java.util.logging.Logger;

public class CabGenerator {
    final static int maxCabs = 1000;
    static Logger logger = Logger.getLogger("kaboot.simulator.cabgenerator");

    public static void main(String[] args) throws InterruptedException {
        logger = Utils.configureLogger(logger, "cabs.log");
        for (int c = 0; c < maxCabs; c++) {
            final int id = c;
            (new Thread(new CabRunnable(id))).start();
            try { Thread.sleep(10); // so that to disperse them a bit and not to kill backend
            } catch (InterruptedException e) {}
        }
    }
}
