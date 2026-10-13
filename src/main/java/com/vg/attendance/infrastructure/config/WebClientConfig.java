package com.vg.attendance.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
public class WebClientConfig {

    @Value("${schedule.service.url:http://vg-ms-schedule:5083}")
    private String scheduleServiceUrl;

    @Value("${enrollment.service.url:http://vg-ms-enrollment:5087}")
    private String enrollmentServiceUrl;

    @Value("${user.service.url:http://vg-ms-user:5082}")
    private String userServiceUrl;

    @Value("${course.service.url:http://vg-ms-course:5095}")
    private String courseServiceUrl;

    @Value("${academic.service.url:http://vg-ms-academic:5089}")
    private String academicServiceUrl;

    @Value("${services.comms.url:http://vg-ms-comms:5088}")
    private String commsServiceUrl;

    // Llamadas internas servicio→servicio: se autentican con los headers de
    // confianza que acepta el GatewayTrustWebFilter de cada servicio (mismo
    // patrón del gateway). Sin esto, schedule/enrollment/user rechazaban con
    // 401 los tokens que attendance reenvía río abajo.
    private static final String INTERNAL_REQUEST_HEADER = "X-Internal-Request";
    private static final String INTERNAL_REQUEST_VALUE = "gateway";

    @Value("${internal-secret:edunova-internal-trust}")
    private String internalSecret;

    private ExchangeFilterFunction propagateRequestContext() {
        return (request, next) -> Mono.deferContextual(context -> {
            ClientRequest.Builder builder = ClientRequest.from(request);
            if (internalSecret != null && !internalSecret.isBlank()) {
                builder.header("X-Internal-Secret", internalSecret);
            }
            replaceHeader(builder, "X-Tenant-Id", context, GatewayTrustWebFilter.TENANT_CONTEXT_KEY);
            replaceHeader(builder, "X-User-Id", context, GatewayTrustWebFilter.USER_ID_CONTEXT_KEY);
            replaceHeader(builder, "X-User-Role", context, GatewayTrustWebFilter.USER_ROLE_CONTEXT_KEY);
            replaceHeader(builder, "X-User-Email", context, GatewayTrustWebFilter.USER_EMAIL_CONTEXT_KEY);
            replaceHeader(builder, "Authorization", context, GatewayTrustWebFilter.AUTHORIZATION_CONTEXT_KEY);
            return next.exchange(builder.build());
        });
    }

    private void replaceHeader(ClientRequest.Builder builder,
                                String headerName,
                                reactor.util.context.ContextView context,
                                String contextKey) {
        String value = context.getOrDefault(contextKey, "");
        if (value == null || value.isBlank()) {
            return;
        }
        builder.headers(headers -> headers.remove(headerName));
        builder.header(headerName, value);
    }

    @Bean
    public WebClient scheduleWebClient() {
        return WebClient.builder()
                .baseUrl(scheduleServiceUrl)
                .defaultHeader(INTERNAL_REQUEST_HEADER, INTERNAL_REQUEST_VALUE)
                .filter(propagateRequestContext())
                .build();
    }

    @Bean
    public WebClient enrollmentWebClient() {
        return WebClient.builder()
                .baseUrl(enrollmentServiceUrl)
                .defaultHeader(INTERNAL_REQUEST_HEADER, INTERNAL_REQUEST_VALUE)
                .filter(propagateRequestContext())
                .build();
    }

    @Bean
    public WebClient userWebClient() {
        return WebClient.builder()
                .baseUrl(userServiceUrl)
                .defaultHeader(INTERNAL_REQUEST_HEADER, INTERNAL_REQUEST_VALUE)
                .filter(propagateRequestContext())
                .build();
    }

    @Bean
    public WebClient academicWebClient() {
        return WebClient.builder()
                .baseUrl(academicServiceUrl)
                .defaultHeader(INTERNAL_REQUEST_HEADER, INTERNAL_REQUEST_VALUE)
                .filter(propagateRequestContext())
                .build();
    }

    @Bean
    public WebClient courseWebClient() {
        return WebClient.builder()
                .baseUrl(courseServiceUrl)
                .defaultHeader(INTERNAL_REQUEST_HEADER, INTERNAL_REQUEST_VALUE)
                .filter(propagateRequestContext())
                .build();
    }

    @Bean
    public WebClient commsWebClient() {
        return WebClient.builder()
                .baseUrl(commsServiceUrl)
                .defaultHeader(INTERNAL_REQUEST_HEADER, INTERNAL_REQUEST_VALUE)
                .filter(propagateRequestContext())
                .build();
    }
}
