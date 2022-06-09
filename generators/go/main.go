package main

import (
	"errors"
	"kabina/model"
	"kabina/utils"
	"log"
	"math/rand"
	"os"
	"sort"
	"strconv"
	"time"
)

// for cabs
const MAX_TIME = 30 // minutes; how long should a cab wait for assigments == how long customer requests are sent
const CHECK_INTERVAL = 15 // secs
const MAX_CABS = 300
const MAX_STAND = 5191
// the customer part
const REQ_PER_MIN = 60;
const MAX_WAIT = 15;
const MAX_POOL_LOSS = 90; // 90% detour
const MAX_WAIT_FOR_RESPONSE = 3
const MAX_TRIP_LEN = 30
const MAX_TRIP_LOSS = 2

func main() {
	args := os.Args
	if len(args) == 2 && args[1] == "cab" { // CAB
		var multi = MAX_STAND/MAX_CABS
		LOG_FILE := "cabs.log"
	    logFile, err := os.OpenFile(LOG_FILE, os.O_APPEND|os.O_RDWR|os.O_CREATE, 0644)
    	if err != nil {
        	log.Panic(err)
    	}
    	defer logFile.Close()
		log.SetOutput(logFile)
		var stops []model.Stop
		stops, _ = utils.GetEntity[[]model.Stop]("cab0", "/stops");
		// well, why GetStops returns error? sth with Bearing
		for c := 0; c < MAX_CABS; c++ {
			go RunCab(&stops, c, c * multi)
        }
	} else { // CUSTOMER
		LOG_FILE := "customers.log"
	    logFile, err := os.OpenFile(LOG_FILE, os.O_APPEND|os.O_RDWR|os.O_CREATE, 0644)
    	if err != nil {
        	log.Panic(err)
    	}
    	defer logFile.Close()
		log.SetOutput(logFile)
		usrid := 0;
		for t:= 0; t < MAX_TIME; t++ { // time axis
            for i := 0; i < REQ_PER_MIN; i++ {
				var dem model.Demand
				dem.From= rand.Intn(MAX_STAND)
				dem.To 	= utils.RandomTo(dem.From, MAX_STAND)
				dem.MaxWait = MAX_WAIT
				dem.MaxLoss = MAX_POOL_LOSS
				                // 'at time' requests can be simulated with Java client
                RunCustomer(usrid, dem)
				usrid++
                
            }
            time.Sleep(60 * time.Second)
        }
	}
	// wait until all threads complete - 1h?
	time.Sleep(3600 * time.Second) 
}

func RunCab(stops *[]model.Stop, cab_id int, stand int) {
	var usr string = "cab" + strconv.Itoa(cab_id) 
	log.Printf("Starting my shift, cab_id=%d\n", cab_id)
	cab, err := utils.GetEntity[model.Cab](usr, "/cabs/" + strconv.Itoa(cab_id))
	if err != nil {
		log.Printf("cab_id=%d not activated\n", cab_id);
		return;
	}
	
	// non-random, nice deterministic distribution so that simulations are reproducible
	log.Printf("Updating cab_id=%d, free at: %d\n", cab_id, stand)
	utils.UpdateCab(usr, cab_id, stand, "FREE")
	
	cab.Location = stand; // the cab location read from DB (see above) might be wrong, that was the day before e.g.

	for t := 0; t < MAX_TIME * (60/CHECK_INTERVAL); t++ { 
		route, err := utils.GetEntity[model.Route](usr, "/routes") // TODO: status NULL
		if err == nil { // this cab has been assigned a job
			log.Printf("New route to run, cab_id=%d, route_id=%d\n", cab_id,  route.Id);
			// go to first task - virtual walk, just wait this amount of time
			legs := route.Legs
			if len(legs) == 0 {
				log.Printf("Error - route has no legs, cab_id=%d, route_id=%d\n", cab_id, route.Id);
				time.Sleep(CHECK_INTERVAL * time.Second) 
				continue
			}
			// TODO: backend should give a sorted list
			sort.Slice(legs[:], func(i, j int) bool {
				return legs[i].Place < legs[j].Place
			  })
			// the first leg in a route should already be cab's move to pickup the first customer
			// but let's check
			if legs[0].FromStand != cab.Location { 
				// strange - scheduler did not know cab's location (! never found in logs)
				log.Printf("Error, first leg (%d) does not start at cab's (cab_id=%d) location: %d. Moving to stand %d\n", 
										legs[0].Id, cab_id, cab.Location, legs[0].FromStand);
				// cab is moving
				var dist = utils.GetDistance(stops, cab.Location, legs[0].FromStand)
				if dist == -1 {
					log.Printf("GetDistance failed, cab_id=%d from: %d to: %d\n", cab.Id, cab.Location, legs[0].FromStand)
					return
				}
				time.Sleep(time.Duration(120 * dist) * time.Second); // 120sec per km = 30km/h
				cab.Location = legs[0].FromStand;
				// inform that cab is at the stand -> update Cab entity, 'completed' previous Task
				utils.UpdateCab(usr, cab_id, cab.Location, "ASSIGNED")
			}
			deliverPassengers(stops, usr, legs, cab);
			utils.UpdateStatus(usr, "/routes/", route.Id, "COMPLETED")
		} 
		time.Sleep(CHECK_INTERVAL * time.Second)
	}
}

