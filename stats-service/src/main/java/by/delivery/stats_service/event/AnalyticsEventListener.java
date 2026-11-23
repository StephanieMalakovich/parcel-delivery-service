package by.delivery.stats_service.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import by.delivery.stats_service.service.EventProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventListener {

    private final EventProcessingService eventProcessingService;

    @KafkaListener(topics = "${stats.kafka.topic}")
    public void onEvent(ConsumerRecord<String, String> record) {
        log.debug("[Broker 2] Получено событие: partition={}, offset={}, key={}",
                record.partition(), record.offset(), record.key());
        try {
            eventProcessingService.handle(record.value());
        } catch (InvalidEventException e) {
            log.error("[Broker 2] Событие пропущено: partition={}, offset={}, причина: {}",
                    record.partition(), record.offset(), e.getMessage());
        }
    }
}
