use std::fs::File;
use std::io::{BufReader};
use std::error::Error;
use std::process;
use serde::Deserialize;
use std::{thread, time};
use std::time::{SystemTime, UNIX_EPOCH};
extern crate csv;
extern crate rustc_serialize;

const NUMBTHREAD: usize = 5;
const MAXJOB: u32 = 100000;
const MEMSIZE : usize = 100000000;
const MAXSTOPNAME : u8 = 200;
const MAXSTOPSNUMB : usize = 5200;
const MAXORDERSNUMB : u16 = 2000;
const MAXCABSNUMB : u16 = 3000;
const MAXKEYLEN : u8 = 100;
const MAXINPOOL : usize = 4;
const MAXORDID : usize = MAXINPOOL * 2;
const MAXANGLE : f32 = 120.0;
const MAXLEVSIZE : u32 = 1200000;
const MAXNODE : usize = MAXINPOOL + MAXINPOOL - 1;
const MAXJSON : u32 = 50000;
const MAXTHREADMEM : u32 = 2500000;

static mut nodeSize : [u32; MAXNODE] = [0; MAXNODE];
static mut nodeSizeSMP : [u8; NUMBTHREAD] = [0; NUMBTHREAD];

static mut stops : [Stop; 5500] = [Stop {
    								id: 0, latitude: 0.0, longitude: 0.0, bearing: 0}; 5500];
static mut STOPS_LEN: u16 = 0;

static mut DISTANCE : [[i16; MAXSTOPSNUMB]; MAXSTOPSNUMB] = [[0; MAXSTOPSNUMB]; MAXSTOPSNUMB];
const M_PI : f32 = 3.14159265358979323846264338327950288;
const M_PI_180 : f32 = M_PI / 180.0;
const REV_M_PI_180 : f32 = 180.0 / M_PI;

static mut demand: Vec<Order> = Vec::new();
//static mut NODE: [Branch; MEMSIZE] = [Branch::new(); MEMSIZE];

#[derive(Copy, Clone, Deserialize)]
struct Stop {
    id: u16,
	latitude: f32,
    longitude: f32,
	bearing: i16
}

#[derive(Deserialize)]
struct Order {
    id: i16, // -1 as to-be-dropped
	from: u16,
    to: u16,
	wait: i16,
	loss: i16,
	dist: u16
}

#[derive(Deserialize)]
struct Cab {
    id: i32,
	stand: u16
}

#[derive(Clone,Debug)]
struct Branch {
	key : String, // used to remove duplicates and search in hashmap
	cost: i16,
	outs: u8, // BYTE, number of OUT nodes, so that we can guarantee enough IN nodes
	ordNumb: u8, // it is in fact ord number *2; length of vectors below - INs & OUTs
	ordIDs : [i16; MAXORDID], // we could get rid of it to gain on memory (key stores this too); but we would lose time on parsing
	ordActions: [char; MAXORDID],
	ordIDsSorted: [i16; MAXORDID],
	ordActionsSorted: [char; MAXORDID],
	cab :i32
}

impl Branch {
    pub fn new() -> Self {
        Self {
            key: "".to_string(),
            cost: 0,
			outs: 0,
			ordNumb: 0,
			ordIDs: [0; MAXORDID],
			ordActions: [' '; MAXORDID],
			ordIDsSorted: [0; MAXORDID],
			ordActionsSorted: [' '; MAXORDID],
			cab : -1
        }
    }
}

fn read_stops(path: &str, arr: &mut [Stop]) -> Result<(), Box<dyn Error>> {
	let mut i : usize = 0; 
	for record in get_reader(path).deserialize() {
		arr[i] = record?;
		i = i + 1;
    }
	unsafe { STOPS_LEN = i as u16; }
	Ok(())
}

fn read_orders(path: &str, arr: &mut Vec<Order>) -> Result<(), Box<dyn Error>> {
	for record in get_reader(path).deserialize() {
		arr.push(record?);
    }
	Ok(())
}

fn read_cabs(path: &str, arr: &mut Vec<Cab>) -> Result<(), Box<dyn Error>> {
	for record in get_reader(path).deserialize() {
		arr.push(record?);
    }
	Ok(())
}

fn get_reader(path: &str) -> csv::Reader<std::io::BufReader<std::fs::File>> {
	let file = File::open(path).unwrap();
    let reader = BufReader::new(file);
	return csv::Reader::from_reader(reader);
}

