package com.aegisflow.simulator.warehouse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WarehouseSimulatorApplication {

    public static void main(String[] args) {
        if (java.util.Arrays.asList(args).contains("--aegisflow.local-demo=true")) {
            LocalDemo.run(args);
            return;
        }
        SpringApplication.run(WarehouseSimulatorApplication.class, args);
    }
}
