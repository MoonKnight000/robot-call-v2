package uz.murodjon.robotcallv2.billing.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uz.murodjon.robotcallv2.billing.application.dto.*;
import uz.murodjon.robotcallv2.billing.application.mapper.PaymeMapper;
import uz.murodjon.robotcallv2.billing.application.port.input.PaymeMerchantUseCase;
import uz.murodjon.robotcallv2.billing.application.port.output.PaymentProviderTransactionRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.PaymentTopupRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.PaymentProviderTransaction;
import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymeError;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.billing.domain.enums.ProviderTransactionState;
import uz.murodjon.robotcallv2.billing.domain.service.PaymeRules;
import uz.murodjon.robotcallv2.billing.infrastructure.config.PaymentPaymeProperties;
import uz.murodjon.robotcallv2.billing.infrastructure.config.PaymentProperties;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The Payme Merchant API (JSON-RPC 2.0): Payme asks whether a top-up may be paid, opens a
 * transaction on it, and then either performs it — the moment the balance is credited — or
 * cancels it.
 *
 * <p>Every answer is Payme's own JSON-RPC body. Nothing here throws for a refusal: Payme
 * reads the outcome from the body and takes any status but 200 as its own failure to reach
 * us. Each call runs in a transaction of its own; if storage fails inside it, the whole
 * call rolls back and Payme gets -32400 and asks again.
 *
 * <p>What makes a repeated call safe: a transaction is keyed by Payme's id (unique per
 * provider) and read under a row lock, so a repeat waits for the first call and then sees
 * what it did; and Perform credits only through {@link TopupSettlementService#complete},
 * which credits a top-up at most once.
 */
@Service
public class PaymeMerchantService implements PaymeMerchantUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymeMerchantService.class);

    /** A field this code does not read is no reason to refuse the call. */
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** The account field a top-up is identified by; account errors name it in {@code data}. */
    private static final String ACCOUNT_FIELD = "payment_id";

    private final PaymentTopupRepository paymentTopupRepository;
    private final PaymentProviderTransactionRepository paymentProviderTransactionRepository;
    private final TopupSettlementService topupSettlementService;
    private final PaymeMapper paymeMapper;
    private final PaymentProperties paymentProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PaymeMerchantService(PaymentTopupRepository paymentTopupRepository,
                                PaymentProviderTransactionRepository paymentProviderTransactionRepository,
                                TopupSettlementService topupSettlementService,
                                PaymeMapper paymeMapper,
                                PaymentProperties paymentProperties,
                                PlatformTransactionManager transactionManager,
                                Clock clock) {
        this.paymentTopupRepository = paymentTopupRepository;
        this.paymentProviderTransactionRepository = paymentProviderTransactionRepository;
        this.topupSettlementService = topupSettlementService;
        this.paymeMapper = paymeMapper;
        this.paymentProperties = paymentProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public PaymeResponse handle(String authorization, String body) {
        PaymeRequest request = parse(body);
        Object id = request != null ? request.id() : null;
        // Before anything in the body is acted on: an unauthenticated caller learns nothing
        // about which top-ups or transactions exist.
        PaymentPaymeProperties payme = paymentProperties.payme();
        if (!payme.configured() || !PaymeRules.isAuthorized(authorization, payme.key())) {
            return PaymeResponse.ofError(id, PaymeError.INSUFFICIENT_PRIVILEGE, null);
        }
        if (request == null) {
            return PaymeResponse.ofError(null, PaymeError.PARSE_ERROR, null);
        }
        if (request.method() == null || request.params() == null) {
            return PaymeResponse.ofError(id, PaymeError.INVALID_REQUEST, null);
        }
        try {
            return transactionTemplate.execute(status -> dispatch(request));
        } catch (RuntimeException e) {
            log.error("Payme {} failed: {}", request.method(), e.getMessage(), e);
            return PaymeResponse.ofError(id, PaymeError.SYSTEM_ERROR, null);
        }
    }

    private PaymeResponse dispatch(PaymeRequest request) {
        Object id = request.id();
        PaymeParams params = request.params();
        return switch (request.method()) {
            case "CheckPerformTransaction" -> checkPerformTransaction(id, params);
            case "CreateTransaction" -> createTransaction(id, params);
            case "PerformTransaction" -> performTransaction(id, params);
            case "CancelTransaction" -> cancelTransaction(id, params);
            case "CheckTransaction" -> checkTransaction(id, params);
            case "GetStatement" -> getStatement(id, params);
            default -> PaymeResponse.ofError(id, PaymeError.METHOD_NOT_FOUND, request.method());
        };
    }

    private PaymeResponse checkPerformTransaction(Object id, PaymeParams params) {
        String paymentId = paymentIdOf(params);
        if (params.amount() == null || paymentId == null) {
            return PaymeResponse.ofError(id, PaymeError.INVALID_REQUEST, null);
        }
        PaymentTopup topup = paymentTopupRepository.findByPaymentId(paymentId)
                .filter(PaymeMerchantService::isPaidByPayme)
                .orElse(null);
        PaymeError refusal = findRefusal(topup, params.amount());
        if (refusal != null) {
            return refuse(id, refusal);
        }
        PaymentPaymeProperties payme = paymentProperties.payme();
        PaymeReceiptDetail detail = payme.sendsReceipt()
                ? paymeMapper.toReceiptDetail(topup, payme.ikpuCode(), payme.packageCode(), payme.vatPercent())
                : null;
        return PaymeResponse.ofResult(id, new PaymeCheckPerformResult(true, detail));
    }

    private PaymeResponse createTransaction(Object id, PaymeParams params) {
        String paymentId = paymentIdOf(params);
        if (params.id() == null || params.time() == null || params.amount() == null || paymentId == null) {
            return PaymeResponse.ofError(id, PaymeError.INVALID_REQUEST, null);
        }
        Optional<PaymentProviderTransaction> existing = findTransaction(params.id());
        if (existing.isPresent()) {
            return answerRepeatedCreate(id, existing.get());
        }

        PaymentTopup topup = paymentTopupRepository.findByPaymentIdForUpdate(paymentId)
                .filter(PaymeMerchantService::isPaidByPayme)
                .orElse(null);
        // Looked up again now that the top-up is held: a repeat of this same call that was a
        // step ahead may have committed while this one waited for the lock.
        existing = findTransaction(params.id());
        if (existing.isPresent()) {
            return answerRepeatedCreate(id, existing.get());
        }
        PaymeError refusal = findRefusal(topup, params.amount());
        if (refusal != null) {
            return refuse(id, refusal);
        }

        PaymentProviderTransaction created = paymentProviderTransactionRepository.save(PaymentProviderTransaction.start(
                PaymentMethod.PAYME, params.id(), topup, Instant.ofEpochMilli(params.time())));
        log.info("Payme transaction {} opened on top-up {} ({} UZS)", params.id(), topup.paymentId(), topup.amountUzs());
        return PaymeResponse.ofResult(id, paymeMapper.toCreateResult(created));
    }

    /** CreateTransaction for an id already seen: the same answer again, unless it can no longer be performed. */
    private PaymeResponse answerRepeatedCreate(Object id, PaymentProviderTransaction transaction) {
        if (transaction.state() != ProviderTransactionState.CREATED) {
            return PaymeResponse.ofError(id, PaymeError.CANNOT_PERFORM, null);
        }
        if (PaymeRules.isTimedOut(transaction, clock.instant())) {
            cancel(transaction, PaymeRules.REASON_TIMEOUT);
            return PaymeResponse.ofError(id, PaymeError.CANNOT_PERFORM, null);
        }
        return PaymeResponse.ofResult(id, paymeMapper.toCreateResult(transaction));
    }

    private PaymeResponse performTransaction(Object id, PaymeParams params) {
        if (params.id() == null) {
            return PaymeResponse.ofError(id, PaymeError.INVALID_REQUEST, null);
        }
        Optional<PaymentProviderTransaction> found = findTransaction(params.id());
        if (found.isEmpty()) {
            return PaymeResponse.ofError(id, PaymeError.TRANSACTION_NOT_FOUND, null);
        }
        PaymentProviderTransaction transaction = found.get();
        return switch (transaction.state()) {
            case CREATED -> performCreated(id, transaction);
            // Payme repeats a Perform it got no answer to; the balance was credited the first time.
            case PERFORMED -> PaymeResponse.ofResult(id, paymeMapper.toPerformResult(transaction));
            case CANCELLED -> PaymeResponse.ofError(id, PaymeError.CANNOT_PERFORM, null);
        };
    }

    private PaymeResponse performCreated(Object id, PaymentProviderTransaction transaction) {
        if (PaymeRules.isTimedOut(transaction, clock.instant())) {
            cancel(transaction, PaymeRules.REASON_TIMEOUT);
            return PaymeResponse.ofError(id, PaymeError.CANNOT_PERFORM, null);
        }
        // An EXPIRED top-up is still credited: this payment started before it expired, and
        // money that landed is never refused. One already paid or cancelled is not.
        boolean credited = paymentTopupRepository.findByPaymentId(transaction.paymentId())
                .map(topupSettlementService::complete)
                .orElse(false);
        if (!credited) {
            return PaymeResponse.ofError(id, PaymeError.CANNOT_PERFORM, null);
        }
        PaymentProviderTransaction performed = paymentProviderTransactionRepository.save(
                transaction.perform(clock.instant()));
        return PaymeResponse.ofResult(id, paymeMapper.toPerformResult(performed));
    }

    private PaymeResponse cancelTransaction(Object id, PaymeParams params) {
        if (params.id() == null || params.reason() == null) {
            return PaymeResponse.ofError(id, PaymeError.INVALID_REQUEST, null);
        }
        Optional<PaymentProviderTransaction> found = findTransaction(params.id());
        if (found.isEmpty()) {
            return PaymeResponse.ofError(id, PaymeError.TRANSACTION_NOT_FOUND, null);
        }
        PaymentProviderTransaction transaction = found.get();
        return switch (transaction.state()) {
            case CREATED -> PaymeResponse.ofResult(id,
                    paymeMapper.toCancelResult(cancel(transaction, params.reason())));
            // The balance is credited and may already be spent on calls: a refund is for a
            // person to decide, not for a callback.
            case PERFORMED -> PaymeResponse.ofError(id, PaymeError.CANNOT_CANCEL, null);
            case CANCELLED -> PaymeResponse.ofResult(id, paymeMapper.toCancelResult(transaction));
        };
    }

    private PaymeResponse checkTransaction(Object id, PaymeParams params) {
        if (params.id() == null) {
            return PaymeResponse.ofError(id, PaymeError.INVALID_REQUEST, null);
        }
        return findTransaction(params.id())
                .map(transaction -> PaymeResponse.ofResult(id, paymeMapper.toCheckResult(transaction)))
                .orElseGet(() -> PaymeResponse.ofError(id, PaymeError.TRANSACTION_NOT_FOUND, null));
    }

    private PaymeResponse getStatement(Object id, PaymeParams params) {
        if (params.from() == null || params.to() == null) {
            return PaymeResponse.ofError(id, PaymeError.INVALID_REQUEST, null);
        }
        List<PaymeStatementTransaction> transactions = paymentProviderTransactionRepository
                .findByProviderAndProviderTimeBetween(PaymentMethod.PAYME,
                        Instant.ofEpochMilli(params.from()), Instant.ofEpochMilli(params.to()))
                .stream()
                .map(paymeMapper::toStatementTransaction)
                .toList();
        return PaymeResponse.ofResult(id, new PaymeStatementResult(transactions));
    }

    /** Why a new payment may not start on this top-up for this amount, or null when it may. */
    private PaymeError findRefusal(PaymentTopup topup, BigDecimal amountTiyin) {
        if (topup == null) {
            return PaymeError.TOPUP_NOT_FOUND;
        }
        if (!PaymeRules.amountMatches(amountTiyin, topup)) {
            return PaymeError.INVALID_AMOUNT;
        }
        if (!topup.isPayable()) {
            return PaymeError.TOPUP_NOT_PAYABLE;
        }
        if (paymentProviderTransactionRepository.existsByTopupIdAndState(topup.id(), ProviderTransactionState.CREATED)) {
            return PaymeError.TOPUP_BUSY;
        }
        return null;
    }

    /** Cancels a transaction still in state 1, and the top-up it was paying along with it. */
    private PaymentProviderTransaction cancel(PaymentProviderTransaction transaction, int reason) {
        PaymentProviderTransaction cancelled = paymentProviderTransactionRepository.save(
                transaction.cancel(clock.instant(), reason));
        paymentTopupRepository.cancelById(transaction.topupId());
        log.info("Payme transaction {} on top-up {} cancelled (reason {})",
                transaction.providerTransactionId(), transaction.paymentId(), reason);
        return cancelled;
    }

    private Optional<PaymentProviderTransaction> findTransaction(String paymeTransactionId) {
        return paymentProviderTransactionRepository.findByProviderTransactionIdForUpdate(
                PaymentMethod.PAYME, paymeTransactionId);
    }

    private static PaymeResponse refuse(Object id, PaymeError refusal) {
        return PaymeResponse.ofError(id, refusal, refusal.isAccountError() ? ACCOUNT_FIELD : null);
    }

    private static String paymentIdOf(PaymeParams params) {
        return params.account() != null ? params.account().paymentId() : null;
    }

    /** A top-up raised for Click or a bank transfer is not Payme's to pay: it is "not found" there. */
    private static boolean isPaidByPayme(PaymentTopup topup) {
        return topup.paymentMethod() == PaymentMethod.PAYME;
    }

    /** The request, or null when the body is empty or not the JSON-RPC shape. */
    private static PaymeRequest parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(body, PaymeRequest.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
