package uz.murodjon.robotcallv2.crm.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmCallHistoryEntry;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClient;
import uz.murodjon.robotcallv2.shared.http.PublicHttpRequest;
import uz.murodjon.robotcallv2.shared.http.PublicHttpResponse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class Bitrix24ConnectorAdapterTest {

    private static final String PHONE = "+998901234567";

    private PublicHttpClient httpClient;
    private Bitrix24ConnectorAdapter adapter;

    @BeforeEach
    void setUp() {
        httpClient = mock(PublicHttpClient.class);
        adapter = new Bitrix24ConnectorAdapter(httpClient, new ObjectMapper());
    }

    @Test
    void webhookInsideTheNetworkIsNeverCalled() {
        for (String webhook : List.of("https://192.168.1.20/rest/1/code/", "http://localhost/rest/1/code",
                "https://172.17.0.1/rest/1/code")) {
            CrmConnection connection = new CrmConnection(CrmProvider.BITRIX24, webhook, null);

            assertNull(adapter.findByPhone(connection, PHONE));
            assertNull(adapter.postNote(connection, 42L, "note"));
            assertFalse(adapter.postCallHistory(connection, entry()));
        }
        verifyNoInteractions(httpClient);
    }

    @Test
    void callIsRegisteredThenFinishedThroughTheWebhook() throws Exception {
        when(httpClient.send(any(PublicHttpRequest.class))).thenReturn(
                ok("{\"result\":{\"CALL_ID\":\"externalCall.abc\"}}"),
                ok("{\"result\":{}}"));

        assertTrue(adapter.postCallHistory(
                new CrmConnection(CrmProvider.BITRIX24, "https://203.0.113.10/rest/12/code/", null), entry()));

        ArgumentCaptor<PublicHttpRequest> requests = ArgumentCaptor.forClass(PublicHttpRequest.class);
        verify(httpClient, times(2)).send(requests.capture());
        assertEquals("https://203.0.113.10/rest/12/code/telephony.externalcall.register.json",
                requests.getAllValues().get(0).getUri().toString());
        assertEquals("https://203.0.113.10/rest/12/code/telephony.externalcall.finish.json",
                requests.getAllValues().get(1).getUri().toString());
    }

    private static PublicHttpResponse ok(String body) {
        return new PublicHttpResponse(200, body.getBytes(StandardCharsets.UTF_8), false);
    }

    private static CrmCallHistoryEntry entry() {
        return new CrmCallHistoryEntry(42L, "voice-1", Instant.parse("2026-09-13T10:00:00Z"), 30,
                false, true, PHONE, null);
    }
}
