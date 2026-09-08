package uz.murodjon.robotcallv2.conversion.application.port.output;

import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionAttribution;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionEvent;
import uz.murodjon.robotcallv2.conversion.domain.entity.VariantConversions;

import java.util.List;
import java.util.Optional;

/** Reported events and the calls they were credited to. */
public interface ConversionRepository {

    ConversionEvent saveEvent(ConversionEvent event);

    /**
     * The event already written under that key, if any.
     *
     * <p>The poster's webhook may be retried, so ingest looks here first and returns what
     * was decided the first time rather than counting the same payment twice.
     */
    Optional<ConversionEvent> findEventByDedupeKey(long companyId, String dedupeKey);

    ConversionAttribution saveAttribution(ConversionAttribution attribution);

    Optional<ConversionAttribution> findAttributionByEventId(long conversionEventId);

    /**
     * Conversions credited to one campaign, grouped by the A/B variant that earned them,
     * plus a row with a null variant for calls that ran none.
     */
    List<VariantConversions> findConversionsByVariant(long companyId, long campaignId);
}
