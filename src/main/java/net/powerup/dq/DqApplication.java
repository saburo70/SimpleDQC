package net.powerup.dq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DqApplication {

	public static void main(String[] args) {
		SpringApplication.run(DqApplication.class, args);
	}

}