func deliverPassengers(stops *[]model.Stop, usr string, legs []model.Task, cab model.Cab) {
	for l:=0; l < len(legs); l++ {
		time.Sleep(60 * time.Second) // wait 1min: pickup + dropout; but it is stupid if the first leg has no passenger!!
		// go from where you are to task.stand
		task := legs[l]
		log.Printf("Cab cab_id=%d is moving from %d to %d, task_id=%d\n", 
				   cab.Id, task.FromStand, task.ToStand, task.Id)
		task.Status = "STARTED"
		utils.UpdateStatus(usr, "/legs/", task.Id, "STARTED")
		// wait as long as it takes to go
		var dist = utils.GetDistance(stops, task.FromStand, task.ToStand)
		if dist == -1 {
			log.Printf("GetDistance failed, cab_id=%d from: %d to: %d\n", cab.Id, task.FromStand, task.ToStand)
			return
		}
		time.Sleep(time.Duration(120 * dist) * time.Second) // cab is moving

		utils.UpdateStatus(usr, "/legs/", task.Id, "COMPLETED")
		cab.Location = task.ToStand;

		// inform sheduler / customer
		if (l == len(legs) - 1) {
			cab.Status = "FREE"
		}
		utils.UpdateCab(usr, cab.Id, task.ToStand, cab.Status) // such call should 'completed' tasks; at the last task -> 'complete' route and 'free' that cab
		// !! update leg here -> completed
		// a route can be extended with new legs (but only these 'not started'), we have to read it again
		route, err := utils.GetEntity[model.Route](usr, "/routes")
		if err != nil { 
			log.Printf("Could not update route, cab_id=%d\n", cab.Id)
			break
		}
		legs = route.Legs;
		sort.Slice(legs[:], func(i, j int) bool {
			return legs[i].Place < legs[j].Place
		})
	}
}

func RunCustomer(custId int, dem model.Demand) {
	/*
		1. request a cab
		2. wait for an assignment - do you like it ?
		3. wait for a cab
		4. take a trip
		5. mark the end
	*/
	// send to dispatcher that we need a cab
	// order id returned
	var usr string = "cust" + strconv.Itoa(custId) 

	log.Printf("Request cust_id=%d from=%d to=%d\n", custId, dem.From, dem.To)
	order, err := utils.SaveDemand("POST", usr, dem);
  
	if err!=nil {
		log.Printf("Unable to request a cab, cust_id=%d\n", custId)
		return
	}

	log.Printf("Cab requested, cust_id=%d, order_id=%d\n", custId, order.Id)
	time.Sleep(20 * time.Second) // just give the solver some time
	
	order, err = waitForAssignment(usr, order.Id, custId)
	
	if err != nil {
		log.Printf("Waited in vain, no answer, custId=%d, order_id=%d\n", custId, order.Id)
		return
	}
	if order.Status != "ASSIGNED" { //|| ord.cab_id == -1
		log.Printf("Waited in vain, no assignment, custId=%d, order_id=%d\n", custId, order.Id)
		order.Status = "CANCELLED" // just not to kill scheduler
		utils.SaveDemand("PUT", usr, order)
		return
	}

	log.Printf("Assigned, custId=%d, order_id=%d, cab_id=%d\n", custId, order.Id, order.Cab_id)
	
	if order.Eta > order.MaxWait {
		// TASK: stop here, now only complain
		log.Printf("ETA exceeds maxWait, custId=%d, order_id=%d\n", custId, order.Id)
	}
	// maybe not necessary, server/cab does not wait for this
	order.Status = "ACCEPTED"
	utils.SaveDemand("PUT", usr, order)
	log.Printf("Accepted, waiting for that cab, custId=%d, order_id=%d, cab_id=%d\n", custId, order.Id, order.Cab_id)
	
	if !hasArrived(usr, order.Cab_id, dem.From, dem.MaxWait) {
		// complain
		log.Printf("Cab has not arrived, custId=%d, order_id=%d, cab_id=%d\n", custId, order.Id, order.Cab_id)
		order.Status = "CANCELLED" // just not to kill scheduler
		utils.SaveDemand("PUT", usr, order)
		return;
	}
	
	takeATrip(usr, custId, order); 
	
	if order.Status != "COMPLETED" {
		order.Status = "CANCELLED" // just not to kill scheduler
		log.Printf("Status is not COMPLETED, cancelling the trip, custId=%d, order_id=%d, cab_id=%d\n", 
					custId, order.Id, order.Cab_id)
		utils.SaveDemand("PUT", usr, order)
	}
}

