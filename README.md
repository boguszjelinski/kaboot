# Kaboot
This repository contains a subproject of Kabina - Kaboot taxi dispatcher, a SpringBoot application with some clients
that help test the dispatcher. Kaboot dispatcher is composed of two parts - the **dispatcher**, that finds
the optimal assignment plan for requests and available cabs, and **Rest API**, which is responsible
for receiving requests, share statuses and store them in a database. 

Kaboot dispatcher consists of three vital components:
* GLPK linear solver called from a Python subroutine, scenarios with 1000 customers & 1000 cabs have been tested
* Pool finder to assign several customers to one cab, currently max 4 separate passengers/requests are allowed (single-threaded)
* low-cost method (aka greedy) pre-solver to decrease the size of models sent to solver 

You can find theoretical foundations with code examples on GitHub: https://github.com/boguszjelinski/taxidispatcher

## Kabina subprojects:
The idea behind Kabina is to provide an enabler (a software skeleton, testing framework and RestAPI standard proposal)
for a taxi service that can assign up to 10+ passengers to 
one cab (mini-bus), thus reducing cost of the driver per passenger (self-driven buses might not be allowed i many countries,
we might need a 'driver' to prevent fraud). Such extended cab service would allow for the shift to sustainable transport,
it might be cost-competitive with the public transport while providing better service quality including shorter travel time.
It is not meant to be a market-ready solution due to limited resources. 

* Kabina: mobile application for taxi customers 
* Kab: mobile application for taxi drivers 
* Kaboot: dispatcher with RestAPI
* Kadm: administration and surveillance

## Prerequisites:
* Python with CVXOPT and NUMPY packages
* GLPK solver: https://www.gnu.org/software/glpk/
* PostgreSQL (tested with MariaDB too)
* Java SDK
## How to run
### Core

Scheduler can be started with *mvn spring-boot:run*
The first run will set up database schema. 
You have to insert rows to 'cab' and 'customer' tables, which tells Kaboot to authenticate 
these clients. You will find SQL scripts in the db directory, you can import them with:
```
psql -U kabina kabina < create_cabs.sql
```
The same with customers. Be sure your database configuration (host, database, user and password) is reflected in *application.yml*. 

Dispatcher is scheduled to run every minute. 

### Clients
In 'generators' directory you will find Rest API client applications that simulate
behaviour of thousands of cabs and customers. Multiple Java threads are awakened to life, separate ones
for each customer and cab. They submit requests to the server side (backend), checks statuses and write two logs. 
Threads simulating cabs "move" virtually just by waiting specific amount of time (1min = 1km).
You can start with just one cab and one customer in two separate command line windows:
```
javac OneCab.java
java OneCab 0 1
2020-12-19 16:45:21 INFO   CabRunnable live Starting my shift, cab=0
2020-12-19 16:45:21 INFO   CabRunnable live Updating cab=0, free at: 1
2020-12-19 16:45:52 INFO   ApiClient log New route to run, cab_id=0, route_id=418781,
2020-12-19 16:45:52 INFO   ApiClient log Moving, from=1, to=2, cab_id=0, leg_id=418782,
2020-12-19 16:46:52 INFO   ApiClient log Saving leg=418782, JSON={"status":"COMPLETE"}
2020-12-19 16:46:52 INFO   ApiClient log Saving cab=0, JSON={"location":"2", "status": "ASSIGNED"}
2020-12-19 16:47:52 INFO   ApiClient log Moving, from=2, to=3, cab_id=0, leg_id=418783,
2020-12-19 16:48:52 INFO   ApiClient log Saving leg=418783, JSON={"status":"COMPLETE"}
2020-12-19 16:48:53 INFO   ApiClient log Saving cab=0, JSON={"location":"3", "status": "FREE"}

javac OneCustomer.java
java OneCustomer 0 2 3 10 30
2020-12-19 16:45:25 INFO   CustomerRunnable live Request cust_id=0, from=2, to=3
2020-12-19 16:45:25 INFO   ApiClient logCust Cab requested, cust_id=0, order_id=418780,
2020-12-19 16:45:56 INFO   ApiClient log Assigned, cust_id=0, order_id=418780, cab_id=0,
2020-12-19 16:45:56 INFO   ApiClient log Accepted, waiting for that cab, cust_id=0, order_id=418780, cab_id=0,
2020-12-19 16:46:56 INFO   ApiClient log Picked up, cust_id=0, order_id=418780, cab_id=0,
2020-12-19 16:48:58 INFO   ApiClient log Arrived at 3, cust_id=0, order_id=418780, cab_id=0,
```
Parameters mean that cab '0' is waiting at stand '1', a custemer requests a trip from stand '2' to '3', 
with 10min acceptable wait time and 30% of acceptable time waste in pool.
In order to run thousands of cabs and customers type this in two separate command line windows: 
```
javac CabGenerator.java
java -Dnashorn.args="--no-deprecation-warning" CabGenerator

javac CustomerGenerator.java
java -Dnashorn.args="--no-deprecation-warning" CustomerGenerator
```

