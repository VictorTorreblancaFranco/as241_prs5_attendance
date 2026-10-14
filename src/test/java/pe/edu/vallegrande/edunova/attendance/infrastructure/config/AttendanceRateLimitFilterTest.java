package pe.edu.vallegrande.edunova.attendance.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceRateLimitFilterTest {

    @Test
    void rejectsAttendanceRequestsWhenMinuteLimitIsExceeded() {
        AttendanceRateLimitFilter filter = new AttendanceRateLimitFilter(
                Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "requestsPerMinute", 1);
        AtomicInteger chainCalls = new AtomicInteger();
        WebFilterChain chain = exchange -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        };

        MockServerWebExchange firstRequest = exchange("/api/attendance/class/1");
        MockServerWebExchange secondRequest = exchange("/api/attendance/class/1");

        StepVerifier.create(filter.filter(firstRequest, chain)).verifyComplete();
        StepVerifier.create(filter.filter(secondRequest, chain)).verifyComplete();

        assertThat(chainCalls).hasValue(1);
        assertThat(secondRequest.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void ignoresRoutesOutsideAttendance() {
        AttendanceRateLimitFilter filter = new AttendanceRateLimitFilter(
                Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "requestsPerMinute", 0);
        AtomicInteger chainCalls = new AtomicInteger();
        WebFilterChain chain = exchange -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange("/actuator/health"), chain)).verifyComplete();

        assertThat(chainCalls).hasValue(1);
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest
                .get(path)
                .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 8080)));
    }
}
