package com.attendance.course.sessions;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QrConfiguration {
    @Bean
    Clock qrClock() {
        return Clock.systemUTC();
    }
}
