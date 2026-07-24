package com.syncari.connector.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Represents a message from Kafka
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class KafkaMessage {
    private String key;
    private String value;
    private String topic;
    private int partition;
    private long offset;
    private long timestamp;
}