/*
 * Copyright 2020 Bogusz Jelinski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// javac -Dnashorn.args="--no-deprecation-warning" -Xms5g -Xmx5g CustomerGenerator.java

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.time.LocalDateTime;
import java.util.Random;

public class CustomerGenerator extends ApiClient {
    static int DURATION =  30; // min
    static int REQ_PER_MIN = 60;
    static int MAX_WAIT = 15;
    static int MAX_POOL_LOSS = 90; // 1%
    static int MAX_TRIP = 4; // this does not have any impact, distance not based on ID any more
    static int AT_TIME_LAG = 30;
    static int maxStand = 3300; // default
    static Random rand;
    public static void main(String[] args) throws InterruptedException {
        logger = Logger.getLogger("kaboot.simulator.customergenerator");
        logger = ApiClient.configureLogger(logger, "../../logs/customer.log");
        rand = new Random(10L);
        maxStand = getFromYaml("../../src/main/resources/application.yml", "max-stand");
        if (maxStand == -1) {
            logger.warning("Error reading max-stand from YML");     
            System.exit(0);
        }
        int id = 0;
        for (int t = 0; t < DURATION; t++) { // time axis
            // filter out demand for this time point
            for (int i = 0; i < REQ_PER_MIN; i++) {
                int from = 10 * rand.nextInt((int)(maxStand/10)); // concentrate demand in one tenth of stops
                                                        // to make common trips more probable
                LocalDateTime atTime = null;
                if (from % 3 == 0 && t < DURATION - AT_TIME_LAG) {
                    atTime = LocalDateTime.now().plusMinutes(AT_TIME_LAG);
                }
                final Demand d = new Demand(id, 
                                            from,
                                            randomTo(from, maxStand), // to
                                            MAX_WAIT, 
                                            MAX_POOL_LOSS,
                                            atTime
                                           );
                (new Thread(new CustomerRunnable(d))).start();
                Thread.sleep(5); // so that to disperse them a bit and not to kill backend
                id++;
            }
            TimeUnit.SECONDS.sleep(60);
        }
    }
    
    private static int randomTo(int from, int maxStand) {
        int diff = rand.nextInt(MAX_TRIP * 2) - MAX_TRIP;
        if (diff == 0) diff = 1;
        int to = 0;
        if (from + diff > maxStand -1 ) to = from - diff;
        else if (from + diff < 0) to = 0;
        else to = from + diff;
        return to;
    }
}
