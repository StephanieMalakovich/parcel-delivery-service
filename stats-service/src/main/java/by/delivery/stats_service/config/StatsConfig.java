package by.delivery.stats_service.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StatsProperties.class)
public class StatsConfig {

    @Bean
    Clock clock(StatsProperties properties) {
        return Clock.system(properties.zoneId());
    }
}
