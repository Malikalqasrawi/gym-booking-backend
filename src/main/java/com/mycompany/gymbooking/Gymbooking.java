package com.mycompany.gymbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * @author malikalqasrawi
 */
@SpringBootApplication
public class Gymbooking {

    public static void main(String[] args) {
        // Allows Hibernate's ByteBuddy to run on JDK versions newer than it officially supports.
        System.setProperty("net.bytebuddy.experimental", "true");

        SpringApplication.run(Gymbooking.class, args);
    }
}
