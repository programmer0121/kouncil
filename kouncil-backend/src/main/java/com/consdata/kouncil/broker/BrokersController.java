package com.consdata.kouncil.broker;

import com.consdata.kouncil.KafkaConnectionService;
import com.consdata.kouncil.KouncilRuntimeException;
import com.consdata.kouncil.logging.EntryExitLogger;
import lombok.AllArgsConstructor;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeConfigsResult;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@AllArgsConstructor
public class BrokersController {

    private final KafkaConnectionService kafkaConnectionService;

    @GetMapping("/api/brokers")
    @EntryExitLogger
    public BrokersDto getBrokers() {
        try {
            String serverId = "kouncil_consdata_local_8001"; //TODO: JG
            DescribeClusterResult describeClusterResult = kafkaConnectionService.getAdminClient(serverId).describeCluster();
            Collection<Node> nodes = describeClusterResult.nodes().get();
            List<Broker> brokers = new ArrayList<>();
            nodes.forEach(node -> brokers.add(Broker.builder()
                    .host(node.host())
                    .port(node.port())
                    .port(node.port())
                    .id(node.idString())
                    .rack(node.rack())
                    .build()));
            Collections.sort(brokers);
            return BrokersDto.builder().brokers(brokers).build();
        } catch (Exception e) {
            throw new KouncilRuntimeException(e);
        }
    }

    @GetMapping("/api/configs/{name}")
    @EntryExitLogger
    public Collection<BrokerConfig> getConfigs(@PathVariable("name") String name) {
        try {
            String serverId = "kouncil_consdata_local_8001"; //TODO: JG
            ConfigResource o = new ConfigResource(ConfigResource.Type.BROKER, name);
            Collection<ConfigResource> resources = Collections.singletonList(o);
            DescribeConfigsResult describeClusterResult = kafkaConnectionService.getAdminClient(serverId).describeConfigs(resources);
            KafkaFuture<Config> nodes = describeClusterResult.values().get(o);
            Collection<ConfigEntry> entries = nodes.get().entries();
            List<BrokerConfig> configs = new ArrayList<>();
            entries.forEach(e -> configs.add(BrokerConfig.builder()
                    .name(e.name())
                    .source(e.source())
                    .value(e.value())
                    .isReadOnly(e.isReadOnly())
                    .isSensitive(e.isSensitive())
                    .build()));
            return configs.stream().sorted().collect(Collectors.toList());
        } catch (Exception e) {
            throw new KouncilRuntimeException(e);
        }
    }
}
