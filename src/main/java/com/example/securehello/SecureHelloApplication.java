package com.example.securehello;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SecureHelloApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureHelloApplication.class, args);
    }
}
