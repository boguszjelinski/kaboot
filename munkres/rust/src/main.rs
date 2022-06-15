use std::fs::File;
use std::io::Write;
use std::io::{BufRead, BufReader};
use hungarian::minimize;

fn main() {
    let mut matrix1: Vec<i32> = vec![];
    let mut width: i32 = 0;
    let mut length: i32 = 0;
    let filename = "/Users/m91127/Boot/kaboot/rmunkinp.txt";
    let file = File::open(filename).unwrap();
    let reader = BufReader::new(file);
    let mut i=0;
    for (_, line) in reader.lines().enumerate() {
        let line = line.unwrap(); // Ignore errors.
        if i==0 {
            let mut parts = line.split_whitespace().map(|s| s.parse::<i32>());
            width = parts.next().unwrap().unwrap();
            length = parts.next().unwrap().unwrap();
        } else {
          let mut draws: Vec<i32>;
          draws = line.split_whitespace().map(|num| num.trim().parse::<i32>().unwrap()).collect();
          matrix1.append(&mut draws);
        }
        i=i+1;
    }
   
    let assignment = minimize(&matrix1, length.try_into().unwrap(), width.try_into().unwrap());

    let mut w = File::create("/Users/m91127/Boot/kaboot/rmunkout.txt").unwrap();

    for s in assignment {
        if s.is_some() {
            show_zeros(&mut w, s.unwrap() as i32);
            writeln!(&w, "1");
            show_zeros(&mut w, width - s.unwrap() as i32 -1);
        } else {
            show_zeros(&mut w, width);
        }
    }
}

fn show_zeros(f: &mut File, n: i32) {
    if n < 1 { return; }
    for _ in 0..n { 
        writeln!(f, "0");
    }
}
