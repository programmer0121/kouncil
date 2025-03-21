package com.consdata.kouncil.track;

import com.consdata.kouncil.AbstractMessagesController;
import com.consdata.kouncil.KafkaConnectionService;
import com.consdata.kouncil.topic.TopicMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@RestController
@SuppressWarnings("java:S6212") //val
public class TrackController extends AbstractMessagesController {

    private final SimpMessagingTemplate eventSender;

    private final ExecutorService executor;

    private final WebSocketMessageBrokerStats webSocketMessageBrokerStats;

    private final DestinationStore destinationStore;

    private final EventMatcher eventMatcher;

    public TrackController(KafkaConnectionService kafkaConnectionService, SimpMessagingTemplate eventSender, ExecutorService executor, WebSocketMessageBrokerStats webSocketMessageBrokerStats, DestinationStore destinationStore, EventMatcher eventMatcher) {
        super(kafkaConnectionService);
        this.eventSender = eventSender;
        this.executor = executor;
        this.webSocketMessageBrokerStats = webSocketMessageBrokerStats;
        this.destinationStore = destinationStore;
        this.eventMatcher = eventMatcher;
    }

    @GetMapping("/api/track/stats")
    public String printStats() throws JsonProcessingException {
        WebSocketStats wss = WebSocketStats.builder()
                .wsSession(webSocketMessageBrokerStats.getWebSocketSessionStatsInfo())
                .taskScheduler(webSocketMessageBrokerStats.getSockJsTaskSchedulerStatsInfo())
                .clientInbound(webSocketMessageBrokerStats.getClientInboundExecutorStatsInfo())
                .clientOutbound(webSocketMessageBrokerStats.getClientOutboundExecutorStatsInfo())
                .destinations(destinationStore.getActiveDestinations())
                .build();
        String result = new ObjectMapper().writeValueAsString(wss);
        log.debug(result);
        return result;
    }

    @GetMapping("/api/track/sync")
    public List<TopicMessage> getSync(@RequestParam("topicNames") List<String> topicNames,
                                      @RequestParam("field") String field,
                                      @RequestParam("operator") String operatorParam,
                                      @RequestParam("value") String value,
                                      @RequestParam("beginningTimestampMillis") Long beginningTimestampMillis,
                                      @RequestParam("endTimestampMillis") Long endTimestampMillis,
                                      @RequestParam("serverId") String serverId) {
        return getEvents(topicNames, field, operatorParam, value, beginningTimestampMillis, endTimestampMillis, serverId, new SyncTrackStrategy());
    }

    @GetMapping("/api/track/async")
    public void getAsync(@RequestParam("topicNames") List<String> topicNames,
                         @RequestParam("field") String field,
                         @RequestParam("operator") String operatorParam,
                         @RequestParam("value") String value,
                         @RequestParam("beginningTimestampMillis") Long beginningTimestampMillis,
                         @RequestParam("endTimestampMillis") Long endTimestampMillis,
                         @RequestParam("serverId") String serverId,
                         @RequestParam("asyncHandle") String asyncHandle) {
        executor.submit(() -> getEvents(topicNames, field, operatorParam, value, beginningTimestampMillis, endTimestampMillis, serverId, new AsyncTrackStrategy("/topic/track/" + asyncHandle, eventSender, destinationStore)));
    }

