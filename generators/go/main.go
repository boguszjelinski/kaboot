package main

import (
	"kabina/model"
	"kabina/utils"
	"log"
	"os"
	"sort"
	"strconv"
	"time"
)
const MAX_TIME = 60 // minutes 120
const CHECK_INTERVAL = 15 // secs
const MAX_CABS = 300
const MAX_STAND = 5191

func main() {
	args := os.Args
	if len(args) == 2 && args[1] == "cab" {
		var multi = MAX_STAND/MAX_CABS
		LOG_FILE := "cabs.log"
	    logFile, err := os.OpenFile(LOG_FILE, os.O_APPEND|os.O_RDWR|os.O_CREATE, 0644)
    	if err != nil {
        	log.Panic(err)
    	}
    	defer logFile.Close()
		log.SetOutput(logFile)
		var stops []model.Stop
		stops, _ = utils.GetStops("cab0");
		// well, why GetStops returns error? sth with Bearing
		for c := 0; c < MAX_CABS; c++ {
			go RunCab(&stops, c, c * multi)
        }
	} else {
		
	}
	// wait until all threads complete - 1h?
	time.Sleep(3600 * time.Second) 
}

func RunCab(stops *[]model.Stop, cab_id int, stand int) {
	var usr string = "cab" + strconv.Itoa(cab_id) 
	log.Printf("Starting my shift, cab_id=%d\n", cab_id)
	cab, err := utils.GetCab(usr, cab_id)
	if err != nil {
		log.Printf("cab_id=%d not activated\n", cab_id);
		return;
	}
	
	// non-random, nice deterministic distribution so that simulations are reproducible
	log.Printf("Updating cab_id=%d, free at: %d\n", cab_id, stand)
	utils.UpdateCab(usr, cab_id, stand, "FREE")
	
	cab.Location = stand; // the cab location read from DB (see above) might be wrong, that was the day before e.g.

	for t := 0; t < MAX_TIME * (60/CHECK_INTERVAL); t++ { 
		route, err := utils.GetRoute(usr) // TODO: status NULL
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
			utils.UpdateStatus(usr, "routes", route.Id, "COMPLETED")
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
		utils.UpdateStatus(usr, "legs", task.Id, "STARTED")
		// wait as long as it takes to go
		var dist = utils.GetDistance(stops, task.FromStand, task.ToStand)
		if dist == -1 {
			log.Printf("GetDistance failed, cab_id=%d from: %d to: %d\n", cab.Id, task.FromStand, task.ToStand)
			return
		}
		time.Sleep(time.Duration(120 * dist) * time.Second) // cab is moving

		utils.UpdateStatus(usr, "legs", task.Id, "COMPLETED")
		cab.Location = task.ToStand;

		// inform sheduler / customer
		if (l == len(legs) - 1) {
			cab.Status = "FREE"
		}
		utils.UpdateCab(usr, cab.Id, task.ToStand, cab.Status) // such call should 'completed' tasks; at the last task -> 'complete' route and 'free' that cab
		// !! update leg here -> completed
		// a route can be extended with new legs (but only these 'not started'), we have to read it again
		route, err := utils.GetRoute(usr)
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
