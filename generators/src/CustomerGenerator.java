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

// javac CustomerGenerator.java 

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class CustomerGenerator extends ApiClient {
    static int DURATION =  120; // min
    static int REQ_PER_MIN = 100;
    static int MAX_WAIT = 10;
    static int MAX_POOL_LOSS = 1; // 1%
    static int MAX_TRIP = 4;

    public static void main(String[] args) throws InterruptedException {
        logger = Logger.getLogger("kaboot.simulator.customergenerator");
        logger = ApiClient.configureLogger(logger, "customer.log");

        for (int t = 0; t < DURATION; t++) { // time axis
            // filter out demand for this time point
            for (int i = 0; i < REQ_PER_MIN; i++) {
                int id = t + i;
                int from = randomFrom(id);
                final Demand d = new Demand(id, 
                                            from,
                                            randomTo(from), // to
                                            MAX_WAIT, 
                                            MAX_POOL_LOSS
                                           );
                (new Thread(new CustomerRunnable(d))).start();
                Thread.sleep(5); // so that to disperse them a bit and not to kill backend
            }
            TimeUnit.SECONDS.sleep(60); // 120min
        }
    }

    // no, we can't have a random, we need reproducible results
    private static int randomFrom(int i) {
        int maxStand = getFromYaml("../../src/main/resources/application.yml", "max-stand");
        if (maxStand == -1) {
            logger.warning("Error reading max-stand from YML");     
            System.exit(0);
        }
        int x1 = i % (maxStand / 4);
        int x2 = i % (maxStand / 2);
        int x3 = i % (maxStand -2);
        int a = x1 + x2 + x3;
        if (a >= maxStand -1) {
            a = a % maxStand +1;
            a = a >= maxStand -1 ? a - x3 : a;
            if (a > maxStand -2) a = maxStand -2;
            else if (a<0) a= 0;
        }
        return a;
    }

    private static int randomTo(int from) {
        int diff = from % (MAX_TRIP /2) - MAX_TRIP;
        if (diff == 0) diff = 1;
        int to = 0;
        if (from + diff > maxStand -1 ) to = from - diff;
        else if (from + diff < 0) to =0;
        else to = from + diff;
        return to;
    }
}