## How it works
### Core
* available cabs and incoming requests from customers are read from database
* pool discoverer checks if we can assign more customers than one to a cab without affecting badly
  duration of their trips. Each customer can choose their tolerance, or decide that a pool is 
  not acceptable. Maximally four passengers can be assigned to one cab due to core's performance limitations.
  Pool discoverer produces pools with four, three and two customers. 
* Unassigned customers (without a pool) and first legs of pools (customer which is picked up first) are sent to
  LCM pre-solver if the resulting model exceeds an assumed solver's limit. Solver produces better plans than LCM but 
  time spent on finding optimal solutions, which theoretically means shorter overall wait time, causes longer ...
  wait time. A balance needs to be found.
* models reduced by LCM are sent to GLPK solver via Python interface.
* after all this effort 'routes' with 'legs' are created in the database, 
  'cab' and 'taxi_order' tables are updated - marked as 'assigned'.
  RestAPI clients receive this information - cabs begin to move, customers wait for notification that their cabs have
  reached their stands and can pick them up. Currently, the following statuses may be assigned to an order:
  - RECEIVED: sent by customer
  - ASSIGNED: assigned to a cab, a proposal sent to customer with time-of-arrival
  - ACCEPTED: plan accepted by a customer, waiting for the cab
  - CANCELLED: cancelled by a customer before assignment
  - REJECTED:  proposal rejected by customer
  - ABANDONED: cancelled after assignment but before PICKEDUP
  - REFUSED: no cab available
  - PICKEDUP: cab has arrived
  - COMPLETE: customer dropped off
  
### Cab
* wait for a route
* after having received a route - go to the first customer and follow 'legs' of the route.
* wait 1min after having reached a waypoint (stand) - time for customers to get the notification via RestAPI
* mark cab as FREE at the last stand

### Customer 
* request a cab
* wait for an assignment - a proposal 
* do you like it ?
* wait for a cab
* take a trip
* mark the end (COMPLETE)

## Current work in kaboot
There is a lot of work in progress in Kaboot:
* putting together all simulation parameters (se chapter below), getting rid of hardcodes
* big load tuning (60k requests per hour)
* taxi order at a specific time, not ASAP
* adding passengers during a planned route 
* ad-hoc passengers on stops ("hail and ride")
* multithreaded pool discovery (massive SMP)
* anticipatory allocation based on currently executed routes
* call GLPK directly, without Python
* removing hardcoded parameters

## Future work
* distance service based on data from the field
* charging plans & payment integration
* resistance to bizarre situations (customers interrupting trips, for example)
* use of commercial solvers - performance gain?
* extended tuning  
* get on with other Kabina modules

## Important parameters / limits

