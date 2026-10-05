package uz.murodjon.robotcallv2.crm.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmCallHistoryEntry;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.crm.infrastructure.config.CrmProperties;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Against a local stand-in for the Uysot Open API — the connector builds its own HTTP client. */
class UysotConnectorAdapterTest {

    private static final String PHONE = "+998 90 123-45-67";

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile String lastBody;
    private volatile String lastToken;
    private volatile String lastAuthorization;
    private volatile int status = 200;
    private volatile String responseBody = "{}";
    private HttpServer server;
    private boolean serverStopped;
    private CrmConnection connection;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastToken = exchange.getRequestHeaders().getFirst("X-Open-Api-Token");
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        connection = new CrmConnection(CrmProvider.UYSOT,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/open-api", "company-token");
    }

    @AfterEach
    void tearDown() {
        if (!serverStopped) {
            server.stop(0);
        }
    }

    @Test
    void leadIsReadOutOfTheEnvelopeWithTheOpenApiToken() {
        responseBody = "{\"data\":{\"name\":\"Ali Valiyev\",\"debtAmount\":\"1500000\"},\"requestId\":null}";

        CrmClientSnapshot lead = adapter(12).findLead(connection, 5L);

        assertEquals("Ali Valiyev", lead.name());
        assertEquals(new BigDecimal("1500000"), lead.debtAmount());
        assertEquals(List.of("GET /v1/open-api/lead/5"), requests);
        assertEquals("company-token", lastToken);
        assertNull(lastAuthorization, "the Open API ignores a bearer token");
    }

    @Test
    void callerIsSearchedByTheLastNineDigitsOfTheNumber() {
        responseBody = "{\"data\":{\"data\":[{\"name\":\"Ali Valiyev\"}]}}";

        CrmClientSnapshot caller = adapter(12).findByPhone(connection, PHONE);

        assertEquals("Ali Valiyev", caller.name());
        assertEquals(List.of("POST /v1/open-api/lead/filter"), requests);
        assertTrue(lastBody.contains("\"phone\":\"901234567\""), lastBody);
    }

    @Test
    void numberNoLeadHasIsNoCaller() {
        responseBody = "{\"data\":{\"data\":[]}}";

        assertNull(adapter(12).findByPhone(connection, PHONE));
    }

    @Test
    void noteIsTakenUnderItsAsyncRequestId() {
        responseBody = "{\"data\":null,\"requestId\":\"req-1\"}";

        assertEquals("req-1", adapter(12).postNote(connection, 42L, "Mijoz ertaga to'laydi."));
        assertEquals(List.of("POST /v1/open-api/lead/42/note"), requests);
        assertTrue(lastBody.startsWith("[{"), "the endpoint takes an array of notes: " + lastBody);
        assertTrue(lastBody.contains("\"text\":\"Mijoz ertaga to'laydi.\""), lastBody);
    }

    /** A 2xx without a request id means the write was not enqueued; calling it posted would lose the note. */
    @Test
    void noteAnsweredWithoutARequestIdIsNotTaken() {
        responseBody = "{\"data\":null,\"message\":\"ok\"}";

        assertNull(adapter(12).postNote(connection, 42L, "note"));
    }

    @Test
    void callHistoryIsNotSentWithoutAnEmployeeToFileItUnder() {
        assertFalse(adapter(null).postCallHistory(connection, entry(0)));
        assertTrue(requests.isEmpty());
    }

    @Test
    void callThatNeverConnectedIsRecordedAsOneOutboundSecond() {
        assertTrue(adapter(12).postCallHistory(connection, entry(0)));

        assertEquals(List.of("POST /v1/open-api/call-history"), requests);
        assertTrue(lastBody.contains("\"uuid\":\"voice-55\""), lastBody);
        assertTrue(lastBody.contains("\"employeeId\":12"), lastBody);
        assertTrue(lastBody.contains("\"durationSec\":1"), lastBody);
        assertTrue(lastBody.contains("\"direction\":\"OUTBOUND\""), lastBody);
        assertFalse(lastBody.contains("recordUrl"), lastBody);
    }

    @Test
    void refusingOrUnreachableCrmAnswersNothing() {
        status = 500;
        responseBody = "{\"error\":\"boom\"}";
        assertNull(adapter(12).findLead(connection, 5L));
        assertNull(adapter(12).postNote(connection, 42L, "note"));
        assertFalse(adapter(12).postCallHistory(connection, entry(30)));

        server.stop(0);
        serverStopped = true;
        assertNull(adapter(12).findByPhone(connection, PHONE));
    }

    private static UysotConnectorAdapter adapter(Integer callHistoryEmployeeId) {
        return new UysotConnectorAdapter(new CrmProperties(true, "https://unused.example", "platform-token",
                "/lead/{id}", "/lead/filter", "/lead/{id}/note", "/contract/{id}", "/contract/filter",
                "/call-history", callHistoryEmployeeId), new ObjectMapper());
    }

    private static CrmCallHistoryEntry entry(int durationSeconds) {
        return new CrmCallHistoryEntry(42L, "voice-55", Instant.parse("2026-09-14T09:58:00Z"), durationSeconds,
                false, false, "+998901234567", null);
    }
}
