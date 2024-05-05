package com.trading.crypto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class StalkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(StalkerApplication.class, args);
	}

}
