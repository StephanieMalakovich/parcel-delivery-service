package by.delivery.main_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.scheduling.annotation.EnableScheduling;

import by.delivery.stats.grpc.StatisticsServiceGrpc;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ImportGrpcClients(target = "statistics", types = StatisticsServiceGrpc.StatisticsServiceBlockingStub.class)
class StatisticsConfig {
}
