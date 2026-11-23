package by.delivery.stats_service.config;

import java.time.Clock;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StatsProperties.class)
public class StatsConfig {

    @Bean
    Clock clock(StatsProperties properties) {
        return Clock.system(properties.zoneId());
    }

    @Bean
    NewTopic analyticsEventsTopic(StatsProperties properties) {
        return TopicBuilder.name(properties.kafka().topic())
                .partitions(properties.kafka().partitions())
                .replicas(1)
                .build();
    }
}