fn main() {
	unsafe {
	  if let Err(err) = read_stops("/Users/m91127/Boot/kaboot/db/stops-Budapest-import.csv",
						&mut stops) {
        println!("Error reading stops: {}", err);
        process::exit(1);
      }
	}
	init_distance();

	loop {
		if std::path::Path::new("/Users/m91127/Boot/kaboot/flag.txt").is_file() {
			unsafe {
			demand = Vec::new();
			let mut cabs: Vec<Cab> = Vec::with_capacity(1);
		
			if let Err(err) = read_orders("/Users/m91127/Boot/kaboot/orders2.csv",
								&mut demand) {
				println!("Error reading orders: {}", err);
				process::exit(1);
			}

			if let Err(err) = read_cabs("/Users/m91127/Boot/kaboot/cabs2.csv",
								&mut cabs) {
				println!("Error reading cabs: {}", err);
				process::exit(1);
			}
			println!("START");
			find_pool(4, 8, &demand, &cabs);
			}
			//std::fs::remove_file("/Users/m91127/Boot/kaboot/flag.txt");
			break;
		} else {
			thread::sleep(time::Duration::from_millis(500));
			println!(".");
		}
	}
}

fn find_pool(in_pool: u8, threads: i16, orders: &'static Vec<Order>, stops2: &Vec<Cab>) {
	let root = dive(0, in_pool, threads, orders);
    //for (int i = 0; i < inPool + inPool - 1; i++)
    //    printf("node[%d].size: %d\n", i, countNodeSize(i));
    rm_final_duplicates(in_pool, &root);
    println!("FINAL: inPool: {}, found pools: {}\n", in_pool, root.len());
}

fn dive(lev: u8, in_pool: u8, threads_numb: i16, orders: &'static Vec<Order>) -> Vec<Branch> {
	if lev > in_pool + in_pool - 3 { // lev >= 2*inPool-2, where -2 are last two levels
		return store_leaves(orders);
		// last two levels are "leaves"
	}
	let mut t_numb = threads_numb;
	
	let prev_node = dive(lev + 1, in_pool, t_numb, orders);
	
	let mut ret:Vec<Branch> = Vec::with_capacity(1000000);
	let mut children = vec![];
	let chunk: f32 = orders.len() as f32 / t_numb as f32;
	if ((t_numb as f32 * chunk).round() as i16) < orders.len() as i16 { t_numb += 1; } // last thread will be the reminder of division
	
    for i in 0..t_numb { // TASK: allocated orders might be spread unevenly -> count non-allocated and devide chunks ... evenly
		let prev = prev_node.to_vec();
		children.push(thread::spawn(move || {
			iterate(lev, in_pool, i, chunk, orders, &prev)
        }));
    }
	for handle in children {
        let mut vector = handle.join().unwrap();
		ret.append(&mut vector);  
    }
	println!("Level: {}, size: {}", lev, ret.len());
	return ret;
}

fn store_leaves(orders: &Vec<Order>) -> Vec<Branch> {
	let mut ret : Vec<Branch> = Vec::new();
	for c in 0..orders.len() {
	  if orders[c].id != -1 {
		for d in 0..orders.len() {
		  if orders[d].id != -1 { // not allocated in previous search: inPool+1 (e.g. in_pool=4 and now we search in_pool=3)
		 	// to situations: <1in, 1out>, <1out, 2out>
			unsafe {
		 	if c == d  {
			// IN and OUT of the same passenger, we don't check bearing as they are probably distant stops
		 		ret.push(add_branch(c as i16, d as i16, 'i', 'o', 1, orders));
		 	} else if (DISTANCE[orders[c].to as usize][orders[d].to as usize] as f32)
				< DISTANCE[orders[d].from as usize][orders[d].to as usize] as f32
					* (100.0 + orders[d].loss as f32) / 100.0
		 			&& bearing_diff(stops[orders[c].to as usize].bearing, stops[orders[d].to as usize].bearing) < MAXANGLE {
		 		// TASK - this calculation above should be replaced by a redundant value in taxi_order - distance * loss
		 		ret.push(add_branch(c as i16, d as i16, 'o', 'o', 2, orders));
		 	}
			}
		  }
		}
	  }
	}
	return ret;
}

fn bearing_diff(a: i16, b: i16 ) -> f32 {
    let mut r = (a as f32 - b as f32) % 360.0;
    if r < -180.0 {
      r += 360.0;
    } else if r >= 180.0 {
      r -= 360.0;
    }
    return r.abs();
}

