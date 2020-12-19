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
// javac OneCab.java
// java -Dnashorn.args="--no-deprecation-warning" OneCab

import java.util.logging.Logger;

public class OneCab extends ApiClient {

    public static void main(String[] args) {
        logger = Logger.getLogger("kaboot.simulator.onecab");
        logger = configureLogger(logger, "onecab.log");
        if (args.length != 2) {
            logger.warning("Missing argument: <id> <stand>");     
            return;
        }
        final int id = Integer.parseInt(args[0]);
        final int where = Integer.parseInt(args[1]);
        (new Thread(new CabRunnable(id, where))).start();
    }
}
