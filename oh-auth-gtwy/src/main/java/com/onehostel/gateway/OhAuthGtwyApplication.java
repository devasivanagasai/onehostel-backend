package com.onehostel.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

@SpringBootApplication
@EnableReactiveMongoRepositories(basePackages = {"com.onehostel.gateway.repository"})
public class OhAuthGtwyApplication {

	public static void main(String[] args) {
		SpringApplication.run(OhAuthGtwyApplication.class, args);
	}
}