    private List<TopicMessage> getEvents(List<String> topicNames, String field, String operatorParam, String value, Long beginningTimestampMillis, Long endTimestampMillis, String serverId, TrackStrategy trackStrategy) {
        log.debug("TRACK01 topicNames={}, field={}, operator={}, value={}, beginningTimestampMillis={}, endTimestampMillis={}, serverId={}",
                topicNames, field, operatorParam, value, beginningTimestampMillis, endTimestampMillis, serverId);
        TrackOperator trackOperator = TrackOperator.fromValue(operatorParam);
        validateTopics(serverId, topicNames);

        try (KafkaConsumer<String, String> consumer = kafkaConnectionService.getKafkaConsumer(serverId, 5000)) {
            List<TopicMetadata> metadataList = prepareMetadata(topicNames, beginningTimestampMillis, endTimestampMillis, consumer);
            metadataList.sort(Comparator.comparing(TopicMetadata::getAllPartitionRangeSize));
            log.debug("TRACK20 metadata={}", metadataList);

            for (TopicMetadata m : metadataList) {
                Boolean[] exhausted = positionConsumer(consumer, m);
                long startTime = System.nanoTime();
                int emptyPolls = 0;
                while (emptyPolls < 5 && Arrays.stream(exhausted).anyMatch(x -> !x)) {
                    if (trackStrategy.shouldStopTracking()) {
                        return trackStrategy.processFinalResult();
                    }
                    List<TopicMessage> candidates = new ArrayList<>();
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100L * (emptyPolls + 1)));
                    if (records.isEmpty()) {
                        emptyPolls++;
                    } else {
                        emptyPolls = 0;
                    }
                    log.debug("TRACK70 topic={} poll took={}ms, returned {} records", m.getTopicName(), (System.nanoTime() - startTime) / 1000000, records.count());
                    for (ConsumerRecord<String, String> consumerRecord : records) {
                        if (consumerRecord.offset() >= m.getEndOffsets().get(consumerRecord.partition())) {
                            if (Boolean.FALSE.equals(exhausted[consumerRecord.partition()])) {
                                log.debug("TRACK24 topic={}, partition={} exhausted", m.getTopicName(), consumerRecord.partition());
                                exhausted[consumerRecord.partition()] = true;
                            }
                            continue;
                        }
                        if (eventMatcher.filterMatch(field, trackOperator, value, consumerRecord)) {
                            candidates.add(TopicMessage
                                    .builder()
                                    .topic(m.getTopicName())
                                    .key(consumerRecord.key())
                                    .value(consumerRecord.value())
                                    .offset(consumerRecord.offset())
                                    .partition(consumerRecord.partition())
                                    .timestamp(consumerRecord.timestamp())
                                    .headers(mapHeaders(consumerRecord.headers()))
                                    .build());
                        }
                    }
                    log.debug("TRACK90 topic={}, poll completed candidates.size={}", m.getTopicName(), candidates.size());
                    trackStrategy.processCandidates(candidates);
                }
            }

            return trackStrategy.processFinalResult();
        }
    }

    private Boolean[] positionConsumer(KafkaConsumer<String, String> consumer, TopicMetadata m) {
        consumer.assign(m.getPartitions().values());
        Boolean[] exhausted = new Boolean[m.getPartitions().size()];
        Arrays.fill(exhausted, Boolean.FALSE);
        for (Map.Entry<Integer, TopicPartition> entry : m.getPartitions().entrySet()) {
            Integer partitionIndex = entry.getKey();
            Long startOffsetForPartition = m.getBeginningOffsets().get(partitionIndex);
            log.debug("TRACK50 topic={}, partition={}, startOffsetForPartition={}", m.getTopicName(), partitionIndex, startOffsetForPartition);
            if (startOffsetForPartition < 0) {
                log.debug("TRACK51 topic={}, partition={} startOffsetForPartition is -1, seekToEnd", m.getTopicName(), partitionIndex);
                consumer.seekToEnd(Collections.singletonList(m.getPartitions().get(partitionIndex)));
                exhausted[partitionIndex] = true;
            } else {
                log.debug("TRACK52 topic={}, partition={}, startOffsetForPartition={}", m.getTopicName(), partitionIndex, startOffsetForPartition);
                consumer.seek(m.getPartitions().get(partitionIndex), startOffsetForPartition);
            }
        }
        return exhausted;
    }

    private List<TopicMetadata> prepareMetadata(List<String> topicNames, Long beginningTimestampMillis, Long endTimestampMillis, KafkaConsumer<String, String> consumer) {
        List<TopicMetadata> metadataList = new ArrayList<>();
        for (String t : topicNames) {
            Map<Integer, TopicPartition> partitions = IntStream.rangeClosed(0, consumer.partitionsFor(t).size() - 1).boxed().collect(Collectors.toMap(Function.identity(), p -> new TopicPartition(t, p)));

            consumer.assign(partitions.values());

            Map<Integer, Long> beginningOffsets = calculateBeginningOffsets(beginningTimestampMillis, null, consumer, partitions.values());
            Map<Integer, Long> endOffsets = calculateEndOffsets(endTimestampMillis, null, consumer, partitions.values());
            metadataList.add(TopicMetadata.builder()
                    .topicName(t)
                    .partitions(partitions)
                    .beginningOffsets(beginningOffsets)
                    .endOffsets(endOffsets)
                    .build());
        }
        return metadataList;
    }

}
