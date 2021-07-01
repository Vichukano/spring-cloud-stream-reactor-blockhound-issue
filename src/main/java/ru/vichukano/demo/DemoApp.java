package ru.vichukano.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.blockhound.BlockHound;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.function.Function;

@RestController
@SpringBootApplication
public class DemoApp {
    private final static Logger LOGGER = LoggerFactory.getLogger(DemoApp.class);

    public static void main(String[] args) {
        BlockHound.install();
        SpringApplication.run(DemoApp.class, args);
    }

    @Bean
    public Function<Flux<String>, Flux<String>> process(WebClient webClient) {
        return flux -> flux
                .flatMap(f -> getBar(webClient, f))
                .map(String::toUpperCase)
                .doOnNext(next -> LOGGER.warn("Publish result {} in separate thread", next));
    }

    @PostMapping(path = "/bar")
    public String bar(@RequestBody String body) {
        return body + "bar";
    }

    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(1));
        return WebClient.builder()
                .baseUrl("http://localhost:8080/")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    private Mono<String> getBar(WebClient client, String body) {
        return client.post()
                .uri("/bar")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> LOGGER.warn("Receive response {} in netty thread", response))
                .publishOn(Schedulers.boundedElastic());
    }

}
