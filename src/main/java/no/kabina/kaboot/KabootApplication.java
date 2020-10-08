package no.kabina.kaboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KabootApplication {

	public static void main(String[] args) {
		SpringApplication.run(KabootApplication.class, args);
	}

}

	/*drop table taxi_order;
		drop table cab cascade;
		drop table task;
		drop table route;
		drop table customer;*/