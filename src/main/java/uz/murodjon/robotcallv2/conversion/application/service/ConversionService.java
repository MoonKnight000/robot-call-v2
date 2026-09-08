package uz.murodjon.robotcallv2.conversion.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.callrecord.application.service.CallRecordService;
import uz.murodjon.robotcallv2.callrecord.domain.entity.AnsweredCall;
import uz.murodjon.robotcallv2.conversion.application.dto.ConversionGoalRequest;
import uz.murodjon.robotcallv2.conversion.application.dto.ConversionResultResponse;
import uz.murodjon.robotcallv2.conversion.application.dto.ReportConversionRequest;
import uz.murodjon.robotcallv2.conversion.application.port.input.ConversionUseCase;
import uz.murodjon.robotcallv2.conversion.application.port.output.ConversionGoalRepository;
import uz.murodjon.robotcallv2.conversion.application.port.output.ConversionRepository;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionAttribution;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionEvent;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionGoal;
import uz.murodjon.robotcallv2.conversion.domain.entity.VariantConversions;
import uz.murodjon.robotcallv2.conversion.domain.enums.AttributionModel;
import uz.murodjon.robotcallv2.conversion.domain.service.ConversionAttributor;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns "this customer paid" into "that call earned it".
 *
 * <p>Without this a campaign could only be judged on the disposition the bot recorded — a
 * promise to pay, which is not a payment. With it, a variant that gets more promises and
 * fewer payments stops looking like the winner.
 */
@Service
public class ConversionService implements ConversionUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConversionService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConversionGoalRepository goalRepository;
    private final ConversionRepository conversionRepository;
    private final CallRecordService callRecords;

    public ConversionService(ConversionGoalRepository goalRepository,
                             ConversionRepository conversionRepository,
                             CallRecordService callRecords) {
        this.goalRepository = goalRepository;
        this.conversionRepository = conversionRepository;
        this.callRecords = callRecords;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversionGoal> findGoals(long companyId) {
        return goalRepository.findByCompanyId(companyId);
    }

    @Override
    @Transactional
    public ConversionGoal upsertGoal(long companyId, ConversionGoalRequest request) {
        String goalKey = request.goalKey().trim();
        AttributionModel model = request.attributionModel() != null
                ? request.attributionModel()
                : AttributionModel.LAST_CALL;
        boolean enabled = request.enabled() == null || request.enabled();

        Optional<ConversionGoal> existing = goalRepository.findByCompanyIdAndGoalKey(companyId, goalKey);
        ConversionGoal goal = new ConversionGoal(
                existing.map(ConversionGoal::id).orElse(null),
                companyId,
                goalKey,
                request.name().trim(),
                request.attributionWindowHours(),
                model,
                enabled,
                existing.map(ConversionGoal::createdAt).orElse(null),
                null);
        return goalRepository.save(goal);
    }

    @Override
    @Transactional
    public void deleteGoal(long companyId, long id) {
        ConversionGoal goal = goalRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CONVERSION_GOAL_NOT_FOUND, id));
        // Switched off rather than deleted: the events already attributed under it stay
        // readable, and a report that suddenly cannot name its own goal is worse than one
        // naming a goal nobody posts to any more.
        goalRepository.save(new ConversionGoal(goal.id(), goal.companyId(), goal.goalKey(), goal.name(),
                goal.attributionWindowHours(), goal.attributionModel(), false, goal.createdAt(), null));
    }

    @Override
    @Transactional
    public ConversionResultResponse reportConversion(long companyId, ReportConversionRequest request) {
        // The retry answer first: a poster that timed out and sent the same thing again
        // must get back what was decided the first time, not a second attribution.
        Optional<ConversionEvent> alreadyPosted =
                conversionRepository.findEventByDedupeKey(companyId, request.dedupeKey());
        if (alreadyPosted.isPresent()) {
            return describe(alreadyPosted.get(), true);
        }

        ConversionGoal goal = goalRepository.findByCompanyIdAndGoalKey(companyId, request.goalKey().trim())
                .orElseThrow(() -> new ValidationException(ErrorCode.CONVERSION_GOAL_UNKNOWN, request.goalKey()));

        // Normalised to the shape the dialled numbers are stored in; an event posted as
        // "90 123 45 67" has to find a call placed to "+998901234567".
        String phone = PhoneNumbers.normalize(request.phone());

        ConversionEvent event = ConversionEvent.reported(companyId, goal.goalKey(), phone,
                request.occurredAt(), request.valueUzs(), request.source(), toJson(request.evidence()),
                request.dedupeKey());

        if (!goal.enabled()) {
            return saveRejected(event, "goal is switched off");
        }
        if (phone == null || phone.isBlank()) {
            return saveRejected(event, "phone could not be read as a number");
        }

        Instant windowStart = request.occurredAt().minusSeconds((long) goal.attributionWindowHours() * 3600);
        List<AnsweredCall> candidates =
                callRecords.findAnsweredCalls(companyId, phone, windowStart, request.occurredAt());

        AnsweredCall earned = ConversionAttributor.attribute(candidates, request.occurredAt(),
                goal.attributionWindowHours(), goal.attributionModel());
        if (earned == null) {
            return saveRejected(event, "no answered call to this number in the last "
                    + goal.attributionWindowHours() + "h");
        }

        ConversionEvent saved = conversionRepository.saveEvent(event);
        ConversionAttribution attribution = conversionRepository.saveAttribution(
                ConversionAttribution.of(saved.id(), companyId, earned, goal.attributionModel(),
                        goal.attributionWindowHours(), request.valueUzs()));

        log.info("Conversion '{}' for {} credited to call {} (campaign {}, variant {})",
                goal.goalKey(), phone, earned.callAttemptId(), earned.campaignId(), earned.variantId());

        return new ConversionResultResponse(saved.id(), true, null, false,
                attribution.campaignId(), attribution.variantId(), attribution.callAttemptId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<VariantConversions> findVariantConversions(long companyId, long campaignId) {
        return conversionRepository.findConversionsByVariant(companyId, campaignId);
    }

    private ConversionResultResponse saveRejected(ConversionEvent event, String reason) {
        ConversionEvent saved = conversionRepository.saveEvent(event.reject(reason));
        log.info("Conversion '{}' for {} not attributed: {}", saved.goalKey(), saved.phone(), reason);
        return new ConversionResultResponse(saved.id(), false, reason, false, null, null, null);
    }

    /** What a repeated post gets back: whatever the first one decided. */
    private ConversionResultResponse describe(ConversionEvent event, boolean duplicate) {
        return conversionRepository.findAttributionByEventId(event.id())
                .map(attribution -> new ConversionResultResponse(event.id(), true, null, duplicate,
                        attribution.campaignId(), attribution.variantId(), attribution.callAttemptId()))
                .orElseGet(() -> new ConversionResultResponse(event.id(), false, event.rejectionReason(),
                        duplicate, null, null, null));
    }

    private static String toJson(Map<String, Object> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(evidence);
        } catch (JsonProcessingException e) {
            // Evidence is what somebody kept for a later argument, not something the
            // attribution needs; losing it must not cost the conversion.
            log.warn("Conversion evidence could not be serialised: {}", e.getMessage());
            return null;
        }
    }
}
