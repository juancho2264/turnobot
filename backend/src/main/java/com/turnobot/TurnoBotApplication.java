package com.turnobot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TurnoBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TurnoBotApplication.class, args);
    }
}
