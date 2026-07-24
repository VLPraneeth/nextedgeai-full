package com.syncari.connector.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.*;
import static org.apache.kafka.common.config.SaslConfigs.SASL_JAAS_CONFIG;
import static org.apache.kafka.common.config.SaslConfigs.SASL_MECHANISM;

@Slf4j
@Component
public class KafkaClient {
    public static final String BOOTSTRAP_SERVERS = "bootstrap.servers";
    public static final String TOPIC = "topic";
    private static final String CURSOR_DELIMITER = "|";
    private static final String DELIMITER = ":";
    @Autowired
    ObjectMapper mapper;

    public EntitySchema getSchema(ConnectorInfo config, String entityName) {
        String consumerId = getConsumerId(new Pipeline("describe-pipeline", "APPROVED", "999999"));
        var client = getConsumerClient(config, consumerId);
        EntitySchema schema = new EntitySchema();
        schema.setApiName(entityName);
        schema.setDisplayName(entityName);
        try {
            // Read a few sample messages to infer schema
            client.subscribe(Collections.singletonList(entityName));
            ConsumerRecords<String, Object> sampleMessages = client.poll(Duration.ofMinutes(1));

            if (sampleMessages.isEmpty()) {
                log.info("No messages found in topic {} to infer schema", entityName);
                return schema;
            }

            Map<String, AttributeSchema> attributes = new HashMap<>();
            try {
                for (ConsumerRecord<String, Object> record : sampleMessages) {
                    EntityData entityData = extractData(record, entityName);
                    for(Object key: entityData.getValues().keySet()) {
                        if(!attributes.containsKey(key.toString())) {
                            attributes.put(key.toString(), createAttributeSchema(key.toString(), key.toString(), "Text", false));
                        }
                    }
                }
            } catch (Exception e) {
                log.error(ExceptionUtils.getStackTrace(e));
            }
            schema.setAttributes(new ArrayList<>(attributes.values()));
            return schema;
        } catch (Exception e) {
            log.error("Error inferring schema from Kafka topic {}: {}", entityName, e.getMessage(), e);
            return null;
        } finally {
            client.close();
        }
    }