| Parameter | Value | Location | Purpose
|----------|--------|----------|----------------------------------
| datasource.url | jdbc:... | application.yml | location of the database
| max-non-lcm | 200 | application.yml | limit the size of model which is sent to solver to speed up performance
| max-pool4 | 600 | application.yml | max size of demand that can be sent to pool discoverer with 4 passengers; to speed up performance
| max-pool3 | 1200 | application.yml | max size of demand that can be sent to pool discoverer with 3 passengers; to speed up performance
| max-stand | 50 | application.yml | cost matrix generation in PoolUtil; for future use in CabGenerator.java - random start location
| solver.cmd | runpy.bat | application.yml | path to python and solver
| solver.cost | cost.txt | application.yml | temporary file with cost matrix sent to solver
| solver.output | solv_out.txt | application.yml | output vector from solver 
| SCHEDULING_DURATION | 2 | LcmUtil | assumed duration of dispatching taken into consideration while checking if we can meet customer expectations. It might be replaced by a forecast based on demand size.
| MAX_WAIT | 10 | CustomerGenerator | customers can only wait 10min for a cab.
| MAX_POOL_LOSS | 1 | CustomerGenerator | 1%, restrictive, customers can only lose 1% of time compared to non-pooled trip
| MAX_TRIP | 4 | CustomerGenerator | maximum trip duration
| REQ_PER_MIN | 100 | CustomerGenerator | number of requests per minute
| DURATION | 120 | CustomerGenerator | how many minutes lasts the simulation, total number of requests = DURATION * REQ_PER_MIN
| MAX_WAIT_FOR_RESPONSE | 3 | CustomerRunnable | does a customer need a quick response? (see also SCHEDULING_DURATION). This is different from maxWait attribute, which defines how long can a customer wait for a cab to come (see also ETA). Serious problem if scheduler cannot answer within, say, 3 minutes.
| MAX_TRIP_LOSS | 2 | CustomerRunnable | this is a buffer for RestAPI latency, just an internal delay to analyze only errors in the algorithm. 
| MAX_TRIP_LEN | 30 | CustomerRunnable | check gen_demand.py, taxi_demand.txt also
| CHECK_INTERVAL | 15 | CustomerRunnable | secs; how often to check assignment and trip completion
| MAX_CABS | 1000 | CabGenerator | how many cabs are available. All IDs have to be in the 'cab' table!

## RestAPI
The following endpoints are available now with described purposes:

| Endpoint | Method | Purpose
|----------|--------|----------------------------------
| /cabs/{id} | GET | Inform customer about location
| /cabs/{id} | PUT | Update location of the cab, mark as FREE
| /cabs/ | POST | not used
| /orders/{id} | GET | inform about a cab assignment
| /orders/{id} | PUT | accepting, canceling a trip, mark as completed
| /orders/ | POST | submit a trip request - a cab is needed 
| /routes/ | GET | get ONE route that a cab should follow with all legs
| /routes/{id} | PUT | mark as completed
| /legs/{id} | PUT | mark as completed
| /schedulework/ | GET | manually trigger dispatcher 

## Core's KPIs
During runtime a few measures are gathered and stored in the database - see 'stat' table. It allows for 
tuning of the core:
- avg_lcm_size
- avg_lcm_time
- avg_model_size
- avg_order_assign_time	
- avg_order_complete_time	
- avg_order_pickup_time	
- avg_pool_time		
- avg_pool3_time		
- avg_pool4_time		
- avg_sheduler_time	
- avg_solver_size		
- avg_solver_time		
- max_lcm_size		
- max_lcm_time		
- max_model_size		
- max_pool_time		
- max_pool3_time		
- max_pool4_time		
- max_sheduler_time	
- max_solver_size		
- max_solver_time		
- total_lcm_used		
- total_pickup_distance


## Copyright notice

Copyright 2020 Bogusz Jelinski

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

#
Bogusz Jelinski    
December 2020  
Mo i Rana
