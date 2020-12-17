/*  Author: Bogusz Jelinski
    Project: Kabina/Kaboot
    Date: 2020
*/
// javac CabGenerator.java
// java -Dnashorn.args="--no-deprecation-warning" CabGenerator

import java.util.logging.Logger;

public class CabGenerator {
    static final int MAX_CABS = 1000;
    static Logger logger = Logger.getLogger("kaboot.simulator.cabgenerator");

    public static void main(String[] args) throws InterruptedException {
        logger = Utils.configureLogger(logger, "cabs.log");
        for (int c = 0; c < MAX_CABS; c++) {
            final int id = c;
            (new Thread(new CabRunnable(id))).start();
            Thread.sleep(5); // so that to disperse them a bit and not to kill backend
        }
    }
}
