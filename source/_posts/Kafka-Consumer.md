title: Kafka 简单的使用总结
author: xdkxlk
tags: []
categories:
  - Kafka
date: 2018-11-13 09:57:00
---
# 生产者
## 简单的使用方法
```java
public class SimpleAsyncKafKaProducer {

    public static void main(String[] args) {

        final Map<String, Object> config = ImmutableMap.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer",
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer"
        );
        try (val producer = new KafkaProducer<String, String>(config)) {
            val record = new ProducerRecord<String, String>("topic01", "key", "value2");
            producer.send(record, (RecordMetadata metadata, Exception exception) -> {
                if (exception != null) {
                    exception.printStackTrace();
                }
                System.out.println("send success");
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        while (true) {
        }
    }
}
```
## 分区器
### 默认的分区器
```java
public class DefaultPartitioner implements Partitioner {

    private final ConcurrentMap<String, AtomicInteger> topicCounterMap = new ConcurrentHashMap<>();

    public void configure(Map<String, ?> configs) {}

    /**
     * Compute the partition for the given record.
     *
     * @param topic The topic name
     * @param key The key to partition on (or null if no key)
     * @param keyBytes serialized key to partition on (or null if no key)
     * @param value The value to partition on or null
     * @param valueBytes serialized value to partition on or null
     * @param cluster The current cluster metadata
     */
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
        List<PartitionInfo> partitions = cluster.partitionsForTopic(topic);
        int numPartitions = partitions.size();
        if (keyBytes == null) {
            // 如果没有设置key值，则采用轮询的方式到各个分区上面
            int nextValue = nextValue(topic);
            // 获得可用的分区
            List<PartitionInfo> availablePartitions = cluster.availablePartitionsForTopic(topic);
            if (availablePartitions.size() > 0) {
                int part = Utils.toPositive(nextValue) % availablePartitions.size();
                return availablePartitions.get(part).partition();
            } else {
                // no partitions are available, give a non-available partition
                // 如果没有可用分区，那么就给一个不可用的分区
                return Utils.toPositive(nextValue) % numPartitions;
            }
        } else {
            // hash the keyBytes to choose a partition
            // 如果设置了key，那么使用kafka的hash算法计算分区
            return Utils.toPositive(Utils.murmur2(keyBytes)) % numPartitions;
        }
    }

    private int nextValue(String topic) {
        // 获得这个分区的计数器
        AtomicInteger counter = topicCounterMap.get(topic);
        if (null == counter) {
            // 生成一个随机数
            // 意味着是从一个随机的分区开始的
            counter = new AtomicInteger(ThreadLocalRandom.current().nextInt());
            AtomicInteger currentCounter = topicCounterMap.putIfAbsent(topic, counter);
            if (currentCounter != null) {
                counter = currentCounter;
            }
        }
        return counter.getAndIncrement();
    }

    public void close() {}

}
```
### 自定义分区器
#### 定义
```java
public class MyPartitioner implements Partitioner {

    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value,
                         byte[] valueBytes, Cluster cluster) {
        List<PartitionInfo> partitionInfos = cluster.partitionsForTopic(topic);
        int numPartitions = partitionInfos.size();

        return (Utils.toPositive(Utils.murmur2(keyBytes)) % numPartitions);
    }

    @Override
    public void close() {
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }
}
```
#### 使用
```java
public class SimpleKafKaProducerPartition {

    public static void main(String[] args) {

        final Map<String, Object> config = ImmutableMap.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer",
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer",

                // 注意这里
                ProducerConfig.PARTITIONER_CLASS_CONFIG,
                "com.lk.producer.MyPartitioner"
        );

        try (val producer = new KafkaProducer<String, String>(config)) {
            for (int i = 0; i < 3; i++) {
                val record = new ProducerRecord<String, String>("topic01", "key", "value" + i);
                producer.send(record).get();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```
# 消费者
## 简单的使用
```java
public class SimpleConsumer {

    public static void main(String[] args) {
        val consumer = new KafkaConsumer<String, String>(ImmutableMap.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer",
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer",
                ConsumerConfig.GROUP_ID_CONFIG, "group01"
        ));
        consumer.subscribe(ImmutableList.of("topic01"));

        try {
            while (true) {
                val records = consumer.poll(Duration.ofMillis(100));
                for (val record : records) {
                    String str = "offset: " +
                            record.offset() +
                            " ,key: " +
                            record.key() +
                            " ,value: " +
                            record.value();
                    System.out.println(str);

                }
            }
        } finally {
            consumer.close();
        }
    }
}
```
## 再均衡和offset提交
```java
public class OffsetCommitConsumer {

    private static Map<TopicPartition, OffsetAndMetadata> currentOffset = new HashMap<>();

    public static void main(String[] args) {
        val consumer = new KafkaConsumer<String, String>(ImmutableMap.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer",
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer",
                ConsumerConfig.GROUP_ID_CONFIG, "group01"
        ));
        consumer.subscribe(ImmutableList.of("topic01"), new ConsumerRebalanceListener() {

            @Override
            // 再均衡之前，停止读取消息之后
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            	System.out.println("onPartitionsRevoked");
                consumer.commitSync(currentOffset);
            }

            @Override
            // 被重新分配分区之后（再均衡之后），开始读取消息之前调用
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                System.out.println("onPartitionsAssigned");
            }
        });

        try {
            while (true) {
                val records = consumer.poll(Duration.ofMillis(100));
                for (val record : records) {
                    String str = "offset: " +
                            record.offset() +
                            " ,key: " +
                            record.key() +
                            " ,value: " +
                            record.value();
                    System.out.println(str);

                    currentOffset.put(new TopicPartition(record.topic(), record.partition()),
                            new OffsetAndMetadata(record.offset() + 1, "no data"));
                    
                }
                // 这个是异步的提交，不会阻塞方法
                // 也可以记录下处理的条数，当达到多少后，就提交，而不是全部处理完之后才提交
                consumer.commitAsync(currentOffset, null);
            }
        } finally {
            try {
                consumer.commitSync();
            } finally {
                consumer.close();
            }
        }
    }
}
```
## 安全的退出
```java
public class SafeShutdown {

    public static void main(String[] args) {
        val consumer = new KafkaConsumer<String, String>(ImmutableMap.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer",
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer",
                ConsumerConfig.GROUP_ID_CONFIG, "group01"
        ));
        consumer.subscribe(ImmutableList.of("topic01"));

        val mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("exit ...");
            // 触发wakeup
            consumer.wakeup();
            try {
                mainThread.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        try {
            while (true) {
                val records = consumer.poll(Duration.ofMillis(100));
                for (val record : records) {
                    String str = "offset: " +
                            record.offset() +
                            " ,key: " +
                            record.key() +
                            " ,value: " +
                            record.value();
                    System.out.println(str);
                }
            }
        } catch (WakeupException ignored) {
            // 如果是WakeupException，那么说明是退出
        } finally {
            consumer.close();
        }
    }
}
```