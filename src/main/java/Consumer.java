import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Consumer {

  private static final Logger LOG = LoggerFactory.getLogger(Consumer.class);
  private final KafkaConsumer<String, String> consumer;
  private final HashMap<TopicPartition, OffsetAndMetadata> lastOffsets = new HashMap<>();
  private final HashMap<TopicPartition, OffsetAndMetadata> firstOffsets = new HashMap<>();
  private final RedisClient redisClient;
  private final StatefulRedisConnection<String, String> connection;

  public static void main(String[] args) {
    new Consumer().run();
  }

  public Consumer() {
    Map<String, Object> map = new HashMap<String, Object>();
    map.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    map.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
    map.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    map.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    map.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

    this.consumer = new KafkaConsumer<String, String>(map);
    this.consumer.subscribe(Arrays.asList("test"));
    this.redisClient = RedisClient.create(RedisURI.builder().withHost("localhost").withPort(6379).build());
    this.connection = redisClient.connect();
  }

  public void run() {
    try {
      while (true) {
        ConsumerRecords<String, String> records = consumer.poll(100);
        for (ConsumerRecord<String, String> record : records) {
          LOG.info("Partition is {} and offset is {}", record.partition(), record.offset());
          var tp = new TopicPartition(record.topic(), (record.partition()));
          if (!firstOffsets.containsKey(tp)) {
            firstOffsets.put(tp, new OffsetAndMetadata(record.offset()));
            LOG.info("Began processing partition {} at offset {}", record.partition(),
                record.offset());
          }

          var prev = Integer.parseInt(connection.sync().get("test-group:" + record.partition()));
          LOG.info("Fetched from redis test-group:{} previosuly commited offset {}",
              record.partition(), prev);

          if (prev >= record.offset() && !(record.offset() == 0)) {
            throw new RuntimeException(
                "REPROCESSING ERROR. Tried to process " + record.partition() + "-" + record.offset()
                    + ", but already processed " + prev);
          }

          connection.sync()
              .set("test-group:" + record.partition(), String.valueOf(record.offset()));
          lastOffsets.put(tp, new OffsetAndMetadata(record.offset()));

        }
      }
    } catch (Exception e) {
      LOG.error("Something went wrong", e);
    }

  }

}
