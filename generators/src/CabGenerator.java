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
// javac CabGenerator.java
// java -Dnashorn.args="--no-deprecation-warning" CabGenerator

import java.util.logging.Logger;

public class CabGenerator extends ApiClient {
    static final int MAX_CABS = 1000;

    public static void main(String[] args) throws InterruptedException {
        logger = Logger.getLogger("kaboot.simulator.cabgenerator");
        logger = configureLogger(logger, "cabs.log");
        int maxStand = getFromYaml("../../src/main/resources/application.yml", "max-stand");
        if (maxStand == -1) {
            logger.warning("Error reading max-stand from YML");     
            return;
        }
        for (int c = 0; c < MAX_CABS; c++) {
            final int id = c;
            (new Thread(new CabRunnable(id, id % maxStand))).start();
            Thread.sleep(5); // so that to disperse them a bit and not to kill backend
        }
    }
}
