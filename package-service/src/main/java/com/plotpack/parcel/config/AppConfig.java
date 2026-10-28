package com.plotpack.parcel.config;

import com.plotpack.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AppConfig {

    @Bean
    RestClient userRestClient(@Value("${plotpack.user-service-url}") String url) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(2));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    /** Runs the user-service call and the free-bin lookup in parallel; virtual threads make blocking I/O cheap. */
    @Bean(destroyMethod = "close")
    ExecutorService lookupExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    NewTopic packageEventsTopic() {
        return TopicBuilder.name(Topics.PACKAGE_EVENTS).partitions(3).replicas(1).build();
    }
}
