package uz.murodjon.robotcallv2.billing.domain.service;

import uz.murodjon.robotcallv2.billing.domain.entity.PaymentProviderTransaction;
import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;
import uz.murodjon.robotcallv2.billing.domain.enums.ProviderTransactionState;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * The Payme Merchant API's own rules, free of Spring and storage: who may call, what counts
 * as the right amount, when a transaction is too old to perform, and how a state is
 * numbered on the wire.
 */
public final class PaymeRules {

    /** Payme gives up on a transaction left in state 1 for longer than this: 12 hours. */
    public static final long TIMEOUT_MILLIS = 43_200_000L;

    /** Payme's cancel reason for a transaction that outlived {@link #TIMEOUT_MILLIS}. */
    public static final int REASON_TIMEOUT = 4;

    private static final String BASIC_PREFIX = "Basic ";
    private static final String LOGIN = "Paycom";

    private PaymeRules() {
    }

    /**
     * Whether {@code authorizationHeader} is {@code Basic base64(Paycom:<key>)}.
     *
     * <p>Compared in constant time: a "no" that comes back sooner on the first wrong byte
     * tells a caller how much of the key it already has right.
     */
    public static boolean isAuthorized(String authorizationHeader, String key) {
        if (authorizationHeader == null || key == null || key.isBlank()
                || !authorizationHeader.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
            return false;
        }
        byte[] presented;
        try {
            presented = Base64.getDecoder().decode(authorizationHeader.substring(BASIC_PREFIX.length()).trim());
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
        return MessageDigest.isEqual((LOGIN + ":" + key).getBytes(StandardCharsets.UTF_8), presented);
    }

    /** Whether Payme's {@code amount}, in tiyin, is exactly what the top-up is for. */
    public static boolean amountMatches(BigDecimal amountTiyin, PaymentTopup topup) {
        return amountTiyin != null && amountTiyin.compareTo(BigDecimal.valueOf(topup.amountTiyin())) == 0;
    }

    /** Whether a transaction still in state 1 has outlived Payme's timeout, and must be cancelled rather than performed. */
    public static boolean isTimedOut(PaymentProviderTransaction transaction, Instant now) {
        return transaction.state() == ProviderTransactionState.CREATED
                && Duration.between(transaction.createdAt(), now).toMillis() > TIMEOUT_MILLIS;
    }

    /** Payme's number for a state: 1 created, 2 performed, -1 cancelled before it was performed. */
    public static int stateCode(ProviderTransactionState state) {
        return switch (state) {
            case CREATED -> 1;
            case PERFORMED -> 2;
            case CANCELLED -> -1;
        };
    }
}
