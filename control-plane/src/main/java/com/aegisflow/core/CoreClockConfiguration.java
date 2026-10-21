package com.aegisflow.core;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Infrastructure (including Modulith Moments) needs one unqualified application clock. */
@Configuration(proxyBeanMethods=false)
class CoreClockConfiguration {
    @Bean @Primary Clock applicationClock() { return Clock.systemUTC(); }
}
