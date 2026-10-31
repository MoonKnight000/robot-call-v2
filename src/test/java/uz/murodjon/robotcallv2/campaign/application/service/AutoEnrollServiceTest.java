package uz.murodjon.robotcallv2.campaign.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.campaign.application.dto.AddTargetRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.AddTargetsResponse;
import uz.murodjon.robotcallv2.campaign.application.port.output.AutoEnrollContactRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollCandidate;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollRule;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollment;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.enums.AutoEnrollField;
import uz.murodjon.robotcallv2.campaign.domain.enums.AutoEnrollOperator;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignKind;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutoEnrollServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long CAMPAIGN_ID = 5L;
    private static final AutoEnrollment RULES = new AutoEnrollment(true,
            List.of(new AutoEnrollRule(AutoEnrollField.TAG, AutoEnrollOperator.EQUALS, "qarzdor")), List.of());

    private AutoEnrollContactRepository contacts;
    private CampaignRepository campaigns;
    private CampaignTargetService targets;
    private AutoEnrollService service;

    @BeforeEach
    void setUp() {
        contacts = mock(AutoEnrollContactRepository.class);
        campaigns = mock(CampaignRepository.class);
        targets = mock(CampaignTargetService.class);
        service = new AutoEnrollService(contacts, campaigns, targets, new ObjectMapper());
    }

    private static Campaign campaign(CampaignKind kind, AutoEnrollment rules) {
        return Campaign.builder().id(CAMPAIGN_ID).name("c").status(CampaignStatus.ACTIVE).companyId(COMPANY_ID)
                .kind(kind).autoEnroll(rules).build();
    }

    @Test
    void matchingContactsBecomeTargetsWithTheirOwnFactsButNotTheirNotes() {
        Campaign campaign = campaign(CampaignKind.AUTO_ENROLL, RULES);
        when(contacts.findMatching(COMPANY_ID, RULES, CAMPAIGN_ID, AutoEnrollService.SWEEP_BATCH)).thenReturn(
                List.of(new AutoEnrollCandidate(7, "Aziza opa", "+998901112233", "Toshkent", "qarzdor, vip")));
        when(targets.addTargets(eq(COMPANY_ID), eq(CAMPAIGN_ID), anyList()))
                .thenReturn(new AddTargetsResponse(CAMPAIGN_ID, 1, List.of(9L), 0, 0));

        int added = service.enrollMatchingContacts(campaign);

        assertThat(added).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AddTargetRequest>> captured = ArgumentCaptor.forClass(List.class);
        verify(targets).addTargets(eq(COMPANY_ID), eq(CAMPAIGN_ID), captured.capture());
        AddTargetRequest request = captured.getValue().get(0);
        assertThat(request.phone()).isEqualTo("+998901112233");
        assertThat(request.contextData().get("clientName").asText()).isEqualTo("Aziza opa");
        assertThat(request.contextData().has("notes")).isFalse();
    }

    @Test
    void nobodyMatchingEnrollsNothing() {
        when(contacts.findMatching(anyLong(), any(), any(), anyInt())).thenReturn(List.of());

        assertThat(service.enrollMatchingContacts(campaign(CampaignKind.AUTO_ENROLL, RULES))).isZero();
        verifyNoInteractions(targets);
    }

    @Test
    void theSweepSkipsStandardCampaignsAndCampaignsWithoutRules() {
        when(campaigns.findActive()).thenReturn(List.of(
                campaign(CampaignKind.STANDARD, AutoEnrollment.NONE),
                campaign(CampaignKind.AUTO_ENROLL, AutoEnrollment.NONE)));

        service.enrollAllActiveCampaigns();

        verifyNoInteractions(contacts);
    }

    @Test
    void oneCampaignFailingDoesNotStopTheNext() {
        Campaign failing = campaign(CampaignKind.AUTO_ENROLL, RULES);
        Campaign healthy = failing.toBuilder().id(6L).build();
        when(campaigns.findActive()).thenReturn(List.of(failing, healthy));
        when(contacts.findMatching(COMPANY_ID, RULES, CAMPAIGN_ID, AutoEnrollService.SWEEP_BATCH))
                .thenThrow(new IllegalStateException("db down"));
        when(contacts.findMatching(COMPANY_ID, RULES, 6L, AutoEnrollService.SWEEP_BATCH)).thenReturn(List.of());

        service.enrollAllActiveCampaigns();

        verify(contacts).findMatching(COMPANY_ID, RULES, 6L, AutoEnrollService.SWEEP_BATCH);
    }

    @Test
    void previewOfAStandardCampaignListsItsFilteredContacts() {
        when(campaigns.find(COMPANY_ID, CAMPAIGN_ID)).thenReturn(campaign(CampaignKind.STANDARD, RULES));
        when(contacts.countMatching(COMPANY_ID, RULES)).thenReturn(3L);

        assertThat(service.previewAutoEnroll(COMPANY_ID, CAMPAIGN_ID, null, 1, 20).total()).isEqualTo(3L);
    }

    @Test
    void previewCountsAllMatchesAndSamplesTheFirstFew() {
        when(campaigns.find(COMPANY_ID, CAMPAIGN_ID)).thenReturn(campaign(CampaignKind.AUTO_ENROLL, RULES));
        when(contacts.countMatching(COMPANY_ID, RULES)).thenReturn(42L);
        when(contacts.findMatchingPage(COMPANY_ID, RULES, 0, 10)).thenReturn(
                List.of(new AutoEnrollCandidate(7, "Aziza opa", "+998901112233", null, null)));

        var preview = service.previewAutoEnroll(COMPANY_ID, CAMPAIGN_ID, null, 1, 10);

        assertThat(preview.total()).isEqualTo(42L);
        assertThat(preview.sample()).hasSize(1);
    }
}
