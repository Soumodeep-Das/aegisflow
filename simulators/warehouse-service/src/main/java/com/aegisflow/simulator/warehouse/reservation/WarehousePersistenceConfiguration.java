package com.aegisflow.simulator.warehouse.reservation;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class WarehousePersistenceConfiguration {

    @Bean
    Clock warehouseClock() {
        return Clock.systemUTC();
    }
}