fn add_branch(id1: i16, id2: i16, dir1: char, dir2: char, outs: u8, orders: &Vec<Order>) -> Branch {
    let mut br : Branch = Branch::new();
	br.key = "".to_string();
    if id1 < id2 || (id1==id2 && dir1 == 'i') {
		//br.key = sprintf!("%d%c%d%c", id1, dir1, id2, dir2).unwrap();
		br.key = format!("{}{}{}{}", id1, dir1, id2, dir2);
        br.ordIDsSorted[0] = id1;
        br.ordIDsSorted[1] = id2;
        br.ordActionsSorted[0] = dir1;
        br.ordActionsSorted[1] = dir2;
    }
    else if id1 > id2 || id1 == id2 {
        br.key = format!("{}{}{}{}", id2, dir2, id1, dir1);
        br.ordIDsSorted[0] = id2;
        br.ordIDsSorted[1] = id1;
        br.ordActionsSorted[0] = dir2;
        br.ordActionsSorted[1] = dir1;
    }
	unsafe {
    	br.cost = DISTANCE[orders[id1 as usize].to as usize][orders[id2 as usize].to as usize];
	}
    br.outs = outs;
    br.ordIDs[0] = id1;
    br.ordIDs[1] = id2;
    br.ordActions[0] = dir1;
    br.ordActions[1] = dir2;
    br.ordNumb = 2;
    return br;
}

// multithreaded - range of orders is split into chunks
// fn iterate2<'arr>(lev: u8, in_pool: u8, start: i16, size: i16, orders: &Vec<Order>, node: &Vec<Branch>) 
// 				-> &'arr mut Vec<Branch> {
// 	let stop: i16 = if (start + 1) * size > orders.len() as i16 { orders.len() as i16 } 
// 					else { (start + 1) * size };
	
// 	let ret: &'arr mut Vec<Branch>;

// 	for ord_id in start .. stop {
// 		if orders[ord_id as usize].id != -1 { // not allocated in previous search (inPool+1)
// 			for b in node.iter() {
// 				if b.cost != -1 {  
// 					// we iterate over product of the stage further in the tree: +1
// 					storeBranchIfNotFoundDeeperAndNotTooLong(lev, in_pool, ord_id, b, orders, ret);
// 				}
// 			}
// 		}
// 	}
// 	return ret;
// }

fn iterate(lev: u8, in_pool: u8, start: i16, size: f32, orders: &Vec<Order>, node: &Vec<Branch>) 
				-> Vec<Branch> {
	let next = ((start + 1) as f32 * size).round() as i16;
	let stop: i16 = if next > orders.len() as i16 { orders.len() as i16 } else { next };
	
	let ret: &mut Vec<Branch> = &mut Vec::with_capacity(100000);
	println!("A: start:{} stop:{}, time: {}", (start as f32 * size).round() as u8 , stop, 
				SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis());

	for ord_id in (start as f32 * size).round() as i16 .. stop {
		if orders[ord_id as usize].id != -1 { // not allocated in previous search (inPool+1)
			for b in node.iter() {
				if b.cost != -1 {  
					// we iterate over product of the stage further in the tree: +1
					storeBranchIfNotFoundDeeperAndNotTooLong(lev, in_pool, ord_id, b, orders, ret);
				}
			}
		}
	}
	println!("B: start:{} stop:{}, time: {}", (start as f32 * size).round() as u8 , stop, 
				SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis());
	return ret.to_vec();
}

// br is existing Branch in lev+1
fn storeBranchIfNotFoundDeeperAndNotTooLong(lev: u8, in_pool: u8, ord_id: i16, br: &Branch, 
											orders: &Vec<Order>, ret: &mut Vec<Branch>) {
    // two situations: c IN and c OUT
    // c IN has to have c OUT in level+1, and c IN cannot exist in level + 1
    // c OUT cannot have c OUT in level +1
    let mut in_found : bool = false;
    let mut out_found : bool = false;
    for i in 0 .. (br.ordNumb as usize) {
      if br.ordIDs[i] == ord_id {
        if br.ordActions[i] == 'i' {
          in_found = true;
        } else {
          out_found = true;
        }
        // current passenger is in the branch below
      }
    }
    // now checking if anyone in the branch does not lose too much with the pool
    // c IN
    let next_stop: usize = if br.ordActions[0] == 'i'
                    	{ orders[br.ordIDs[0] as usize].from as usize } 
						else { orders[br.ordIDs[0] as usize].to as usize };
	let id = ord_id as usize;
	unsafe {
    if !in_found
        && out_found
        && !is_too_long(DISTANCE[orders[id].from as usize][next_stop], br, orders)
        // TASK? if the next stop is OUT of passenger 'c' - we might allow bigger angle
        && bearing_diff(stops[orders[id].from as usize].bearing, stops[next_stop].bearing) < MAXANGLE
        { ret.push(store_branch('i', lev, ord_id, br, in_pool, orders)); }
    // c OUT
    if lev > 0 // the first stop cannot be OUT
        && br.outs < in_pool // numb OUT must be numb IN
        && !out_found // there is no such OUT later on
        && !is_too_long(DISTANCE[orders[id].to as usize][next_stop], br, orders)
        && bearing_diff(stops[orders[id].to as usize].bearing, stops[next_stop].bearing) < MAXANGLE
        { ret.push(store_branch('o', lev, ord_id, br, in_pool, orders)); }
	}
}

