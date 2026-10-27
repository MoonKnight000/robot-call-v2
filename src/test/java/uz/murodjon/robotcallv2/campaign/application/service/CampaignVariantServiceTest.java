package uz.murodjon.robotcallv2.campaign.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.billing.application.port.input.BillingUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignVariantRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignVariant;
import uz.murodjon.robotcallv2.campaign.domain.entity.VariantOptimizationPlan;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.conversion.application.port.input.ConversionUseCase;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CampaignVariantServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long CAMPAIGN_ID = 100L;

    private CampaignVariantRepository variantRepository;
    private CampaignRepository campaignRepository;
    private ConversionUseCase conversionUseCase;
    private BillingUseCase billingUseCase;
    private AiAgentUseCase aiAgentUseCase;

    private CampaignVariantService service;

    @BeforeEach
    void setUp() {
        variantRepository = mock(CampaignVariantRepository.class);
        campaignRepository = mock(CampaignRepository.class);
        conversionUseCase = mock(ConversionUseCase.class);
        billingUseCase = mock(BillingUseCase.class);
        aiAgentUseCase = mock(AiAgentUseCase.class);

        service = new CampaignVariantService(variantRepository, campaignRepository, conversionUseCase,
                billingUseCase, aiAgentUseCase);
    }

    @Test
    void optimizeWeightsFailsWhenCampaignNotFound() {
        when(campaignRepository.find(COMPANY_ID, CAMPAIGN_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.optimizeWeights(COMPANY_ID, CAMPAIGN_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void optimizeWeightsUpdatesTrafficWeightsInRepository() {
        Campaign campaign = testCampaign();
        when(campaignRepository.find(COMPANY_ID, CAMPAIGN_ID)).thenReturn(campaign);

        // Variant 1 has 500 answered and 250 converted (winner), Variant 2 has 500 answered and 50 converted (loser)
        CampaignVariant v1 = variant(1L, "Winner", 50, 500, 250, true);
        CampaignVariant v2 = variant(2L, "Loser", 50, 500, 50, true);
        when(variantRepository.findAllByCampaignIdAndCompanyId(CAMPAIGN_ID, COMPANY_ID))
                .thenReturn(List.of(v1, v2));

        VariantOptimizationPlan plan = service.optimizeWeights(COMPANY_ID, CAMPAIGN_ID);

        assertThat(plan.strategy()).isEqualTo("STATISTICAL_WINNER");
        assertThat(plan.winningVariantId()).isEqualTo(1L);

        // Winner updated from 50 to 90, loser updated from 50 to 10
        verify(variantRepository).updateTrafficWeight(1L, COMPANY_ID, 90);
        verify(variantRepository).updateTrafficWeight(2L, COMPANY_ID, 10);
    }

    @Test
    void autoTuneIfEligibleReturnsNullWhenInColdStart() {
        CampaignVariant v1 = variant(1L, "A", 50, 5, 1, true);
        CampaignVariant v2 = variant(2L, "B", 50, 5, 0, true);
        when(variantRepository.findAllByCampaignIdAndCompanyId(CAMPAIGN_ID, COMPANY_ID))
                .thenReturn(List.of(v1, v2));

        VariantOptimizationPlan plan = service.autoTuneIfEligible(COMPANY_ID, CAMPAIGN_ID);

        assertThat(plan).isNull();
        verify(variantRepository, never()).updateTrafficWeight(anyLong(), anyLong(), anyInt());
    }

    @Test
    void autoTuneIfEligibleExecutesWhenAnswerCountThresholdReached() {
        Campaign campaign = testCampaign();
        when(campaignRepository.find(COMPANY_ID, CAMPAIGN_ID)).thenReturn(campaign);

        CampaignVariant v1 = variant(1L, "A", 50, 25, 10, true);
        CampaignVariant v2 = variant(2L, "B", 50, 25, 2, true);
        when(variantRepository.findAllByCampaignIdAndCompanyId(CAMPAIGN_ID, COMPANY_ID))
                .thenReturn(List.of(v1, v2));

        VariantOptimizationPlan plan = service.autoTuneIfEligible(COMPANY_ID, CAMPAIGN_ID);

        assertThat(plan).isNotNull();
        assertThat(plan.strategy()).isEqualTo("ADAPTIVE_BANDIT");
        verify(variantRepository, atLeastOnce()).updateTrafficWeight(eq(1L), eq(COMPANY_ID), anyInt());
        verify(variantRepository, atLeastOnce()).updateTrafficWeight(eq(2L), eq(COMPANY_ID), anyInt());
    }

    @Test
    void recordConversionTriggersAutoTune() {
        Campaign campaign = testCampaign();
        when(campaignRepository.find(COMPANY_ID, CAMPAIGN_ID)).thenReturn(campaign);

        CampaignVariant v1 = variant(1L, "A", 50, 30, 15, true);
        CampaignVariant v2 = variant(2L, "B", 50, 30, 3, true);

        when(variantRepository.findById(1L)).thenReturn(Optional.of(v1));
        when(variantRepository.findAllByCampaignIdAndCompanyId(CAMPAIGN_ID, COMPANY_ID))
                .thenReturn(List.of(v1, v2));

        service.recordConversion(1L);

        verify(variantRepository).recordConversion(1L);
        verify(variantRepository, atLeastOnce()).updateTrafficWeight(eq(1L), eq(COMPANY_ID), anyInt());
    }

    private static CampaignVariant variant(long id, String name, int weight, int answered, int converted, boolean active) {
        return new CampaignVariant(id, CAMPAIGN_ID, COMPANY_ID, name, null, null, null, null, weight,
                answered, answered, converted, active, Instant.now(), Instant.now());
    }

    private static Campaign testCampaign() {
        return Campaign.builder()
                .id(CAMPAIGN_ID)
                .companyId(COMPANY_ID)
                .name("A/B Test Campaign")
                .type(CampaignType.OUTBOUND)
                .status(CampaignStatus.ACTIVE)
                .build();
    }
}
