package com.vg.attendance.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

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

    @Value("${services.comms.url:http://vg-ms-comms:5088}")
    private String commsServiceUrl;

    // Llamadas internas servicio→servicio: se autentican con los headers de
    // confianza que acepta el GatewayTrustWebFilter de cada servicio (mismo
    // patrón del gateway). Sin esto, schedule/enrollment/user rechazaban con
    // 401 los tokens que attendance reenvía río abajo.
    private static final String INTERNAL_REQUEST_HEADER = "X-Internal-Request";
    private static final String INTERNAL_REQUEST_VALUE = "gateway";
    private static final String INTERNAL_USER_ID = "00000000-0000-0000-0000-000000000000";

    @Bean
    public WebClient scheduleWebClient() {
        return WebClient.builder()
                .baseUrl(scheduleServiceUrl)
                .defaultHeader(INTERNAL_REQUEST_HEADER, INTERNAL_REQUEST_VALUE)
                .defaultHeader("X-User-Id", INTERNAL_USER_ID)
                .defaultHeader("X-User-Role", "ADMIN")
                .build();
    }

    @Bean
    public WebClient enrollmentWebClient() {
        return WebClient.builder()
                .baseUrl(enrollmentServiceUrl)
                .defaultHeader(INTERNAL_REQUEST_HEADER, INTERNAL_REQUEST_VALUE)
                .defaultHeader("X-User-Id", INTERNAL_USER_ID)
                .defaultHeader("X-User-Role", "ADMIN")
                .build();
    }

    @Bean
    public WebClient userWebClient() {
        return WebClient.builder()
                .baseUrl(userServiceUrl)
                .defaultHeader(INTERNAL_REQUEST_HEADER, INTERNAL_REQUEST_VALUE)
                .defaultHeader("X-User-Id", INTERNAL_USER_ID)
                .defaultHeader("X-User-Role", "ADMIN")
                .build();
    }

    @Bean
    public WebClient academicWebClient() {
        return WebClient.builder()
                .baseUrl(courseServiceUrl)
                .defaultHeader(INTERNAL_REQUEST_HEADER, INTERNAL_REQUEST_VALUE)
                .defaultHeader("X-User-Id", INTERNAL_USER_ID)
                .defaultHeader("X-User-Role", "ADMIN")
                .build();
    }

    @Bean
    public WebClient commsWebClient() {
        return WebClient.builder()
                .baseUrl(commsServiceUrl)
                .defaultHeader(INTERNAL_REQUEST_HEADER, INTERNAL_REQUEST_VALUE)
                .defaultHeader("X-User-Id", INTERNAL_USER_ID)
                .defaultHeader("X-User-Role", "ADMIN")
                .build();
    }
}
