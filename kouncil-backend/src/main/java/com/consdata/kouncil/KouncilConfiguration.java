package com.consdata.kouncil;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.trace.http.HttpTraceRepository;
import org.springframework.boot.actuate.trace.http.InMemoryHttpTraceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
@Data
public class KouncilConfiguration {

    @Value("${bootstrapServers}")
    private List<String> initialBootstrapServers;

    private Map<String, String> servers;

    public String getServerById(String serverId) {
        return servers.get(serverId);
    }

    @PostConstruct
    public void log() {
        servers = initialBootstrapServers.stream()
                .collect(Collectors.toMap(s -> s.replaceAll("[^a-zA-Z0-9\\s]", "_"), s -> s));
        log.info(toString());
    }

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.any())
                .paths(PathSelectors.any())
                .build();
    }

    @Bean
    public HttpTraceRepository httpTraceRepository() {
        return new InMemoryHttpTraceRepository();
    }

}
