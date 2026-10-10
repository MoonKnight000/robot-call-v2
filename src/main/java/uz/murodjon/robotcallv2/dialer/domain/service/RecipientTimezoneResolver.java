package uz.murodjon.robotcallv2.dialer.domain.service;

import java.time.ZoneId;

/**
 * Resolves the recipient's geographic timezone from their E.164 telephone number prefix.
 * Used to enforce local daytime calling hours (quiet hours / TCPA compliance)
 * across regional and international destinations.
 */
public final class RecipientTimezoneResolver {

    private static final ZoneId DEFAULT_UZ_ZONE = ZoneId.of("Asia/Tashkent");

    private RecipientTimezoneResolver() {
    }

    public static ZoneId resolve(String phone, ZoneId defaultZone) {
        if (phone == null || phone.isBlank()) {
            return defaultZone != null ? defaultZone : DEFAULT_UZ_ZONE;
        }
        String p = phone.trim();
        if (p.startsWith("+")) {
            p = p.substring(1);
        }

        // Uzbekistan: +998 (all regions are UTC+5 / Asia/Tashkent)
        if (p.startsWith("998")) {
            return DEFAULT_UZ_ZONE;
        }

        // Kazakhstan: +76, +77 -> Asia/Almaty (UTC+5)
        if (p.startsWith("76") || p.startsWith("77")) {
            return ZoneId.of("Asia/Almaty");
        }

        // Russia: +7 -> Europe/Moscow (default UTC+3)
        if (p.startsWith("7")) {
            return ZoneId.of("Europe/Moscow");
        }

        // Kyrgyzstan: +996 -> Asia/Bishkek (UTC+6)
        if (p.startsWith("996")) {
            return ZoneId.of("Asia/Bishkek");
        }

        // Tajikistan: +992 -> Asia/Dushanbe (UTC+5)
        if (p.startsWith("992")) {
            return ZoneId.of("Asia/Dushanbe");
        }

        // Turkmenistan: +993 -> Asia/Ashgabat (UTC+5)
        if (p.startsWith("993")) {
            return ZoneId.of("Asia/Ashgabat");
        }

        // Azerbaijan: +994 -> Asia/Baku (UTC+4)
        if (p.startsWith("994")) {
            return ZoneId.of("Asia/Baku");
        }

        // United Arab Emirates: +971 -> Asia/Dubai (UTC+4)
        if (p.startsWith("971")) {
            return ZoneId.of("Asia/Dubai");
        }

        // Turkey: +90 -> Europe/Istanbul (UTC+3)
        if (p.startsWith("90")) {
            return ZoneId.of("Europe/Istanbul");
        }

        // United Kingdom: +44 -> Europe/London (UTC+0)
        if (p.startsWith("44")) {
            return ZoneId.of("Europe/London");
        }

        // Germany: +49 -> Europe/Berlin (UTC+1)
        if (p.startsWith("49")) {
            return ZoneId.of("Europe/Berlin");
        }

        // USA / Canada: +1 -> America/New_York (UTC-5)
        if (p.startsWith("1")) {
            return ZoneId.of("America/New_York");
        }

        return defaultZone != null ? defaultZone : DEFAULT_UZ_ZONE;
    }
}
