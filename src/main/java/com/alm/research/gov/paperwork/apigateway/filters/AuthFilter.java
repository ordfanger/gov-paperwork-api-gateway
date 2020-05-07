package com.alm.research.gov.paperwork.apigateway.filters;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AbstractNameValueGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RedirectToGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.style.ToStringCreator;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import javax.validation.constraints.NotEmpty;
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

            //bypass allowed urls
            if (config.getAllowed().contains(request.getPath().toString())) {
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

    @Data
    @NoArgsConstructor
    public static class Config {
        private List<String> allowed;
    }
}
