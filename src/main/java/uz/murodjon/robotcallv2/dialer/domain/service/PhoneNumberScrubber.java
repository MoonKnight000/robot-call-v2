package uz.murodjon.robotcallv2.dialer.domain.service;

import uz.murodjon.robotcallv2.dialer.domain.entity.ScrubResult;
import uz.murodjon.robotcallv2.dialer.domain.enums.ScrubStatus;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates and scrubs telephone numbers before campaign call origination.
 * Screens out invalid formats and unallocated operator/regional prefixes in Uzbekistan
 * to avoid wasting trunk channels and incurring carrier reject fees.
 */
public class PhoneNumberScrubber {

    private static final Pattern INTERNAL_EXTENSION = Pattern.compile("^\\d{3,4}$");

    /** Known valid Uzbekistan mobile and regional PSTN prefixes with operator names. */
    private static final Map<String, String> UZ_PREFIXES = Map.ofEntries(
            Map.entry("90", "Beeline"),
            Map.entry("91", "Beeline"),
            Map.entry("93", "Ucell"),
            Map.entry("94", "Ucell"),
            Map.entry("95", "Uztelecom"),
            Map.entry("97", "Mobiuz"),
            Map.entry("88", "Mobiuz"),
            Map.entry("98", "Perfectum"),
            Map.entry("99", "Uztelecom GSM"),
            Map.entry("77", "Uztelecom GSM"),
            Map.entry("33", "Humans"),
            Map.entry("20", "Oq"),
            Map.entry("71", "Tashkent City"),
            Map.entry("78", "Tashkent Commercial"),
            Map.entry("72", "Jizzakh"),
            Map.entry("73", "Fergana"),
            Map.entry("74", "Andijan"),
            Map.entry("75", "Kashkadarya"),
            Map.entry("76", "Surkhandarya"),
            Map.entry("79", "Navoi"),
            Map.entry("61", "Karakalpakstan"),
            Map.entry("62", "Khorezm"),
            Map.entry("65", "Bukhara"),
            Map.entry("66", "Samarkand"),
            Map.entry("67", "Syrdarya"),
            Map.entry("69", "Namangan")
    );

    public ScrubResult scrub(String raw) {
        if (raw == null || raw.isBlank()) {
            return ScrubResult.invalid(ScrubStatus.INVALID_FORMAT, "Phone number is blank");
        }
        String trimmed = raw.trim();
        if (INTERNAL_EXTENSION.matcher(trimmed).matches()) {
            return ScrubResult.valid(trimmed, "Internal Test Extension");
        }

        String normalized = PhoneNumbers.normalize(trimmed);
        if (normalized == null) {
            return ScrubResult.invalid(ScrubStatus.INVALID_FORMAT, "Invalid phone number format: " + trimmed);
        }

        // Uzbekistan numbering plan: +998 followed by 9 digits
        if (normalized.startsWith("+998")) {
            if (normalized.length() != 13) {
                return ScrubResult.invalid(ScrubStatus.INVALID_FORMAT, "Uzbekistan phone number must have 9 digits after +998");
            }
            String prefix = normalized.substring(4, 6);
            String operatorOrRegion = UZ_PREFIXES.get(prefix);
            if (operatorOrRegion == null) {
                return ScrubResult.invalid(ScrubStatus.UNALLOCATED_PREFIX, "Unallocated Uzbekistan prefix: +998" + prefix);
            }
            return ScrubResult.valid(normalized, operatorOrRegion);
        }

        // International number: +[country_code][subscriber_number], 10-15 chars total
        if (normalized.startsWith("+") && normalized.length() >= 10 && normalized.length() <= 16) {
            return ScrubResult.valid(normalized, "International");
        }

        return ScrubResult.invalid(ScrubStatus.INVALID_FORMAT, "Unsupported or malformed international number: " + normalized);
    }
}