fn is_too_long(dist: i16, br: &Branch, orders: &Vec<Order>) -> bool {
	let mut wait = dist;
    for i in 0..(br.ordNumb as usize) {
		let id = br.ordIDs[i] as usize;
        if wait as f32 >  //distance[orders[br.ordIDs[i]].fromStand][orders[br.ordIDs[i]].toStand] 
            orders[id].dist as f32 * (100.0 + orders[id].loss as f32) / 100.0 { return true; }
        if br.ordActions[i] == 'i' && wait > orders[id].wait { return true; }
		unsafe {
        if i + 1 < br.ordNumb as usize {
            wait += DISTANCE[if br.ordActions[i] == 'i' { orders[id].from as usize} 
							 else { orders[id].to as usize }] 
							[if br.ordActions[i + 1] == 'i' { orders[br.ordIDs[i + 1] as usize].from as usize }
							 else { orders[br.ordIDs[i + 1] as usize].to as usize } ];
		}
		}
    }
    return false;
}

// b is existing Branch in lev+1
fn store_branch(action: char, lev: u8, ord_id: i16, b: &Branch, in_pool: u8, orders: &Vec<Order>) -> Branch  {
	let mut br : Branch = Branch::new();
	//br.key = "".to_string();

    br.ordNumb = in_pool + in_pool - lev;
    br.ordIDs[0] = ord_id;
    br.ordActions[0] = action;
    br.ordIDsSorted[0] = ord_id;
    br.ordActionsSorted[0] = action;
    
    for j in 0.. (br.ordNumb as usize - 1) { // further stage has one passenger less: -1
      br.ordIDs[j + 1]      = b.ordIDs[j];
      br.ordActions[j + 1]  = b.ordActions[j];
      br.ordIDsSorted[j + 1]= b.ordIDs[j];
      br.ordActionsSorted[j + 1] = b.ordActions[j];
    }
	br.key = format!("{}{}{}", ord_id, action, b.key);
	unsafe {
    br.cost = DISTANCE[if action == 'i' { orders[ord_id as usize].from as usize} 
						else { orders[ord_id as usize].to as usize }]
                      [if b.ordActions[0] == 'i' { orders[b.ordIDs[0] as usize].from as usize} 
					   else { orders[b.ordIDs[0]as usize].to as usize} ] + b.cost;
	}
    br.outs = if action == 'o' { b.outs + 1 } else { b.outs };
    return br;
}

fn rm_final_duplicates(in_pool: u8, root: &Vec<Branch>) {
	
}

fn deg2rad(deg: f32) -> f32 { return deg * M_PI_180; }
fn rad2deg(rad: f32) -> f32 { return rad * REV_M_PI_180; }

// https://dzone.com/articles/distance-calculation-using-3
fn dist(lat1:f32, lon1:f32, lat2: f32, lon2: f32) -> f32 {
    let theta = lon1 - lon2;
    let mut dist = deg2rad(lat1).sin() * deg2rad(lat2).sin() + deg2rad(lat1).cos()
                  * deg2rad(lat2).cos() * deg2rad(theta).cos();
    dist = dist.acos();
    dist = rad2deg(dist);
    dist = dist * 60.0 * 1.1515;
    dist = dist * 1.609344;
    return dist;
}

fn init_distance() {
  unsafe {
    for i in 0..(STOPS_LEN as usize) {
        DISTANCE[i][i] = 0;
        for j in i + 1 ..(STOPS_LEN as usize) {
            let d = dist(stops[i].latitude, stops[i].longitude, stops[j].latitude, stops[j].longitude);
            DISTANCE[stops[i].id as usize][stops[j].id as usize] = d as i16; // TASK: we might need a better precision - meters/seconds
            DISTANCE[stops[j].id as usize][stops[i].id as usize] 
				= DISTANCE[stops[i].id as usize][stops[j].id as usize];
        }
    }
  }
}
