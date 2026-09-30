package com.mycompany.gymbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starting point of the backend.
 *
 * @SpringBootApplication tells Spring to:
 *  1. scan this package (and sub-packages) for classes marked @RestController, @Service, @Repository, @Component...
 *  2. create one object of each ("beans") and plug them into each other (dependency injection)
 *  3. start a web server on port 8080
 *
 * @author malikalqasrawi
 */
@SpringBootApplication
public class Gymbooking {

    public static void main(String[] args) {
        // Lets Hibernate's bytecode library run on very new Java versions (your JDK is newer than Java 21).
        System.setProperty("net.bytebuddy.experimental", "true");

        SpringApplication.run(Gymbooking.class, args);
    }
}
