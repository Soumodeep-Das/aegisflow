package com.aegisflow.simulator.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderSimulatorApplication {

    public static void main(String[] args) {
        if (java.util.Arrays.asList(args).contains("--aegisflow.local-demo=true")) {
            LocalDemo.run(args);
            return;
        }
        SpringApplication.run(OrderSimulatorApplication.class, args);
    }
}
