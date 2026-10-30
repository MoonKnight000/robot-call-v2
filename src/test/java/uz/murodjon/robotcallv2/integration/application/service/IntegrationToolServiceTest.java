package uz.murodjon.robotcallv2.integration.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.integration.application.dto.CreateIntegrationToolRequest;
import uz.murodjon.robotcallv2.integration.application.dto.CrmGrant;
import uz.murodjon.robotcallv2.integration.application.dto.CrmToolRow;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateCrmToolsRequest;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateIntegrationToolRequest;
import uz.murodjon.robotcallv2.integration.application.port.input.CrmIntegrationUseCase;
import uz.murodjon.robotcallv2.integration.application.port.output.CrmAccountProbePort;
import uz.murodjon.robotcallv2.integration.application.port.output.IntegrationToolRepository;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationTool;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmPermission;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmScopeLevel;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmTool;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmToolGroup;
import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationToolKind;
import uz.murodjon.robotcallv2.integration.domain.enums.ToolPropagation;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolAuth;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolHttpConfig;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntegrationToolServiceTest {

    private static final long COMPANY_ID = 7L;
    private static final long ADMIN_COMPANY_ID = 1L;

    private IntegrationToolRepository repository;
    private CrmIntegrationUseCase crmIntegrationUseCase;
    private ToolUseCase toolUseCase;
    private CrmAccountProbePort probePort;
    private IntegrationToolService service;

    private final IntegrationTool searchFlats = tool(1, "SEARCH_FLATS", CrmPermission.CONTRACT, CrmScopeLevel.READ, true);
    private final IntegrationTool createLead = tool(2, "CREATE_LEAD", CrmPermission.LEAD, CrmScopeLevel.SAVE, false);

    @BeforeEach
    void setUp() {
        repository = mock(IntegrationToolRepository.class);
        crmIntegrationUseCase = mock(CrmIntegrationUseCase.class);
        toolUseCase = mock(ToolUseCase.class);
        probePort = mock(CrmAccountProbePort.class);
        when(probePort.findUysotBaseUrl()).thenReturn("https://api.uysot.example/");
        service = new IntegrationToolService(repository, crmIntegrationUseCase, mock(CompanyUseCase.class),
                mock(AuditUseCase.class), toolUseCase, probePort);
        when(repository.findByProvider(CrmProvider.UYSOT)).thenReturn(List.of(searchFlats, createLead));
        when(repository.findChoices(COMPANY_ID)).thenReturn(Map.of());
        when(crmIntegrationUseCase.findGrants(COMPANY_ID)).thenReturn(List.of());
    }

    @Test
    void aCompanyThatNeverChoseFollowsTheToolDefaults() {
        List<CrmToolRow> rows = service.findTools(COMPANY_ID);

        assertTrue(rows.get(0).enabled(), "SEARCH_FLATS defaults to on");
        assertFalse(rows.get(1).enabled(), "CREATE_LEAD defaults to off");
        assertTrue(rows.stream().allMatch(CrmToolRow::inherited));
    }

    @Test
    void aCompanysOwnChoiceWinsOverTheDefault() {
        when(repository.findChoices(COMPANY_ID)).thenReturn(Map.of(1L, false, 2L, true));

        List<CrmToolRow> rows = service.findTools(COMPANY_ID);

        assertFalse(rows.get(0).enabled());
        assertTrue(rows.get(1).enabled());
        assertTrue(rows.stream().noneMatch(CrmToolRow::inherited));
    }

    @Test
    void platformDisabledToolIsNotShownToACompany() {
        when(repository.findByProvider(CrmProvider.UYSOT))
                .thenReturn(List.of(searchFlats, createLead.toBuilder().platformEnabled(false).build()));

        assertEquals(1, service.findTools(COMPANY_ID).size());
    }

    @Test
    void savingOnlyStoresWhereTheChoiceDiffersFromTheDefault() {
        // SEARCH_FLATS stays on (= default, nothing stored); CREATE_LEAD is switched on (default off, stored).
        service.updateTools(COMPANY_ID, new UpdateCrmToolsRequest(List.of("SEARCH_FLATS", "CREATE_LEAD")));

        verify(repository).saveChoice(COMPANY_ID, 2L, true);
        verify(repository, never()).saveChoice(eq(COMPANY_ID), eq(1L), anyBoolean());
    }

    @Test
    void returningToTheDefaultForgetsTheChoice() {
        when(repository.findChoices(COMPANY_ID)).thenReturn(Map.of(2L, true));

        service.updateTools(COMPANY_ID, new UpdateCrmToolsRequest(List.of("SEARCH_FLATS")));

        verify(repository).deleteChoice(COMPANY_ID, 2L);
    }

    @Test
    void anUnknownToolKeyIsRefused() {
        ValidationException refused = assertThrows(ValidationException.class,
                () -> service.updateTools(COMPANY_ID, new UpdateCrmToolsRequest(List.of("NO_SUCH_TOOL"))));

        assertEquals(ErrorCode.INTEGRATION_TOOL_UNKNOWN, refused.code());
    }

    @Test
    void aToolWhoseGrantTheCompanyDidNotGiveIsNotOffered() {
        when(crmIntegrationUseCase.findGrants(COMPANY_ID))
                .thenReturn(List.of(new CrmGrant(CrmPermission.LEAD, CrmScopeLevel.READ)));
        when(repository.findChoices(COMPANY_ID)).thenReturn(Map.of(2L, true));

        Set<String> offered = service.findEnabledToolNames(COMPANY_ID);

        assertFalse(offered.contains("searchFlats"), "CONTRACT:READ was not granted");
        assertFalse(offered.contains("createLead"), "LEAD:SAVE was not granted, although the company switched it on");
    }

    @Test
    void withoutRecordedGrantsEveryEnabledToolIsOffered() {
        Set<String> offered = service.findEnabledToolNames(COMPANY_ID);

        assertEquals(Set.of("searchFlats"), offered);
    }

    @Test
    void changingTheDefaultWithoutSayingWhoItReachesIsRefused() {
        when(repository.findById(2L)).thenReturn(Optional.of(createLead));

        ValidationException refused = assertThrows(ValidationException.class,
                () -> service.updateCatalogTool(ADMIN_COMPANY_ID, 2L, edit(true, null)));

        assertEquals(ErrorCode.INTEGRATION_TOOL_PROPAGATION_REQUIRED, refused.code());
        verify(repository, never()).update(any());
    }

    @Test
    void newOnlyPinsExistingCompaniesToTheOldDefaultBeforeItChanges() {
        when(repository.findById(2L)).thenReturn(Optional.of(createLead));
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateCatalogTool(ADMIN_COMPANY_ID, 2L, edit(true, ToolPropagation.NEW_ONLY));

        var order = inOrder(repository);
        order.verify(repository).pinCompaniesWithoutChoice(2L, false);
        order.verify(repository).update(argThat(saved -> saved.defaultEnabled()));
    }

    @Test
    void allLeavesCompaniesFollowingTheNewDefault() {
        when(repository.findById(2L)).thenReturn(Optional.of(createLead));
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateCatalogTool(ADMIN_COMPANY_ID, 2L, edit(true, ToolPropagation.ALL));

        verify(repository, never()).pinCompaniesWithoutChoice(anyLong(), anyBoolean());
        verify(repository).update(argThat(saved -> saved.defaultEnabled()));
    }

    @Test
    void editingWithoutChangingTheDefaultNeedsNoPropagation() {
        when(repository.findById(2L)).thenReturn(Optional.of(createLead));
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> service.updateCatalogTool(ADMIN_COMPANY_ID, 2L, edit(false, null)));
        verify(repository, never()).pinCompaniesWithoutChoice(anyLong(), anyBoolean());
    }

    private static UpdateIntegrationToolRequest edit(boolean defaultEnabled, ToolPropagation applyTo) {
        return new UpdateIntegrationToolRequest("Lid yaratish", "Yangi lid ochadi.", CrmToolGroup.LEADS,
                defaultEnabled, true, 80, null, null, null, null, applyTo);
    }

    private static UpdateIntegrationToolRequest withModelDescription(String text) {
        return new UpdateIntegrationToolRequest("Label", "Description", CrmToolGroup.DEBTS, true, true, 60,
                null, null, null, text, null);
    }

    private static Tool template(String name, String url, List<ToolKeyValuePair> headers, ToolAuth auth, ToolEffect effect) {
        return new Tool(null, null, name, "Looks something up.",
                new ToolHttpConfig(url, effect == ToolEffect.READ ? "GET" : "POST", headers, null, null, null, null,
                        null, 0, auth, null, null),
                null, null, null, 3, effect, null, null, null, null);
    }

    private static CreateIntegrationToolRequest create(String key, boolean defaultEnabled, ToolPropagation applyTo) {
        return new CreateIntegrationToolRequest(CrmProvider.UYSOT, key, CrmToolGroup.LEADS, "Label", "Description",
                CrmPermission.LEAD, CrmScopeLevel.READ, defaultEnabled, true, 90,
                mock(CreateToolRequest.class), applyTo);
    }

    private void givenTemplate(Tool template) {
        when(toolUseCase.buildTemplate(any())).thenReturn(template);
        when(repository.create(any())).thenAnswer(invocation -> {
            IntegrationTool created = invocation.getArgument(0);
            return created.toBuilder().id(50).build();
        });
    }

    private static ValidationException failure(org.junit.jupiter.api.function.Executable action) {
        return assertThrows(ValidationException.class, action);
    }

    @Test
    void anHttpToolMayNotNameAHostOfItsOwn() {
        for (String url : List.of("https://evil.example/x", "/v1/x", "v1/../x", "//evil.example", "{{secrets.A}}/x", "a b")) {
            givenTemplate(template("lookupDebt", url, null, null, ToolEffect.READ));
            assertEquals(ErrorCode.INTEGRATION_TOOL_PATH_INVALID,
                    failure(() -> service.createCatalogTool(ADMIN_COMPANY_ID, create("LOOKUP_DEBT", false, null))).code(),
                    url);
        }
        verify(repository, never()).create(any());
    }

    @Test
    void anHttpToolMayNotCarryHeadersOrItsOwnAuth() {
        givenTemplate(template("lookupDebt", "v1/open-api/x", List.of(new ToolKeyValuePair("X-Open-Api-Token", "mine", null, null)),
                null, ToolEffect.READ));
        assertEquals(ErrorCode.INTEGRATION_TOOL_HTTP_LOCKED,
                failure(() -> service.createCatalogTool(ADMIN_COMPANY_ID, create("LOOKUP_DEBT", false, null))).code());

        givenTemplate(template("lookupDebt", "v1/open-api/x", null, mock(ToolAuth.class), ToolEffect.READ));
        assertEquals(ErrorCode.INTEGRATION_TOOL_HTTP_LOCKED,
                failure(() -> service.createCatalogTool(ADMIN_COMPANY_ID, create("LOOKUP_DEBT", false, null))).code());
    }

    @Test
    void theModelNameMayNotShadowABuiltInTool() {
        givenTemplate(template(CrmTool.values()[0].toolName(), "v1/open-api/x", null, null, ToolEffect.READ));
        assertEquals(ErrorCode.INTEGRATION_TOOL_NAME_INVALID,
                failure(() -> service.createCatalogTool(ADMIN_COMPANY_ID, create("LOOKUP_DEBT", false, null))).code());
    }

    @Test
    void aKeyAlreadyInTheCatalogIsRefused() {
        givenTemplate(template("lookupDebt", "v1/open-api/x", null, null, ToolEffect.READ));
        assertThrows(ConflictException.class,
                () -> service.createCatalogTool(ADMIN_COMPANY_ID, create("SEARCH_FLATS", false, null)));
    }

    @Test
    void aNewToolThatStartsOnNeedsToSayWhoGetsIt() {
        givenTemplate(template("lookupDebt", "v1/open-api/x", null, null, ToolEffect.READ));
        assertEquals(ErrorCode.INTEGRATION_TOOL_PROPAGATION_REQUIRED,
                failure(() -> service.createCatalogTool(ADMIN_COMPANY_ID, create("LOOKUP_DEBT", true, null))).code());
    }

    @Test
    void aNewToolForNewCompaniesOnlyIsPinnedOffForTheExistingOnes() {
        givenTemplate(template("lookupDebt", "v1/open-api/x", null, null, ToolEffect.READ));

        service.createCatalogTool(ADMIN_COMPANY_ID, create("LOOKUP_DEBT", true, ToolPropagation.NEW_ONLY));

        verify(repository).pinCompaniesWithoutChoice(50L, false);
    }

    @Test
    void aNewToolForEveryoneIsNotPinned() {
        givenTemplate(template("lookupDebt", "v1/open-api/x", null, null, ToolEffect.READ));

        service.createCatalogTool(ADMIN_COMPANY_ID, create("LOOKUP_DEBT", true, ToolPropagation.ALL));

        verify(repository, never()).pinCompaniesWithoutChoice(anyLong(), anyBoolean());
    }

    @Test
    void aBuiltInToolsModelTextIsEditableAndBlankGoesBackToCode() {
        IntegrationTool builtIn = tool(3, "GET_DEBT_SUMMARY", CrmPermission.CONTRACT, CrmScopeLevel.READ, true);
        when(repository.findById(3L)).thenReturn(Optional.of(builtIn));
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<IntegrationTool> saved = ArgumentCaptor.forClass(IntegrationTool.class);

        service.updateCatalogTool(ADMIN_COMPANY_ID, 3L, withModelDescription("  Read every debt.  "));
        service.updateCatalogTool(ADMIN_COMPANY_ID, 3L, withModelDescription("   "));

        verify(repository, times(2)).update(saved.capture());
        assertEquals("Read every debt.", saved.getAllValues().get(0).modelDescription());
        assertNull(saved.getAllValues().get(1).modelDescription());
    }

    @Test
    void theModelTextOfBuiltInToolsIsOfferedByTheNameTheModelKnows() {
        IntegrationTool described = tool(3, "GET_DEBT_SUMMARY", CrmPermission.CONTRACT, CrmScopeLevel.READ, true)
                .toBuilder().modelDescription("Read every debt.").build();
        when(repository.findByProvider(CrmProvider.UYSOT)).thenReturn(List.of(searchFlats, described));

        assertEquals(Map.of("getDebtSummary", "Read every debt."), service.findBuiltInModelDescriptions());
    }

    @Test
    void aBuiltInToolCannotBeDeleted() {
        assertEquals(ErrorCode.INTEGRATION_TOOL_BUILT_IN_LOCKED,
                failure(() -> service.deleteCatalogTool(ADMIN_COMPANY_ID, 1L)).code());
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void anEnabledHttpToolIsOfferedPointedAtTheCompanysOwnAccountWithItsToken() {
        IntegrationTool http = tool(9, "LOOKUP_DEBT", null, null, true).toBuilder()
                .kind(IntegrationToolKind.HTTP)
                .definition(template("lookupDebt", "v1/open-api/debt/filter", null, null, ToolEffect.READ))
                .build();
        when(repository.findByProvider(CrmProvider.UYSOT)).thenReturn(List.of(searchFlats, http));
        when(crmIntegrationUseCase.findConnection(COMPANY_ID))
                .thenReturn(Optional.of(new CrmConnection(CrmProvider.UYSOT, null, "tok")));

        List<Tool> tools = service.findEnabledHttpTools(COMPANY_ID);

        assertEquals(1, tools.size());
        Tool offered = tools.get(0);
        assertEquals("https://api.uysot.example/v1/open-api/debt/filter", offered.http().url());
        assertNull(offered.companyId(), "no company: no secrets, no execution row of a stored tool");
        assertEquals("X-Open-Api-Token", offered.http().headers().get(0).key());
        assertEquals("tok", offered.http().headers().get(0).value());
        assertNull(offered.http().auth());
    }

    @Test
    void noHttpToolIsOfferedToACompanyThatIsNotConnected() {
        IntegrationTool http = tool(9, "LOOKUP_DEBT", null, null, true).toBuilder()
                .kind(IntegrationToolKind.HTTP)
                .definition(template("lookupDebt", "v1/open-api/debt/filter", null, null, ToolEffect.READ))
                .build();
        when(repository.findByProvider(CrmProvider.UYSOT)).thenReturn(List.of(http));
        when(crmIntegrationUseCase.findConnection(COMPANY_ID)).thenReturn(Optional.empty());

        assertTrue(service.findEnabledHttpTools(COMPANY_ID).isEmpty());
    }

    @Test
    void noHttpToolIsOfferedWhenThePlatformHasNoUysotAddress() {
        IntegrationTool http = tool(9, "LOOKUP_DEBT", null, null, true).toBuilder()
                .kind(IntegrationToolKind.HTTP)
                .definition(template("lookupDebt", "v1/open-api/debt/filter", null, null, ToolEffect.READ))
                .build();
        when(repository.findByProvider(CrmProvider.UYSOT)).thenReturn(List.of(http));
        when(crmIntegrationUseCase.findConnection(COMPANY_ID))
                .thenReturn(Optional.of(new CrmConnection(CrmProvider.UYSOT, null, "tok")));
        when(probePort.findUysotBaseUrl()).thenReturn(null);

        assertTrue(service.findEnabledHttpTools(COMPANY_ID).isEmpty());
    }

    private static IntegrationTool tool(long id, String key, CrmPermission permission, CrmScopeLevel scope,
                                        boolean defaultEnabled) {
        return IntegrationTool.builder().id(id).provider(CrmProvider.UYSOT).key(key).group(CrmToolGroup.LEADS)
                .label(key).description(key).permission(permission).scope(scope).defaultEnabled(defaultEnabled)
                .sortOrder((int) id).build();
    }
}
