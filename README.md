# Kaboot
This repository contains a subproject of Kabina - Kaboot taxi dispatcher, a SpringBoot application with some clients
that help test the dispatcher. Kaboot dispatcher is composed of two parts - the **dispatcher**, that finds
the optimal assignment plan for requests and available cabs, and **Rest API**, which is responsible
for receieving requests, share statuses and store them in the database. 

Kaboot core is comprised of three vital components:
* GLPK linear solver called from a Python subroutine, scenarios with 1000 customers & 1000 cabs have been tested
* Pool finder to assign several customers to one cab, currently max 4 separate passengers/requests are allowed (single-threaded)
* low-cost method (aka greedy) pre-solver to decrease the size of models sent to solver 

You can find theoretical foundations with code examples on GitHub: https://github.com/boguszjelinski/taxidispatcher

## Kabina subprojects:
The idea behind Kabina is to provide an enabler (a software skeleton, testing framework, RestAPI standard proposal)
for a taxi service that can assign up to 10+ passengers to 
one cab (mini-bus), thus reducing cost of the driver per passenger. It could make it competitive 
with the public trasport while providing better service quality including shorter travel time.
It is not meant to be a market-ready solution due to limited resources. 

* Kabina: mobile application for taxi customers 
* Kab: mobile application for taxi drivers 
* Kaboot: dispatcher
* Kadm: administration and survailance

## Prerequisites:
* Python with CVXOPT and NUMPY packages
* GLPK solver: https://www.gnu.org/software/glpk/
* PostgreSQL (tested with MariaDB too)
* Java SDK

## How to run
### Core

Scheduler can be started with *mvn spring-boot:run*

Dispatcher is scheduled to run every minute. 

### Clients
In 'generators' directory you will find Rest API client applications that simulate
behaviour of cabs and customers. Thousands of Java threads are awakaned to life,
they submit requests to the server side (backend), checks statuses and write two logs. 
Threads simulating cabs "move" virtually just by waiting specific ammount of time (1min = 1km).
```
javac CabGenerator.java
javac CustomerGenerator.java
java -Dnashorn.args="--no-deprecation-warning" CabGenerator
java -Dnashorn.args="--no-deprecation-warning" CustomerGenerator
```

## Current work in kaboot
* big load tuning (60k requests per hour)
* taxi order at a specific time, not ASAP
* adding passengers during a planned route 
* ad-hoc passengers on stops ("hail and ride")
* multithreaded pool discovery (massive SMP)
* anticipatory allocation based on currently executed routes
* try to call GLPK directly, without Python

<span style="color:blue">
  Bogusz Jelinski<br>    
  December 2020<br>  
  Mo i Rana
  </span>
