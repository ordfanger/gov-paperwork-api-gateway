package com.alm.research.gov.paperwork.apigateway.filters;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Component
public class AuthFilter extends AbstractGatewayFilterFactory<AuthFilter.Config> {

    private final WebClient.Builder webClientBuilder;

    @Value("${gov.paperwork.auth-validation-url}")
    private String authValidationUrl;

    public AuthFilter(WebClient.Builder webClientBuilder) {
        super(Config.class);

        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            String path = request.getPath().toString();

            //bypass allowed urls
            if (config.getAllowed().contains(path)) {
                return chain.filter(exchange);
            }

            //allow api-docs
            if (this.matchAPIDocsPath(path)) {
                return chain.filter(exchange);
            }

            return this.webClientBuilder
                    .baseUrl(authValidationUrl)
                    .build()
                    .post()
                    .headers(httpHeaders -> {
                        httpHeaders.putAll(request.getHeaders());
                    })
                    .exchange()
                    .flatMap(res -> {
                        ServerHttpResponse response = exchange.getResponse();

                        if (res.rawStatusCode() < HttpStatus.BAD_REQUEST.value()) {
                            return chain.filter(exchange);
                        }

                        response.setStatusCode(res.statusCode());
                        response.getHeaders().putAll(res.headers().asHttpHeaders());

                        Flux<DataBuffer> body = res.body(BodyExtractors.toDataBuffers());

                        return exchange.getResponse().writeWith(body);
                    });
        };
    }

    private boolean matchAPIDocsPath(String path) {
        List<PathPattern> pathPatterns = new ArrayList<>();
        List<String> apiDocsUrlPatters = List.of("/*/swagger-ui/**", "/*/v3/api-docs/**", "/*/api-docs/**", "/*/webjars/**");

        PathPatternParser pathPatternParser = new PathPatternParser();
        pathPatternParser.setMatchOptionalTrailingSeparator(true);

        apiDocsUrlPatters.forEach(apiDocsUrl -> pathPatterns.add(pathPatternParser.parse(apiDocsUrl)));

        return pathPatterns.stream().anyMatch(pathPattern -> {
            PathContainer pathContainer = PathContainer.parsePath(path);
            return pathPattern.matches(pathContainer);
        });
    }

    @Data
    @NoArgsConstructor
    public static class Config {
        private List<String> allowed;
    }
}