    public DataWithCursor getData(ConnectorInfo config, String prevCursor, int pageSize, String topic, String consumerId) {
        var client = getConsumerClient(config, consumerId, pageSize);
        Map<Integer, Long> partitionOffsetMap = new HashMap<>();
        try {
            if(prevCursor != null) {
                // Build partition-offset map
                List<String> offsets = Arrays.stream(prevCursor.split("\\" + CURSOR_DELIMITER)).collect(Collectors.toList());
                Map<TopicPartition, Long> partitionMap = getPartitionOffsetMap(topic, offsets);

                // Assign all target partitions
                client.assign(partitionMap.keySet());

                // Seek to specified offsets
                for (Map.Entry<TopicPartition, Long> entry : partitionMap.entrySet()) {
                    client.seek(entry.getKey(), entry.getValue()+1);
                }
            } else {
                client.subscribe(Collections.singletonList(topic));
            }
            ConsumerRecords<String, Object> sampleMessages = client.poll(Duration.ofMinutes(1));
            List<EntityData> results = new ArrayList<>();

            for (ConsumerRecord<String, Object> record : sampleMessages) {
                results.add(extractData(record, topic));
                Long existingOffset = partitionOffsetMap.getOrDefault(record.partition(), 0L);
                partitionOffsetMap.put(record.partition(), Math.max(existingOffset, record.offset()));
            }

            results = results.stream().sorted(Comparator.comparingLong(EntityData::getLastModified)).collect(Collectors.toList());
                // The cursor is encoded with each partition and its offset
                // example: p1:100|p2:200|p3:300
            String nextCursor = partitionOffsetMap.entrySet().stream()
                        .map(entry -> entry.getKey() + DELIMITER + entry.getValue())
                        .collect(Collectors.joining(CURSOR_DELIMITER));
            return new DataWithCursor(prevCursor, nextCursor, results);
        } catch (Exception e) {
            log.error("Error fetching data from Kafka topic {}: {}", topic, ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        } finally {
            client.close();
        }
    }

    public List<EntityData> getDataByIds(ConnectorInfo connector, List<String> ids, String topic, String consumerId) {
        var client = getConsumerClient(connector, consumerId, 1);
        if (ids == null || ids.isEmpty()) return new ArrayList<>();

        List<EntityData> results = new ArrayList<>();

        try {
            // Build partition-offset map
            Map<TopicPartition, Long> partitionOffsetMap = getPartitionOffsetMap(topic, ids);

            // Assign all target partitions
            client.assign(partitionOffsetMap.keySet());

            // Seek to specified offsets
            for (Map.Entry<TopicPartition, Long> entry : partitionOffsetMap.entrySet()) {
                client.seek(entry.getKey(), entry.getValue());
            }

            // Poll messages
            ConsumerRecords<String, Object> records = client.poll(Duration.ofSeconds(5));

            // Match only the requested offsets
            for (ConsumerRecord<String, Object> record : records) {
                TopicPartition tp = new TopicPartition(record.topic(), record.partition());
                long targetOffset = partitionOffsetMap.get(tp);
                if (record.offset() == targetOffset) {
                    results.add(extractData(record, topic));
                }
            }

            return results;
        } catch (Exception e) {
            log.error("Error fetching data from Kafka topic {}: {}", topic, ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        } finally {
            client.close();
        }
    }

    public void commit(ConnectorInfo connectorInfo, WatermarkInfo wm, String topic, String consumerId) {
        var client = getConsumerClient(connectorInfo, consumerId);
        Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        try {
            List<String> parts = Arrays.stream(wm.getStreamState().getPreviousCursor().split("\\"+CURSOR_DELIMITER)).collect(Collectors.toList());
            for (String part : parts) {
                String[] split = part.split(DELIMITER);
                offsets.put(new TopicPartition(topic, Integer.parseInt(split[0])), new OffsetAndMetadata(Long.parseLong(split[1])));
            }
            client.commitSync(offsets);
            log.info("Successfully commited kafka wm for {}", wm.getChangeStream());
        } finally {
            client.close();
        }
    }

    public void create(ConnectorInfo connector, String consumerId, List<EntityData> records, String topic) {
        var client = getProducerClient(connector, consumerId);
        try {
            for (EntityData d : records) {
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, d.getName(), mapper.writeValueAsString(d));
                client.send(record).get();
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            client.close();
        }
    }

    public Map listTopics(ConnectorInfo connector) {
        Consumer client = getConsumerClient(connector, getDefaultConsumerId());
        Map<String, List<PartitionInfo>> topics = new HashMap<>();
        try {
            Map<String, List<PartitionInfo>> listTopics = client.listTopics();
            String topicConfig = connector.getMetaConfigValue(TOPIC, null);
            if(StringUtils.isBlank(topicConfig) || "*".equalsIgnoreCase(topicConfig) ) {
                // list all topics
                return listTopics;
            }
            String[] split = topicConfig.split(",");
            for (String topic : split) {
                if(!StringUtils.isBlank(topic)) {
                    topics.put(topic.trim(), listTopics.get(topic));
                }
            }
            return topics;
        } finally {
            client.close();
        }
    }

    private Consumer getConsumerClient(ConnectorInfo connector, String consumerId, int pageSize) {
        Properties props = getProperties(connector, consumerId, pageSize);
        return new KafkaConsumer<>(props);
    }

    private Consumer getConsumerClient(ConnectorInfo connector, String consumerId) {
        return getConsumerClient(connector, consumerId, 500);
    }

    private Producer getProducerClient(ConnectorInfo connector, String consumerId) {
        Properties props = getProperties(connector, consumerId, 1000);
        return new KafkaProducer<>(props);
    }

    private static Properties getProperties(ConnectorInfo connector, String consumerId, int pageSize) {
        Properties props = new Properties() {{
            // User-specific properties that you must set
            put(BOOTSTRAP_SERVERS_CONFIG, connector.getMetaConfigValue(BOOTSTRAP_SERVERS, null));
            String username = connector.getAuthConfig().getUserName();
            String password = connector.getAuthConfig().getPassword();
            put(SASL_JAAS_CONFIG,         "org.apache.kafka.common.security.plain.PlainLoginModule required username='"+username+"' password='"+password+"';");

            // Fixed properties
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
            put(KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class.getCanonicalName());
            put(VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getCanonicalName());
            put(GROUP_ID_CONFIG,                 consumerId);
            put(AUTO_OFFSET_RESET_CONFIG,        "earliest");
            put(SECURITY_PROTOCOL_CONFIG,        "SASL_SSL");
            put(SASL_MECHANISM,                  "PLAIN");
            put(ENABLE_AUTO_COMMIT_CONFIG, "false");
            put(MAX_POLL_RECORDS_CONFIG, pageSize);
        }};
        return props;
    }

    private AttributeSchema createAttributeSchema(String name, String displayName, String type, boolean required) {
        AttributeSchema attr = new AttributeSchema();
        attr.setApiName(name);
        attr.setDisplayName(displayName);
        attr.setDataType(type);
        attr.setNillable(!required);
        attr.setCustom(false);
        return attr;
    }

    private EntityData extractData(ConsumerRecord<String, Object> record, String topic) throws JsonProcessingException {
        EntityData ed = new EntityData();
        ed.setName(topic);
        ed.setId(record.partition()+DELIMITER+record.offset());
        ed.setLastModified(record.timestamp());
        log.debug("Key: {}, Value: {}, Partition: {}, Offset: {}}",
                record.key(), record.value(), record.partition(), record.offset());
        try {
            Map<Object, Object> map = mapper.readValue((String) record.value(), Map.class);
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                String key = entry.getKey().toString();
                Object value = entry.getValue();
                if(value != null) {
                    if("values".equalsIgnoreCase(key)) {
                        ed.getValues().putAll((Map)value);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error extracting data for {}", record);
        }
        ed.addValue(Constants.SYNCARI_FABRICATED_WATERMARKFIELD, record.timestamp());
        ed.addValue("id", ed.getId());
        ed.addValue("headers", getHeaders(record.headers()));
        return ed;
    }

    private Map getPartitionOffsetMap(String topic, List<String> ids) {
        Map<TopicPartition, Long> partitionOffsetMap = new HashMap<>();
        for (String id : ids) {
            String[] parts = id.split("\\" + CURSOR_DELIMITER);
            for(String part : parts) {
                String[] split = part.split(DELIMITER);
                TopicPartition partition = new TopicPartition(topic, Integer.parseInt(split[0]));
                long offset = Long.parseLong(split[1]);
                partitionOffsetMap.put(partition, offset);
            }
        }
        return partitionOffsetMap;
    }

    private Map getHeaders(Headers headers) {
        Map result = new HashMap();
        for (Header h : headers) {
            result.put(h.key(), h.value());
        }
        return  result;
    }

    private String getConsumerId(Pipeline pipeline) {
        return pipeline.getInstanceId()+"_"+pipeline.getDraftStatus()+"_"+pipeline.getApiName();
    }

    private String getDefaultConsumerId() {
        return getConsumerId(new Pipeline("describe-pipeline", "APPROVED", "999999"));
    }

}
