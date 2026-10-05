package uz.murodjon.robotcallv2.integration.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.integration.application.dto.ConnectCrmAccountRequest;
import uz.murodjon.robotcallv2.integration.application.dto.ConnectIntegrationRequest;
import uz.murodjon.robotcallv2.integration.application.dto.CrmGrant;
import uz.murodjon.robotcallv2.integration.application.dto.CrmIntegrationRow;
import uz.murodjon.robotcallv2.integration.application.port.output.CrmIntegrationRepository;
import uz.murodjon.robotcallv2.integration.application.port.output.OAuthStateStorePort;
import uz.murodjon.robotcallv2.integration.application.port.output.UysotOAuthPort;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmIntegration;
import uz.murodjon.robotcallv2.integration.domain.entity.OAuthTokenReply;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmIntegrationStatus;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmPermission;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmScopeLevel;
import uz.murodjon.robotcallv2.integration.infrastructure.config.UysotOAuthProperties;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrmIntegrationServiceTest {

    private static final long COMPANY_ID = 7L;
    private static final String REDIRECT_URI = "https://voice.example/api/settings/integrations/uysot/callback";
    private static final String SETTINGS_PAGE = "https://panel.example/settings";
    private static final UysotOAuthProperties UYSOT_OAUTH = new UysotOAuthProperties(
            "https://uysot.example/oauth/authorize", "https://uysot.example/oauth/token",
            "https://uysot.example/oauth/revoke", REDIRECT_URI, SETTINGS_PAGE, "voice-app", "client-secret");
    private static final String GRANTS_JSON =
            "[{\"permission\":\"LEAD\",\"scope\":\"READ\"},{\"permission\":\"LEAD_NOTE\",\"scope\":\"SAVE\"}]";

    private CrmIntegrationRepository repository;
    private SecretCipher cipher;
    private OAuthStateStorePort stateStore;
    private UysotOAuthPort uysotOAuthPort;
    private AuditUseCase audit;
    private CrmIntegrationService service;

    @BeforeEach
    void setUp() {
        repository = mock(CrmIntegrationRepository.class);
        cipher = mock(SecretCipher.class);
        stateStore = mock(OAuthStateStorePort.class);
        uysotOAuthPort = mock(UysotOAuthPort.class);
        audit = mock(AuditUseCase.class);
        when(cipher.available()).thenReturn(true);
        when(cipher.encrypt(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        when(cipher.decrypt(anyString())).thenAnswer(invocation -> invocation.<String>getArgument(0).substring(4));
        service = new CrmIntegrationService(repository, cipher, UYSOT_OAUTH, stateStore, audit, uysotOAuthPort, new ObjectMapper());
    }

    @Test
    void accountAddressInsideTheNetworkIsRefused() {
        ValidationException amoCrm = assertThrows(ValidationException.class, () -> service.connectAccount(COMPANY_ID,
                new ConnectCrmAccountRequest(CrmProvider.AMOCRM, "http://127.0.0.1:8080", "token")));
        ValidationException bitrix24 = assertThrows(ValidationException.class, () -> service.connectAccount(COMPANY_ID,
                new ConnectCrmAccountRequest(CrmProvider.BITRIX24, "https://10.1.2.3/rest/1/code/", null)));

        assertEquals(ErrorCode.CRM_BASE_URL_INVALID, amoCrm.code());
        assertEquals(ErrorCode.CRM_BASE_URL_INVALID, bitrix24.code());
        verify(repository, never()).saveCredentials(anyLong(), any());
    }

    @Test
    void amoCrmWithoutATokenIsRefused() {
        ValidationException refused = assertThrows(ValidationException.class, () -> service.connectAccount(COMPANY_ID,
                new ConnectCrmAccountRequest(CrmProvider.AMOCRM, "https://203.0.113.10", " ")));

        assertEquals(ErrorCode.CRM_ACCESS_TOKEN_REQUIRED, refused.code());
    }

    @Test
    void uysotIsNotConnectedByToken() {
        ValidationException refused = assertThrows(ValidationException.class, () -> service.connectAccount(COMPANY_ID,
                new ConnectCrmAccountRequest(CrmProvider.UYSOT, "https://203.0.113.10", "token")));

        assertEquals(ErrorCode.CRM_PROVIDER_USES_OAUTH, refused.code());
    }

    @Test
    void publicAccountIsStoredEncryptedAndShownByHostOnly() {
        when(repository.saveCredentials(eq(COMPANY_ID), any())).thenAnswer(invocation -> invocation.getArgument(1));

        CrmIntegrationRow row = service.connectAccount(COMPANY_ID,
                new ConnectCrmAccountRequest(CrmProvider.AMOCRM, " 203.0.113.10/ ", " token "));

        ArgumentCaptor<CrmIntegration> saved = ArgumentCaptor.forClass(CrmIntegration.class);
        verify(repository).saveCredentials(eq(COMPANY_ID), saved.capture());
        assertEquals("enc:https://203.0.113.10", saved.getValue().baseUrlEnc());
        assertEquals("enc:token", saved.getValue().accessTokenEnc());
        assertEquals(CrmIntegrationStatus.CONNECTED, saved.getValue().status());
        assertEquals("203.0.113.10", row.accountHost());
    }

    @Test
    void accountIsNotSavedWithoutAnEncryptionKey() {
        when(cipher.available()).thenReturn(false);

        ConflictException refused = assertThrows(ConflictException.class, () -> service.connectAccount(COMPANY_ID,
                new ConnectCrmAccountRequest(CrmProvider.AMOCRM, "https://203.0.113.10", "token")));

        assertEquals(ErrorCode.ENCRYPTION_KEY_NOT_SET, refused.code());
        verify(repository, never()).saveCredentials(anyLong(), any());
    }

    @Test
    void connectedAmoCrmIsHandedToThePipelineDecrypted() {
        when(repository.find(COMPANY_ID)).thenReturn(Optional.of(connectedAmoCrm()));

        assertEquals(Optional.of(new CrmConnection(CrmProvider.AMOCRM, "https://acme.amocrm.ru", "token")),
                service.findConnection(COMPANY_ID));
    }

    @Test
    void amoCrmTokenIsNeverHandedOutAsTheUysotToken() {
        when(repository.find(COMPANY_ID)).thenReturn(Optional.of(connectedAmoCrm()));

        assertEquals(Optional.empty(), service.findCurrentAccessToken(COMPANY_ID));
    }

    // --- Uysot OAuth: the app, the consent link, the callback

    @Test
    void appIsSavedWithItsGrantsAndReadBack() {
        when(repository.saveAppInfo(eq(COMPANY_ID), eq("Voice agent"), anyString()))
                .thenAnswer(invocation -> uysotApp(invocation.getArgument(2)));
        List<CrmGrant> grants = List.of(new CrmGrant(CrmPermission.LEAD, CrmScopeLevel.READ));

        CrmIntegrationRow row = service.connect(COMPANY_ID, new ConnectIntegrationRequest("Voice agent", grants));

        assertEquals(grants, row.grants());
        verify(audit).record(eq(COMPANY_ID), eq("CRM_INTEGRATION_CONNECT"), anyString(), anyString(),
                eq("Voice agent"));
    }

    @Test
    void consentLinkAsksForTheSavedGrantsUnderAOneTimeState() {
        when(repository.find(COMPANY_ID)).thenReturn(Optional.of(uysotApp(GRANTS_JSON)));
        when(stateStore.save(anyString(), eq(COMPANY_ID), eq(Duration.ofHours(1)))).thenReturn(true);

        String url = service.buildAuthorizeUrl(COMPANY_ID).authorizeUrl();

        ArgumentCaptor<String> state = ArgumentCaptor.forClass(String.class);
        verify(stateStore).save(state.capture(), eq(COMPANY_ID), eq(Duration.ofHours(1)));
        assertEquals("https://uysot.example/oauth/authorize?response_type=code&client_id=voice-app"
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                + "&scope=PERMISSION_OPEN_API_LEAD%3AREAD%20PERMISSION_OPEN_API_LEAD_NOTE%3ASAVE"
                + "&state=" + URLEncoder.encode(state.getValue(), StandardCharsets.UTF_8), url);
    }

    @Test
    void consentLinkNeedsTheAppAndItsGrantsSavedFirst() {
        when(repository.find(COMPANY_ID)).thenReturn(Optional.empty());
        ValidationException noApp = assertThrows(ValidationException.class,
                () -> service.buildAuthorizeUrl(COMPANY_ID));

        when(repository.find(COMPANY_ID)).thenReturn(Optional.of(uysotApp("[]")));
        ValidationException noGrants = assertThrows(ValidationException.class,
                () -> service.buildAuthorizeUrl(COMPANY_ID));

        assertEquals(ErrorCode.CRM_INTEGRATION_APP_NOT_CONFIGURED, noApp.code());
        assertEquals(ErrorCode.CRM_INTEGRATION_APP_NOT_CONFIGURED, noGrants.code());
        verifyNoInteractions(stateStore);
    }

    @Test
    void consentLinkIsNotHandedOutWhenItsStateCannotBeKept() {
        when(repository.find(COMPANY_ID)).thenReturn(Optional.of(uysotApp(GRANTS_JSON)));
        when(stateStore.save(anyString(), anyLong(), any())).thenReturn(false);

        ExternalServiceException refused = assertThrows(ExternalServiceException.class,
                () -> service.buildAuthorizeUrl(COMPANY_ID));

        assertEquals(ErrorCode.OAUTH_STATE_STORE_UNAVAILABLE, refused.code());
    }

    @Test
    void callbackStoresTheTokensForTheCompanyItsStateWasIssuedTo() throws Exception {
        stateIssuedToTheCompany();
        when(uysotOAuthPort.exchangeCode("code-1")).thenReturn(new OAuthTokenReply(200,
                "{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"expires_in\":3600}"));
        Instant before = Instant.now();

        assertEquals(settingsPage("crm=connected"), service.handleCallback("code-1", "state-1"));

        ArgumentCaptor<Instant> expiresAt = ArgumentCaptor.forClass(Instant.class);
        verify(repository).applyTokenResponse(eq(COMPANY_ID), eq("enc:access"), eq("enc:refresh"), expiresAt.capture());
        assertFalse(expiresAt.getValue().isBefore(before.plusSeconds(3600)));
        verify(audit).record(eq(COMPANY_ID), eq("CRM_INTEGRATION_CONNECTED"), anyString(), anyString(), isNull());
    }

    @Test
    void stateIsGoodForOneCallbackOnly() throws Exception {
        when(stateStore.consume("state-1")).thenReturn(COMPANY_ID, (Long) null);
        when(repository.find(COMPANY_ID)).thenReturn(Optional.of(uysotApp(GRANTS_JSON)));
        when(uysotOAuthPort.exchangeCode(anyString())).thenReturn(new OAuthTokenReply(200,
                "{\"access_token\":\"access\"}"));

        service.handleCallback("code-1", "state-1");
        URI replay = service.handleCallback("code-2", "state-1");

        assertEquals(settingsPage("crm=error&reason=OAUTH_STATE_INVALID"), replay);
        verify(uysotOAuthPort, times(1)).exchangeCode(anyString());
    }

    @Test
    void callbackWithoutAStateIsRefusedBeforeAnythingIsAsked() {
        assertEquals(settingsPage("crm=error&reason=OAUTH_STATE_INVALID"), service.handleCallback("code-1", " "));

        verifyNoInteractions(stateStore, uysotOAuthPort);
    }

    @Test
    void callbackForACompanyWithNoIntegrationIsNotFound() throws Exception {
        when(stateStore.consume("state-1")).thenReturn(COMPANY_ID);
        when(repository.find(COMPANY_ID)).thenReturn(Optional.empty());

        assertEquals(settingsPage("crm=error&reason=CRM_INTEGRATION_NOT_FOUND"),
                service.handleCallback("code-1", "state-1"));
        verify(uysotOAuthPort, never()).exchangeCode(anyString());
    }

    @Test
    void refusedTokenExchangeMarksTheIntegrationAndSaysWhy() throws Exception {
        stateIssuedToTheCompany();
        when(uysotOAuthPort.exchangeCode("code-1")).thenReturn(new OAuthTokenReply(400,
                "{\"error\":\"invalid_grant\"}"));

        assertEquals(settingsPage("crm=error&reason=UYSOT_OAUTH_TOKEN_EXCHANGE_HTTP_ERROR"),
                service.handleCallback("code-1", "state-1"));
        verify(repository).markError(COMPANY_ID);
        verify(repository, never()).applyTokenResponse(anyLong(), any(), any(), any());
    }

    @Test
    void tokenReplyWithoutAnAccessTokenIsAFailedConnection() throws Exception {
        stateIssuedToTheCompany();
        when(uysotOAuthPort.exchangeCode("code-1")).thenReturn(new OAuthTokenReply(200, "{\"expires_in\":3600}"));

        assertEquals(settingsPage("crm=error&reason=UYSOT_OAUTH_TOKEN_MISSING"),
                service.handleCallback("code-1", "state-1"));
        verify(repository).markError(COMPANY_ID);
    }

    @Test
    void unreachableTokenEndpointIsAFailedConnection() throws Exception {
        stateIssuedToTheCompany();
        when(uysotOAuthPort.exchangeCode("code-1")).thenThrow(new IOException("connection reset"));

        assertEquals(settingsPage("crm=error&reason=UYSOT_OAUTH_TOKEN_EXCHANGE_FAILED"),
                service.handleCallback("code-1", "state-1"));
        verify(repository).markError(COMPANY_ID);
    }

    // --- the Uysot token over time

    @Test
    void uysotTokenFarFromExpiryIsHandedOutAsStored() {
        when(repository.find(COMPANY_ID)).thenReturn(Optional.of(connectedUysot(Instant.now().plus(Duration.ofHours(1)))));

        assertEquals(Optional.of("access"), service.findCurrentAccessToken(COMPANY_ID));
        verifyNoInteractions(uysotOAuthPort);
    }

    @Test
    void uysotTokenAboutToExpireIsRefreshedAndKeepsItsRefreshToken() throws Exception {
        when(repository.find(COMPANY_ID)).thenReturn(Optional.of(connectedUysot(Instant.now().plusSeconds(60))));
        when(uysotOAuthPort.refreshToken("refresh")).thenReturn(new OAuthTokenReply(200,
                "{\"access_token\":\"fresh\",\"expires_in\":3600}"));

        assertEquals(Optional.of("fresh"), service.findCurrentAccessToken(COMPANY_ID));
        verify(repository).applyTokenResponse(eq(COMPANY_ID), eq("enc:fresh"), eq("enc:refresh"), any());
    }

    @Test
    void refreshTheProviderRefusesMarksTheIntegrationAndHandsOutNoToken() throws Exception {
        when(repository.find(COMPANY_ID)).thenReturn(Optional.of(connectedUysot(Instant.now().minusSeconds(60))));
        when(uysotOAuthPort.refreshToken("refresh")).thenReturn(new OAuthTokenReply(401, "{}"));

        assertEquals(Optional.empty(), service.findCurrentAccessToken(COMPANY_ID));
        verify(repository).markError(COMPANY_ID);
        verify(repository, never()).applyTokenResponse(anyLong(), any(), any(), any());
    }

    // --- disconnect

    @Test
    void disconnectForgetsTheConnectionEvenWhenTheRevokeFails() throws Exception {
        when(repository.find(COMPANY_ID)).thenReturn(Optional.of(connectedUysot(Instant.now().plus(Duration.ofHours(1)))));
        doThrow(new IOException("revoke endpoint down")).when(uysotOAuthPort).revokeToken("access");

        service.disconnect(COMPANY_ID);

        verify(uysotOAuthPort).revokeToken("access");
        verify(repository).disconnect(COMPANY_ID);
        verify(audit).record(eq(COMPANY_ID), eq("CRM_INTEGRATION_DISCONNECT"), anyString(), anyString(), isNull());
    }

    @Test
    void amoCrmTokenIsNeverSentToUysotsRevokeEndpoint() {
        when(repository.find(COMPANY_ID)).thenReturn(Optional.of(connectedAmoCrm()));

        service.disconnect(COMPANY_ID);

        verifyNoInteractions(uysotOAuthPort);
        verify(repository).disconnect(COMPANY_ID);
    }

    private void stateIssuedToTheCompany() {
        when(stateStore.consume("state-1")).thenReturn(COMPANY_ID);
        when(repository.find(COMPANY_ID)).thenReturn(Optional.of(uysotApp(GRANTS_JSON)));
    }

    private static URI settingsPage(String query) {
        return URI.create(SETTINGS_PAGE + "?" + query);
    }

    private static CrmIntegration uysotApp(String grantsJson) {
        return new CrmIntegration(COMPANY_ID, CrmProvider.UYSOT, "Voice agent", grantsJson, null, null, null, null,
                CrmIntegrationStatus.NOT_CONNECTED, null, Instant.now());
    }

    private static CrmIntegration connectedUysot(Instant tokenExpiresAt) {
        return new CrmIntegration(COMPANY_ID, CrmProvider.UYSOT, "Voice agent", GRANTS_JSON, "enc:access",
                "enc:refresh", null, tokenExpiresAt, CrmIntegrationStatus.CONNECTED, Instant.now(), Instant.now());
    }

    private static CrmIntegration connectedAmoCrm() {
        return new CrmIntegration(COMPANY_ID, CrmProvider.AMOCRM, null, null, "enc:token", null,
                "enc:https://acme.amocrm.ru", null, CrmIntegrationStatus.CONNECTED, Instant.now(), Instant.now());
    }
}
