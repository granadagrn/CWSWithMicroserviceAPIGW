package com.example.eurkaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication  //Marks the entry point of the Spring Boot application
@EnableEurekaServer     //Enables Eureka server functionality.
public class EurekaServerAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerAppApplication.class, args);
    }

}
