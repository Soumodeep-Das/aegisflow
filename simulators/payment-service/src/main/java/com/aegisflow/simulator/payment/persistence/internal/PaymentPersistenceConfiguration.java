package com.aegisflow.simulator.payment.persistence.internal;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("postgres")
class PaymentPersistenceConfiguration {

    @Bean
    Clock paymentClock() {
        return Clock.systemUTC();
    }
}
