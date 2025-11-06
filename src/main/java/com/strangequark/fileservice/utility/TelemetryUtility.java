// Integration file: Telemetry

package com.strangequark.fileservice.utility;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TelemetryUtility {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryUtility.class);

    private KafkaProducer<String, String> producer;
    private String cachedServiceToken = null; // Integration line: Auth
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
            if (!jwtUtility.isTokenValid(cachedServiceToken)) {
                cachedServiceToken = authUtility.authenticateServiceAccount();
            }
            String accessToken = "Bearer " + cachedServiceToken;

            UUID userId = null;

            if(includeUserId)
                userId = UUID.fromString(jwtUtility.extractId()); // Integration function end: Auth

            JSONObject requestBody = new JSONObject();
            requestBody.put("serviceName", "fileservice");
            requestBody.put("eventType", eventType);
            requestBody.put("userId", userId); // Integration line: Auth
            requestBody.put("timestamp", LocalDateTime.now());
            requestBody.put("metadata", new JSONObject(new HashMap<>(metadata)));

            LOGGER.info("Message created, attempting to post to file telemetry Kafka topic");
            ProducerRecord<String, String> record = new ProducerRecord<String, String>(
                    "file-telemetry-events",
                    null,
                    null,
                    requestBody.toString()
                    ,List.of(new RecordHeader("Authorization", accessToken.getBytes())) // Integration line: Auth
            );

            getProducer().send(record);
            LOGGER.info("Telemetry event successfully sent");
        } catch (Exception ex) {
            LOGGER.error("Unable to reach telemetry Kafka service: " + ex.getMessage());
        }
    }

    private KafkaProducer<String, String> getProducer() {
        if (producer == null) {
            Properties props = new Properties();
            props.put("bootstrap.servers", "telemetry-kafka:9093");
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            producer = new KafkaProducer<>(props);
        }
        return producer;
    }
}
