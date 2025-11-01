// Integration file: Telemetry

package com.strangequark.fileservice.utility;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader; // Integration line: Auth
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired; // Integration line: Auth
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TelemetryUtility {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryUtility.class);
    // Integration function start: Auth
    @Autowired
    private AuthUtility authUtility;
    @Autowired
    private JwtUtility jwtUtility; // Integration function end: Auth

    public void sendTelemetryEvent(String eventType,
                                   boolean includeUserId, // Integration line: Auth
                                   Map<String, Object> metadata) {
        try {
            LOGGER.info("Attempting to post message to file telemetry Kafka topic");
            // Integration function start: Auth
            String accessToken = authUtility.authenticateServiceAccount();
            accessToken = "Bearer " + accessToken;

            UUID userId = null;

            if(includeUserId)
                userId = UUID.fromString(jwtUtility.extractId()); // Integration function end: Auth

            JSONObject requestBody = new JSONObject();
            requestBody.put("serviceName", "fileservice");
            requestBody.put("eventType", eventType);
            requestBody.put("userId", userId); // Integration line: Auth
            requestBody.put("timestamp", LocalDateTime.now());
            requestBody.put("metadata", new JSONObject(new HashMap<>(metadata)));

            Properties props = new Properties();
            props.put("bootstrap.servers", "telemetry-kafka:9093");
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

            String topic = "file-telemetry-events";

            LOGGER.info("Message created, attempting to post to file telemetry Kafka topic");
            KafkaProducer<String, String> producer = new KafkaProducer<>(props);
            ProducerRecord<String, String> record = new ProducerRecord<String, String>(
                    topic,
                    null,
                    null,
                    requestBody.toString()
                    ,List.of(new RecordHeader("Authorization", accessToken.getBytes())) // Integration line: Auth
            );
            producer.send(record);
            producer.close();
            LOGGER.info("Telemetry event successfully sent");
        } catch (Exception ex) {
            LOGGER.error("Unable to reach telemetry Kafka service: " + ex.getMessage());
        }
    }
}
