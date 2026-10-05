package uz.murodjon.robotcallv2.crm.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmCallHistoryEntry;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClient;
import uz.murodjon.robotcallv2.shared.http.PublicHttpRequest;
import uz.murodjon.robotcallv2.shared.http.PublicHttpResponse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AmoCrmConnectorAdapterTest {

    private static final String PHONE = "+998901234567";

    private PublicHttpClient httpClient;
    private AmoCrmConnectorAdapter adapter;

    @BeforeEach
    void setUp() {
        httpClient = mock(PublicHttpClient.class);
        adapter = new AmoCrmConnectorAdapter(httpClient, new ObjectMapper());
    }

    @Test
    void accountAddressInsideTheNetworkIsNeverCalled() {
        for (String address : List.of("http://127.0.0.1:8080", "https://10.0.0.5", "https://169.254.169.254",
                "https://[::1]")) {
            CrmConnection connection = new CrmConnection(CrmProvider.AMOCRM, address, "token");

            assertNull(adapter.findByPhone(connection, PHONE));
            assertNull(adapter.postNote(connection, 42L, "note"));
            assertFalse(adapter.postCallHistory(connection, entry()));
        }
        verifyNoInteractions(httpClient);
    }

    @Test
    void publicAccountIsSearchedAtItsRootWithTheBearerToken() throws Exception {
        when(httpClient.send(any(PublicHttpRequest.class))).thenReturn(new PublicHttpResponse(200,
                "{\"_embedded\":{\"contacts\":[{\"id\":5,\"name\":\"Ali Valiyev\"}]}}".getBytes(StandardCharsets.UTF_8),
                false));

        CrmClientSnapshot snapshot = adapter.findByPhone(
                new CrmConnection(CrmProvider.KOMMO, "https://203.0.113.10/leads/pipeline", "token"), PHONE);

        assertEquals("Ali Valiyev", snapshot.name());
        ArgumentCaptor<PublicHttpRequest> request = ArgumentCaptor.forClass(PublicHttpRequest.class);
        verify(httpClient).send(request.capture());
        assertEquals("GET", request.getValue().getMethod());
        assertEquals("https://203.0.113.10/api/v4/contacts?query=%2B998901234567&with=leads",
                request.getValue().getUri().toString());
        assertEquals(Optional.of("Bearer token"), request.getValue().findHeader("Authorization"));
    }

    private static CrmCallHistoryEntry entry() {
        return new CrmCallHistoryEntry(42L, "voice-1", Instant.parse("2026-09-13T10:00:00Z"), 30,
                false, true, PHONE, null);
    }
}
