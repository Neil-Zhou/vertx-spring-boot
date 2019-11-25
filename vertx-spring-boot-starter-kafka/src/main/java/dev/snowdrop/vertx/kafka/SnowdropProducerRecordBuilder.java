package dev.snowdrop.vertx.kafka;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.springframework.util.StringUtils;

final class SnowdropProducerRecordBuilder<K, V> implements ProducerRecordBuilder<SnowdropProducerRecord<K, V>, K, V> {

    private final String topic;

    private final V value;

    private final List<Header> headers;

    private K key;

    private Integer partition;

    private Long timestamp;

    SnowdropProducerRecordBuilder(String topic, V value) {
        if (StringUtils.isEmpty(topic)) {
            throw new IllegalArgumentException("Topic cannot be empty");
        }

        this.topic = topic;
        this.value = value;
        this.headers = new LinkedList<>();
    }

    @Override
    public ProducerRecordBuilder<SnowdropProducerRecord<K, V>, K, V> withKey(K key) {
        this.key = key;
        return this;
    }

    @Override
    public ProducerRecordBuilder<SnowdropProducerRecord<K, V>, K, V> withPartition(int partition) {
        if (partition < 0) {
            throw new IllegalArgumentException(
                String.format("Invalid partition: %d. Partition number cannot be negative.", partition));
        }
        this.partition = partition;
        return this;
    }

    @Override
    public ProducerRecordBuilder<SnowdropProducerRecord<K, V>, K, V> withTimestamp(long timestamp) {
        if (timestamp < 0) {
            throw new IllegalArgumentException(
                String.format("Invalid timestamp: %d. Timestamp cannot be negative.", timestamp));
        }
        this.timestamp = timestamp;
        return this;
    }

    @Override
    public ProducerRecordBuilder<SnowdropProducerRecord<K, V>, K, V> withHeader(Header header) {
        Objects.requireNonNull(header, "Header cannot be null");
        this.headers.add(header);
        return this;
    }

    @Override
    public ProducerRecordBuilder<SnowdropProducerRecord<K, V>, K, V> withHeaders(List<Header> headers) {
        Objects.requireNonNull(headers, "Headers cannot be null");
        this.headers.addAll(headers);
        return this;
    }

    @Override
    public SnowdropProducerRecord<K, V> build() {
        return new SnowdropProducerRecord<>(key, value, topic, partition, timestamp, headers);
    }
}
