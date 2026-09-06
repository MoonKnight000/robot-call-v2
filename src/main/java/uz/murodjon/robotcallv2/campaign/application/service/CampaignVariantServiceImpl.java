package uz.murodjon.robotcallv2.campaign.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignVariantUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignVariantRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignVariant;
import uz.murodjon.robotcallv2.campaign.domain.service.AbTestSignificance;
import uz.murodjon.robotcallv2.campaign.domain.service.CampaignVariantSelector;
import uz.murodjon.robotcallv2.campaign.presentation.dto.AbTestReportResponse;
import uz.murodjon.robotcallv2.campaign.presentation.dto.CampaignVariantCreateRequest;
import uz.murodjon.robotcallv2.campaign.presentation.dto.CampaignVariantResponse;
import uz.murodjon.robotcallv2.campaign.presentation.dto.CampaignVariantUpdateRequest;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class CampaignVariantServiceImpl implements CampaignVariantUseCase {

    private final CampaignVariantRepository variantRepository;
    private final CampaignRepository campaignRepository;
    private final CurrentCompany currentCompany;

    public CampaignVariantServiceImpl(CampaignVariantRepository variantRepository,
                                      CampaignRepository campaignRepository,
                                      CurrentCompany currentCompany) {
        this.variantRepository = variantRepository;
        this.campaignRepository = campaignRepository;
        this.currentCompany = currentCompany;
    }

    @Override
    @Transactional
    public CampaignVariantResponse create(long campaignId, CampaignVariantCreateRequest request) {
        long companyId = currentCompany.id();
        Campaign campaign = campaignRepository.find(campaignId);
        if (campaign == null || campaign.companyId() != companyId) {
            throw new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, campaignId);
        }

        CampaignVariant variant = new CampaignVariant(
                0L,
                campaign.id(),
                companyId,
                request.name().trim(),
                request.aiAgentId(),
                request.promptOverride(),
                request.ttsVoiceId(),
                request.trafficWeight() != null ? request.trafficWeight() : 50,
                0,
                0,
                0,
                true,
                Instant.now(),
                Instant.now()
        );

        CampaignVariant saved = variantRepository.save(variant);
        return toResponse(saved);
    }

    @Override
    public CampaignVariantResponse get(long campaignId, long variantId) {
        long companyId = currentCompany.id();
        CampaignVariant variant = variantRepository.findByIdAndCompanyId(variantId, companyId)
                .filter(v -> v.campaignId() == campaignId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CAMPAIGN_VARIANT_NOT_FOUND, variantId));
        return toResponse(variant);
    }

    @Override
    @Transactional
    public CampaignVariantResponse update(long campaignId, long variantId, CampaignVariantUpdateRequest request) {
        long companyId = currentCompany.id();
        CampaignVariant existing = variantRepository.findByIdAndCompanyId(variantId, companyId)
                .filter(v -> v.campaignId() == campaignId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CAMPAIGN_VARIANT_NOT_FOUND, variantId));

        CampaignVariant updated = new CampaignVariant(
                existing.id(),
                campaignId,
                companyId,
                request.name().trim(),
                request.aiAgentId(),
                request.promptOverride(),
                request.ttsVoiceId(),
                request.trafficWeight() != null ? request.trafficWeight() : existing.trafficWeight(),
                existing.callsCount(),
                existing.answeredCount(),
                existing.convertedCount(),
                request.active(),
                existing.createdAt(),
                Instant.now()
        );

        CampaignVariant saved = variantRepository.save(updated);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(long campaignId, long variantId) {
        long companyId = currentCompany.id();
        variantRepository.findByIdAndCompanyId(variantId, companyId)
                .filter(v -> v.campaignId() == campaignId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CAMPAIGN_VARIANT_NOT_FOUND, variantId));
        variantRepository.deleteByIdAndCompanyId(variantId, companyId);
    }

    @Override
    public List<CampaignVariantResponse> list(long campaignId) {
        long companyId = currentCompany.id();
        return variantRepository.findAllByCampaignIdAndCompanyId(campaignId, companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public AbTestReportResponse getReport(long campaignId) {
        long companyId = currentCompany.id();
        List<CampaignVariant> variants = variantRepository.findAllByCampaignIdAndCompanyId(campaignId, companyId);
        int totalCalls = variants.stream().mapToInt(CampaignVariant::callsCount).sum();
        int totalAnswered = variants.stream().mapToInt(CampaignVariant::answeredCount).sum();
        int totalConverted = variants.stream().mapToInt(CampaignVariant::convertedCount).sum();

        double overallConversion = totalAnswered > 0
                ? percent((double) totalConverted / totalAnswered)
                : 0.0;

        // Two different questions, answered separately. "Who is ahead" is a sort and is
        // always answerable; "who won" needs the leader's confidence interval to clear the
        // runner-up's, and on a young campaign the honest answer to it is nobody.
        String leadingVariant = variants.stream()
                .filter(v -> v.answeredCount() > 0)
                .max(Comparator.comparingDouble(CampaignVariant::conversionRate))
                .map(CampaignVariant::name)
                .orElse(null);
        CampaignVariant winner = AbTestSignificance.findWinner(variants);

        List<CampaignVariantResponse> variantResponses = variants.stream()
                .map(this::toResponse)
                .toList();

        return new AbTestReportResponse(
                campaignId,
                companyId,
                variants.size(),
                totalCalls,
                totalAnswered,
                totalConverted,
                overallConversion,
                percent(AbTestSignificance.lowerBound(totalConverted, totalAnswered)),
                percent(AbTestSignificance.upperBound(totalConverted, totalAnswered)),
                leadingVariant,
                winner != null ? winner.name() : null,
                variantResponses
        );
    }

    @Override
    public CampaignVariant findForCall(long companyId, long campaignId, String assignmentKey) {
        return CampaignVariantSelector.selectVariant(
                variantRepository.findAllByCampaignIdAndCompanyId(campaignId, companyId), assignmentKey);
    }

    @Override
    @Transactional
    public void recordCall(long variantId) {
        variantRepository.recordCall(variantId);
    }

    @Override
    @Transactional
    public void recordAnswer(long variantId) {
        variantRepository.recordAnswer(variantId);
    }

    @Override
    @Transactional
    public void recordConversion(long variantId) {
        variantRepository.recordConversion(variantId);
    }

    /** A 0..1 rate as a percentage with two decimals, the shape the report already used. */
    private static double percent(double rate) {
        return Math.round(rate * 10000.0) / 100.0;
    }

    private CampaignVariantResponse toResponse(CampaignVariant v) {
        return new CampaignVariantResponse(
                v.id(),
                v.campaignId(),
                v.companyId(),
                v.name(),
                v.aiAgentId(),
                v.promptOverride(),
                v.ttsVoiceId(),
                v.trafficWeight(),
                v.callsCount(),
                v.answeredCount(),
                v.convertedCount(),
                v.answerRate(),
                v.conversionRate(),
                v.active(),
                v.createdAt(),
                v.updatedAt()
        );
    }
}
