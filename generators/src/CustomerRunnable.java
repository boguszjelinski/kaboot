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
import java.util.logging.Logger;
import static java.lang.StrictMath.abs;

class CustomerRunnable extends ApiClient implements Runnable {
   
    static final int MAX_WAIT_FOR_RESPONSE = 3; // minutes; might be random in taxi_demand.txt
    static final int MAX_WAIT_FOR_CAB = 10; // minutes; might be random in taxi_demand.txt
    static final double MAX_POOL_LOSS = 1.01; // %; might be random in taxi_demand.txt
    static final int MAX_TRIP_LOSS = 4; // minutes; just to not be a jerk!
    static final int MAX_TRIP_LEN = 30; // check gen_demand.py, +/- 4min, 
    static final int CHECK_INTERVAL = 15; // secs

    private Demand tOrder;
    
    public CustomerRunnable(Demand o) {
        this.tOrder = o;
        this.tOrder.setStatus(OrderStatus.RECEIVED);
        logger = Logger.getLogger("kaboot.simulator.customergenerator");
    }
    
    public void run() {
        System.setProperty("java.util.logging.SimpleFormatter.format","%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");
        live(tOrder);
    }

    private void live(Demand d) {
        /*
            1. request a cab
            2. wait for an assignment - do you like it ?
            3. wait for a cab
            4. take a trip
            5. mark the end
        */
        // send to dispatcher that we need a cab
        // order id returned
        int custId = d.id; 
        logger.info("Request cust_id=" + custId + ", from=" + d.from + ", to=" +d.to);
        Demand order = saveOrder("POST", d, custId); //          order = d; but now ith has entity id
        if (order == null) {
            logger.info("Unable to request a cab, cust_id=" + custId);
            return;
        }
        int orderId = order.id;

        logCust("Cab requested", custId, orderId);
        // just give kaboot a while to think about it
        // pool ? cab? ETA ?
        waitSecs(30); // just give the solver some time

        order = waitForAssignment(custId, orderId);
        
        if (order == null || order.status != OrderStatus.ASSIGNED
            //|| ord.cab_id == -1
            ) { // Kaboot has not answered, too busy
            // complain
            if (order == null) {
                logCust("Waited in vain, no answer", custId, orderId);
            } else {
                logCust("Waited in vain, no assignment", custId, orderId);
                order.status = OrderStatus.CANCELLED; // just not to kill scheduler
                saveOrder("PUT", order, custId); 
            }
            return;
        }

        log("Assigned", custId, orderId, order.cab_id);
        
        // TASK: check order.eta > d.at + MAX_WAIT_FOR_CAB) 
        
        order.status = OrderStatus.ACCEPTED;
        saveOrder("PUT", order, custId); // PUT = update
        log("Accepted, waiting for that cab", custId, orderId, order.cab_id);
        
        if (!hasArrived(custId, order.cab_id, d.from)) {
            // complain
            log("Cab has not arrived", custId, orderId, order.cab_id);
            order.status = OrderStatus.CANCELLED; // just not to kill scheduler
            saveOrder("PUT", order, custId); 
            return;
        }
       
        takeATrip(custId, order); 
      
        if (order.status != OrderStatus.COMPLETE) {
            order.status = OrderStatus.CANCELLED; // just not to kill scheduler
            log("Status is not COMPLETE, cancelling the trip", custId, orderId, order.cab_id);
            saveOrder("PUT", order, custId); 
        }
    }

    private Demand waitForAssignment(int custId, int orderId) {
        Demand order = null;
        for (int t = 0; t < MAX_WAIT_FOR_RESPONSE * (60 / CHECK_INTERVAL); t++) {
            order = getOrder(custId, orderId);
            if (order == null) {
                logCust("Serious error, order not found", custId, orderId);
                return null;
            }
            if (order.status == OrderStatus.ASSIGNED)  {
                break;
            }
            waitSecs(CHECK_INTERVAL); 
        }
        return order;
    }

    private boolean hasArrived(int custId, int cabId, int from) {
        for (int t=0; t<MAX_WAIT_FOR_CAB * (60/CHECK_INTERVAL) ; t++) { // *4 as 15 secs below
            waitSecs(CHECK_INTERVAL);
            Cab cab = getCab("cabs/", custId, cabId);
            if (cab.location == from) {
                return true;
            }
        }
        return false;
    }

    private void takeATrip(int custId, Demand order) {
        // authenticate to the cab - open the door?
        log("Picked up", custId, order.id, order.cab_id);
        order.status = OrderStatus.PICKEDUP;
        saveOrder("PUT", order, custId); // PUT = update

        int duration = 0; 
       
        for (; duration<MAX_TRIP_LEN * (60/CHECK_INTERVAL); duration++) {
            waitSecs(CHECK_INTERVAL);
            /*order = getEntity("orders/", cust_id, order_id);
            if (order.status == OrderStatus.COMPLETE && order.cab_id != -1)  {
                break;
            }*/
            Cab cab = getCab("cabs/", custId, order.cab_id);
            if (cab.location == order.to) {
                log("Arrived at " + order.to, custId, order.id, order.cab_id);
                order.status = OrderStatus.COMPLETE;
                saveOrder("PUT", order, custId); 
                break;
            }
        }
          
        if (duration >= MAX_TRIP_LEN * (60/CHECK_INTERVAL)) {
            log("Something wrong - customer has never reached the destination", custId, order.id, order.cab_id);
        } else {
            if (order.inPool) {
                if (duration/(60/CHECK_INTERVAL) > (int) (abs(order.from - order.to) * MAX_POOL_LOSS + MAX_TRIP_LOSS)) {
                    // complain
                    log("Duration in pool was too long", custId, order.id, order.cab_id);
                }
            } else { // not a carpool
                if (duration/(60/CHECK_INTERVAL) > (int) (abs(order.from - order.to) + MAX_TRIP_LOSS)) {
                    // complain
                    log("Duration took too long", custId, order.id, order.cab_id);
                }
            }
        }
    }
}
