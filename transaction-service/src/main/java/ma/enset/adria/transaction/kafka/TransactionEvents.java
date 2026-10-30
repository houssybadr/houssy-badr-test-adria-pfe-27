package ma.enset.adria.transaction.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.enset.adria.transaction.domain.BankTransaction;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.UUID;

@Component
public class TransactionEvents {
    public static final String TOPIC = "bank.transactions.created";
    private static final Logger log = LoggerFactory.getLogger(TransactionEvents.class);
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public TransactionEvents(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @Bean
    public NewTopic transactionsTopic() {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
    }

    public void publish(BankTransaction transaction) {
        TransactionCreated event = new TransactionCreated(UUID.randomUUID(), "TransactionCreated",
                transaction.getId(), transaction.getAccountId(), transaction.getAmount(),
                transaction.getCurrency(), transaction.getCreatedAt().toString());
        try {
            kafka.send(TOPIC, transaction.getAccountId().toString(), mapper.writeValueAsString(event))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Kafka publication failed for completed transaction {}. Do not repeat the bank operation.",
                                    transaction.getId(), error);
                        } else {
                            log.info("KAFKA PRODUCER transaction={} partition={} offset={}", transaction.getId(),
                                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException | RuntimeException error) {
            // Kafka failure must not reverse or repeat an already completed bank movement.
            log.error("Kafka publication failed for completed transaction {}", transaction.getId(), error);
        }
    }

    @KafkaListener(topics = TOPIC, groupId = "bank-transaction-logger")
    public void consume(ConsumerRecord<String, String> message) {
        // Logging only: no repository or banking service call in this consumer.
        log.info("KAFKA CONSUMER topic={} key={} partition={} offset={} event={}",
                message.topic(), message.key(), message.partition(), message.offset(), message.value());
    }

    public record TransactionCreated(UUID eventId, String eventType, UUID transactionId,
                                     UUID accountId, BigDecimal amount, String currency, String occurredAt) {}
}