func waitForAssignment(usr string, orderId int, custId int) (model.Demand, error) {
	var order model.Demand
	order.Id = -1
	for t := 0; t < MAX_WAIT_FOR_RESPONSE * (60 / CHECK_INTERVAL); t++ {
		order, err := utils.GetEntity[model.Demand](usr, "/orders/" + strconv.Itoa(orderId))
		if err != nil {
			//log.Printf("Serious error, order not found or received, cust_id=%d, order_id=%d\n", custId, orderId);
			// ignore
		}
		if (order.Status == "ASSIGNED")  {
			break;
		}
		time.Sleep(CHECK_INTERVAL * time.Second)
	}
	if (orderId == -1 || order.Status != "ASSIGNED") {
		return order, errors.New("Not assigned")
	}
	return order, nil;
}

func hasArrived(usr string, cabId int, from int, wait int) bool {
	for t := 0; t < wait * (60/CHECK_INTERVAL); t++ { // *4 as 15 secs below
		time.Sleep(CHECK_INTERVAL * time.Second)
		cab, err := utils.GetEntity[model.Cab](usr, "/cabs/" + strconv.Itoa(cabId));
		if err != nil { // ignore error
			continue
		}
		if (cab.Location == from) {
			return true;
		}
	}
	return false;
}

func takeATrip(usr string, custId int, order model.Demand) {
	// authenticate to the cab - open the door?
	log.Printf("Picked up, custId=%d, order_id=%d, cab_id=%d\n", custId, order.Id, order.Cab_id)
	order.Status = "PICKEDUP"
	utils.SaveDemand("PUT", usr, order)

	duration := 0

	for ; duration < MAX_TRIP_LEN * (60/CHECK_INTERVAL); duration++ {
		time.Sleep(CHECK_INTERVAL * time.Second)
		/*order = getEntity("orders/", cust_id, order_id);
		if (order.status == OrderStatus.COMPLETED && order.cab_id != -1)  {
			break;
		}*/
		cab, err := utils.GetEntity[model.Cab](usr, "/cabs/" + strconv.Itoa(order.Cab_id));
		if err != nil { // ignore error
			continue
		}
		if cab.Location == order.To {
			log.Printf("Arrived at %d, custId=%d, order_id=%d, cab_id=%d\n", order.To, custId, order.Id, order.Cab_id);
			order.Status = "COMPLETED"
			utils.SaveDemand("PUT", usr, order) 
			break;
		}
	}
	
	if duration >= MAX_TRIP_LEN * (60.0/CHECK_INTERVAL) {
		log.Printf("Something wrong - customer has never reached the destination, custId=%d, order_id=%d, cab_id=%d\n", 
					custId, order.Id, order.Cab_id)
	} else {
		if order.InPool {
			if duration/(60.0/CHECK_INTERVAL) > order.Distance * (1.0+ (order.MaxLoss/100.0)) + MAX_TRIP_LOSS {
				// complain
				str := " - duration: " + strconv.Itoa(duration/(60/CHECK_INTERVAL)) + 
					", distance: " + strconv.Itoa(order.Distance) +
					", maxLoss: " + strconv.Itoa(order.MaxLoss) +
					", " + strconv.Itoa(duration/(60/CHECK_INTERVAL)) +
					">" + strconv.Itoa(int(order.Distance * (1+ (order.MaxLoss/100)) + MAX_TRIP_LOSS))
				log.Printf("Duration in pool was too long, " + str + ", custId=%d, order_id=%d, cab_id=%d\n", 
							custId, order.Id, order.Cab_id)
			}
		} else { // not a pool
			if duration/(60.0/CHECK_INTERVAL) > order.Distance + MAX_TRIP_LOSS {
				// complain
				str := " - duration: " + strconv.Itoa(duration/(60/CHECK_INTERVAL)) +
					", distance: " + strconv.Itoa(order.Distance) +
					", " + strconv.Itoa(duration/(60/CHECK_INTERVAL))  +
					">" + strconv.Itoa(int(order.Distance + MAX_TRIP_LOSS))
				log.Printf("Duration took too long, " + str + ", custId=%d, order_id=%d, cab_id=%d\n", 
							custId, order.Id, order.Cab_id)
			}
		}
	}
}
