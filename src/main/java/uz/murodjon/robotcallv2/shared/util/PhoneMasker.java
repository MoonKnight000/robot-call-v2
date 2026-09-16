package uz.murodjon.robotcallv2.shared.util;

/**
 * A phone number as it may appear in a log line: enough to tell two callers apart when
 * reading a trace, not enough to dial. Logs are kept for two weeks on disk and shipped to
 * whoever runs the box; a full E.164 number there is a PII copy nobody asked for.
 */
public final class PhoneMasker {

    private PhoneMasker() {
    }

    /** {@code +998901234567} → {@code +99890*****67}; short or blank values pass through unchanged. */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        int keepHead = phone.startsWith("+") ? 6 : 5;
        int keepTail = 2;
        if (phone.length() <= keepHead + keepTail) {
            return phone;
        }
        return phone.substring(0, keepHead)
                + "*".repeat(phone.length() - keepHead - keepTail)
                + phone.substring(phone.length() - keepTail);
    }
}
