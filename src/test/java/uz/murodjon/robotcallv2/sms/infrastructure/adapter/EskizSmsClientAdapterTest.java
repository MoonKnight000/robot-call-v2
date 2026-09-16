package uz.murodjon.robotcallv2.sms.infrastructure.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.sms.domain.entity.SmsMessage;
import uz.murodjon.robotcallv2.sms.infrastructure.config.SmsEskizProperties;
import uz.murodjon.robotcallv2.sms.infrastructure.config.SmsProperties;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The part of the Eskiz contract that can be checked without an Eskiz account: one login
 * per token, one fresh login when the token is refused, and nothing counted as sent unless
 * Eskiz said it took the message.
 */
class EskizSmsClientAdapterTest {

    private static final SmsProperties CONFIGURED = new SmsProperties(true,
            new SmsEskizProperties("ops@example.uz", "secret", null, null), null);
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String SEND_PATH = "/api/message/sms/send";

    private final Deque<HttpResponse<String>> responses = new ArrayDeque<>();
    private final List<HttpRequest> requests = new ArrayList<>();
    private HttpClient httpClient;

    @BeforeEach
    void setUp() throws Exception {
        httpClient = mock(HttpClient.class);
        when(httpClient.send(any(), any())).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            return responses.removeFirst();
        });
    }

    @Test
    void logsInOnceAndReusesTheToken() {
        answer(200, "{\"message\":\"token_generated\",\"data\":{\"token\":\"t1\"}}");
        answer(200, "{\"id\":\"a\",\"status\":\"waiting\"}");
        answer(200, "{\"id\":\"b\",\"status\":\"waiting\"}");
        EskizSmsClientAdapter adapter = new EskizSmsClientAdapter(CONFIGURED, httpClient);

        assertThat(adapter.send(sms())).isTrue();
        assertThat(adapter.send(sms())).isTrue();

        assertThat(requests).extracting(request -> request.uri().getPath())
                .containsExactly(LOGIN_PATH, SEND_PATH, SEND_PATH);
        assertThat(requests.get(2).headers().firstValue("Authorization")).hasValue("Bearer t1");
    }

    @Test
    void logsInAgainOnceWhenTheTokenIsRefused() {
        answer(200, "{\"data\":{\"token\":\"t1\"}}");
        answer(401, "{\"status\":\"token-invalid\"}");
        answer(200, "{\"data\":{\"token\":\"t2\"}}");
        answer(200, "{\"id\":\"a\",\"status\":\"success\"}");
        EskizSmsClientAdapter adapter = new EskizSmsClientAdapter(CONFIGURED, httpClient);

        assertThat(adapter.send(sms())).isTrue();

        assertThat(requests).extracting(request -> request.uri().getPath())
                .containsExactly(LOGIN_PATH, SEND_PATH, LOGIN_PATH, SEND_PATH);
        assertThat(requests.get(3).headers().firstValue("Authorization")).hasValue("Bearer t2");
    }

    @Test
    void givesUpWhenTheRenewedTokenIsRefusedToo() {
        answer(200, "{\"data\":{\"token\":\"t1\"}}");
        answer(401, "{\"status\":\"token-invalid\"}");
        answer(200, "{\"data\":{\"token\":\"t2\"}}");
        answer(401, "{\"status\":\"token-invalid\"}");
        EskizSmsClientAdapter adapter = new EskizSmsClientAdapter(CONFIGURED, httpClient);

        assertThat(adapter.send(sms())).isFalse();

        // One renewal per send, not a loop against a gateway that keeps saying no.
        assertThat(requests).hasSize(4);
    }

    @Test
    void doesNotCountAnUnacceptedStatusAsSent() {
        answer(200, "{\"data\":{\"token\":\"t1\"}}");
        answer(200, "{\"status\":\"error\",\"message\":\"Invalid template\"}");
        EskizSmsClientAdapter adapter = new EskizSmsClientAdapter(CONFIGURED, httpClient);

        assertThat(adapter.send(sms())).isFalse();
    }

    @Test
    void sendsToTheDigitsOnlyNumberFromTheConfiguredSender() {
        answer(200, "{\"data\":{\"token\":\"t1\"}}");
        answer(200, "{\"status\":\"waiting\"}");
        EskizSmsClientAdapter adapter = new EskizSmsClientAdapter(CONFIGURED, httpClient);

        adapter.send(new SmsMessage("+998 90 123-45-67", "Salom"));

        assertThat(requests.get(1).uri()).isEqualTo(URI.create("https://notify.eskiz.uz" + SEND_PATH));
        assertThat(requests.get(1).headers().firstValue("Content-Type"))
                .hasValue("application/x-www-form-urlencoded");
    }

    @Test
    void sendsNothingWithoutCredentials() {
        EskizSmsClientAdapter adapter = new EskizSmsClientAdapter(new SmsProperties(true, null, null), httpClient);

        assertThat(adapter.send(sms())).isFalse();
        assertThat(requests).isEmpty();
    }

    @Test
    void sendsNothingToANumberOutsideUzbekistan() {
        EskizSmsClientAdapter adapter = new EskizSmsClientAdapter(CONFIGURED, httpClient);

        assertThat(adapter.send(new SmsMessage("+79161234567", "Salom"))).isFalse();
        assertThat(requests).isEmpty();
    }

    private static SmsMessage sms() {
        return new SmsMessage("+998901234567", "Salom");
    }

    @SuppressWarnings("unchecked")
    private void answer(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        responses.add(response);
    }
}
