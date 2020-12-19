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

// javac OneGenerator.java 

import java.util.logging.Logger;

public class OneCustomer extends ApiClient {
    
    public static void main(String[] args) {
        logger = Logger.getLogger("kaboot.simulator.onecustomer");
        logger = ApiClient.configureLogger(logger, "onecustomer.log");
        int maxStand = getFromYaml("../../src/main/resources/application.yml", "max-stand");
        if (maxStand == -1) {
            logger.warning("Error reading max-stand from YML");     
            return;
        }
        if (args.length != 5) {
            logger.warning("Missing arguments: <id> <from> <to> <maxWait> <maxLoss>");     
            return;
        }
        if (Integer.parseInt(args[1])> maxStand -1 || Integer.parseInt(args[2])> maxStand-1) {
            logger.warning("Invalid 'from' or 'to'");
            return;
        }
        final Demand d = new Demand(Integer.parseInt(args[0]), // id
                                    Integer.parseInt(args[1]), // from
                                    Integer.parseInt(args[2]), // to
                                    Integer.parseInt(args[3]), // max wait
                                    Integer.parseInt(args[4])  // max loss
                                );
        (new Thread(new CustomerRunnable(d))).start();
    }
}
