package uz.murodjon.robotcallv2.billing.domain.service;

import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * The Click SHOP API's rules, free of Spring and storage: the {@code sign_string} every
 * callback carries, and what counts as the right amount.
 */
public final class ClickRules {

    private ClickRules() {
    }

    /**
     * {@code md5(click_trans_id + service_id + secret_key + merchant_trans_id
     * [+ merchant_prepare_id] + amount + action + sign_time)}, in lowercase hex.
     *
     * <p>Every value goes in exactly as Click sent it — {@code amount} included: normalising
     * {@code "1000.00"} to {@code "1000"} first would make the sign never match.
     *
     * @param merchantPrepareId null for Prepare, which is signed without it
     */
    public static String sign(String clickTransId, String serviceId, String secretKey, String merchantTransId,
                              String merchantPrepareId, String amount, String action, String signTime) {
        String payload = clickTransId + serviceId + secretKey + merchantTransId
                + (merchantPrepareId != null ? merchantPrepareId : "") + amount + action + signTime;
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md5.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Every JVM ships MD5; reaching here means the platform is broken.
            throw new IllegalStateException("MD5 is unavailable", e);
        }
    }

    /**
     * Whether the sign Click sent is the one computed here. Constant time, for the same
     * reason as every other secret comparison: an early "no" leaks how much was right.
     */
    public static boolean signMatches(String expectedSign, String presentedSign) {
        if (expectedSign == null || presentedSign == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedSign.getBytes(StandardCharsets.UTF_8),
                presentedSign.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    /** Whether Click's {@code amount}, in UZS and possibly with decimals, is exactly what the top-up is for. */
    public static boolean amountMatches(BigDecimal amountUzs, PaymentTopup topup) {
        return amountUzs != null && amountUzs.compareTo(BigDecimal.valueOf(topup.amountUzs())) == 0;
    }
}
