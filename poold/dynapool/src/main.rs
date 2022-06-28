use std::convert::TryInto;
use std::fs::File;
use std::io::{BufReader};
use std::error::Error;
use std::process;
use serde::Deserialize;
use std::{thread, time};
use std::time::{SystemTime, UNIX_EPOCH};
extern crate csv;
extern crate rustc_serialize;

const MEMSIZE : usize = 20000;
const THRMEMSIZE : usize = 10000;
const MAXSTOPSNUMB : usize = 5200;
const MAXORDERNUMB: usize = 2000;
const MAXCABNUMB: usize = 5000;
const MAXBRANCHNUMB: usize = 100;
const MAXINPOOL : usize = 4;
const MAXORDID : usize = MAXINPOOL * 2;
const MAXANGLE : i16 = 120;
const MAXNODE : usize = MAXINPOOL + MAXINPOOL - 1;

static mut stops : [Stop; MAXSTOPSNUMB] = [Stop {
				id: 0, latitude: 0.0, longitude: 0.0, bearing: 0}; MAXSTOPSNUMB];
static mut STOPS_LEN: i32 = 0;

static mut orders : [Order; MAXORDERNUMB] = [Order {
	id: 0, from: 0, to: 0, wait: 0,	loss: 0, dist: 0}; MAXORDERNUMB];
static mut ORDERS_LEN: i32 = 0;

static mut cabs : [Cab; MAXCABNUMB] = [Cab {id: 0, stand: 0}; MAXCABNUMB];
static mut CABS_LEN: i32 = 0;

static mut DISTANCE : [[i16; MAXSTOPSNUMB]; MAXSTOPSNUMB] = [[0; MAXSTOPSNUMB]; MAXSTOPSNUMB];
const M_PI : f64 = 3.14159265358979323846264338327950288;
const M_PI_180 : f64 = M_PI / 180.0;
const REV_M_PI_180 : f64 = 180.0 / M_PI;

#[repr(C)]
#[derive(Copy, Clone, Deserialize)]
struct Stop {
    id: i32,
	bearing: i16,
	longitude: f64,
	latitude: f64,
}

#[repr(C)]
#[derive(Copy, Clone, Deserialize)]
struct Order {
    id: i32, // -1 as to-be-dropped
	from: i16,
    to: i16,
	wait: i16,
	loss: i16,
	dist: i16
}

#[repr(C)]
#[derive(Copy, Clone,Deserialize)]
struct Cab {
    id: i32,
	stand: i16
}

#[repr(C)]
#[derive(Copy,Clone,Debug)]
struct Branch {
	cost: i16,
	outs: u8, // BYTE, number of OUT nodes, so that we can guarantee enough IN nodes
	ordNumb: i16, // it is in fact ord number *2; length of vectors below - INs & OUTs
	ordIDs : [i32; MAXORDID], // we could get rid of it to gain on memory (key stores this too); but we would lose time on parsing
	ordActions: [i8; MAXORDID],
	ordIDsSorted: [i32; MAXORDID],
	ordActionsSorted: [i8; MAXORDID],
	cab :i32
}

impl Branch {
    pub fn new() -> Self {
        Self {
            cost: 0,
			outs: 0,
			ordNumb: 0,
			ordIDs: [0; MAXORDID],
			ordActions: [0; MAXORDID],
			ordIDsSorted: [0; MAXORDID],
			ordActionsSorted: [0; MAXORDID],
			cab : -1
        }
    }
}

#[link(name = "dynapool25")]
extern "C" {
    fn dynapool(
		numbThreads: i32,
		distance: &[[i16; MAXSTOPSNUMB]; MAXSTOPSNUMB],
		distSize: i32,
		stops: &[Stop; MAXSTOPSNUMB],
		stopsSize: i32,
		orders: &[Order; MAXORDERNUMB],
		ordersSize: i32,
		cabs: &[Cab; MAXCABNUMB],
		cabsSize: i32,
		ret: &mut [Branch; MAXBRANCHNUMB], // returned values
		retSize: i32,
		count: &mut i32 // returned count of values
    );
}

fn read_stops(path: &str, arr: &mut [Stop]) -> Result<(), Box<dyn Error>> {
	let mut i : usize = 0; 
	for record in get_reader(path).deserialize() {
		arr[i] = record?;
		i = i + 1;
    }
	unsafe { STOPS_LEN = i as i32; }
	Ok(())
}

fn read_orders(path: &str, arr: &mut [Order]) -> Result<(), Box<dyn Error>> {
	let mut i : usize = 0; 
	for record in get_reader(path).deserialize() {
		arr[i] = record?;
		i = i + 1;
    }
	unsafe { ORDERS_LEN = i as i32; }
	Ok(())
}

fn read_cabs(path: &str, arr: &mut [Cab]) -> Result<(), Box<dyn Error>> {
	let mut i : usize = 0; 
	for record in get_reader(path).deserialize() {
		arr[i] = record?;
		i = i + 1;
    }
	unsafe { CABS_LEN = i as i32; }
	Ok(())
}

fn get_reader(path: &str) -> csv::Reader<std::io::BufReader<std::fs::File>> {
	let file = File::open(path).unwrap();
    let reader = BufReader::new(file);
	return csv::Reader::from_reader(reader);
}

fn main() {
	//println!("cargo:rustc-link-search=/Users/m91127/Boot/kaboot/poold/lib");
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
			
			if let Err(err) = read_orders("/Users/m91127/Boot/kaboot/orders2.csv",
								&mut orders) {
				println!("Error reading orders: {}", err);
				process::exit(1);
			}

			if let Err(err) = read_cabs("/Users/m91127/Boot/kaboot/cabs2.csv",
								&mut cabs) {
				println!("Error reading cabs: {}", err);
				process::exit(1);
			}
			let mut br: [Branch; MAXBRANCHNUMB] = [Branch::new(); MAXBRANCHNUMB];
			let mut cnt: i32 = 0;
			dynapool(
				4,
				&DISTANCE, // as *mut [i16; MAXSTOPSNUMB],
				MAXSTOPSNUMB as i32,
				&stops,
				STOPS_LEN,
				&orders,
				ORDERS_LEN,
				&cabs,
				CABS_LEN,
				&mut br, // returned values
				MAXBRANCHNUMB as i32,
				&mut cnt // returned count of values
			);
			println!("RET:{} {} {}", cnt, br[0].cab, br[0].ordNumb);
			//find_pool(4, 8, &demand, &cabs);
			}
			//std::fs::remove_file("/Users/m91127/Boot/kaboot/flag.txt");
			break;
		} else {
			thread::sleep(time::Duration::from_millis(500));
			println!(".");
		}
	}
}

fn deg2rad(deg: f64) -> f64 { return deg * M_PI_180; }
fn rad2deg(rad: f64) -> f64 { return rad * REV_M_PI_180; }

// https://dzone.com/articles/distance-calculation-using-3
fn dist(lat1:f64, lon1:f64, lat2: f64, lon2: f64) -> f64 {
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
