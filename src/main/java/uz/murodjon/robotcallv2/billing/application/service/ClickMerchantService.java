package uz.murodjon.robotcallv2.billing.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.billing.application.dto.ClickRequest;
import uz.murodjon.robotcallv2.billing.application.dto.ClickResponse;
import uz.murodjon.robotcallv2.billing.application.port.input.ClickMerchantUseCase;
import uz.murodjon.robotcallv2.billing.application.port.output.PaymentProviderTransactionRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.PaymentTopupRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.PaymentProviderTransaction;
import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;
import uz.murodjon.robotcallv2.billing.domain.enums.ClickResult;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.billing.domain.enums.ProviderTransactionState;
import uz.murodjon.robotcallv2.billing.domain.enums.TopupStatus;
import uz.murodjon.robotcallv2.billing.domain.service.ClickRules;
import uz.murodjon.robotcallv2.billing.infrastructure.config.PaymentClickProperties;
import uz.murodjon.robotcallv2.billing.infrastructure.config.PaymentProperties;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;

/**
 * The Click SHOP API: Prepare (action 0) opens a transaction on a top-up, Complete
 * (action 1) credits the balance — or, when Click reports the payment failed, cancels it.
 *
 * <p>Every answer is Click's own JSON, error code included. Nothing here throws for a
 * refusal: Click reads the outcome from the body. Each callback is checked against its md5
 * {@code sign_string} before anything is looked up, and both hold the top-up's row while
 * they decide, so a repeated or concurrent callback waits for the first one.
 */
@Service
public class ClickMerchantService implements ClickMerchantUseCase {

    private static final Logger log = LoggerFactory.getLogger(ClickMerchantService.class);

    private static final int ACTION_PREPARE = 0;
    private static final int ACTION_COMPLETE = 1;

    private final PaymentTopupRepository paymentTopupRepository;
    private final PaymentProviderTransactionRepository paymentProviderTransactionRepository;
    private final TopupSettlementService topupSettlementService;
    private final PaymentProperties paymentProperties;
    private final Clock clock;

    public ClickMerchantService(PaymentTopupRepository paymentTopupRepository,
                                PaymentProviderTransactionRepository paymentProviderTransactionRepository,
                                TopupSettlementService topupSettlementService,
                                PaymentProperties paymentProperties,
                                Clock clock) {
        this.paymentTopupRepository = paymentTopupRepository;
        this.paymentProviderTransactionRepository = paymentProviderTransactionRepository;
        this.topupSettlementService = topupSettlementService;
        this.paymentProperties = paymentProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ClickResponse prepare(Map<String, String> form) {
        ClickRequest request = ClickRequest.fromForm(form);
        if (!isWellFormed(request, false)) {
            return refuse(request, ClickResult.BAD_REQUEST);
        }
        if (!isSignedByClick(request, null)) {
            return refuse(request, ClickResult.SIGN_CHECK_FAILED);
        }
        if (Integer.parseInt(request.action()) != ACTION_PREPARE) {
            return refuse(request, ClickResult.ACTION_NOT_FOUND);
        }
        PaymentTopup topup = paymentTopupRepository.findByPaymentIdForUpdate(request.merchantTransId())
                .filter(ClickMerchantService::isPaidByClick)
                .orElse(null);
        if (topup == null) {
            return refuse(request, ClickResult.TOPUP_NOT_FOUND);
        }
        if (topup.status() == TopupStatus.COMPLETED) {
            return refuse(request, ClickResult.ALREADY_PAID);
        }
        if (!ClickRules.amountMatches(new BigDecimal(request.amount()), topup)) {
            return refuse(request, ClickResult.INCORRECT_AMOUNT);
        }
        if (!topup.isPayable()) {
            return refuse(request, ClickResult.TRANSACTION_CANCELLED);
        }

        // Click repeats a Prepare it got no answer to: the same transaction, not a second one.
        PaymentProviderTransaction transaction = paymentProviderTransactionRepository
                .findByProviderTransactionIdForUpdate(PaymentMethod.CLICK, request.clickTransId())
                .orElse(null);
        if (transaction == null) {
            transaction = paymentProviderTransactionRepository.save(
                    PaymentProviderTransaction.start(PaymentMethod.CLICK, request.clickTransId(), topup, null));
            log.info("Click transaction {} prepared on top-up {} ({} UZS)",
                    request.clickTransId(), topup.paymentId(), topup.amountUzs());
        } else if (transaction.state() == ProviderTransactionState.CANCELLED) {
            return refuse(request, ClickResult.TRANSACTION_CANCELLED);
        }
        return new ClickResponse(Long.parseLong(request.clickTransId()), request.merchantTransId(),
                transaction.id(), null, ClickResult.SUCCESS.code(), ClickResult.SUCCESS.note());
    }

    @Override
    @Transactional
    public ClickResponse complete(Map<String, String> form) {
        ClickRequest request = ClickRequest.fromForm(form);
        if (!isWellFormed(request, true)) {
            return refuse(request, ClickResult.BAD_REQUEST);
        }
        if (!isSignedByClick(request, request.merchantPrepareId())) {
            return refuse(request, ClickResult.SIGN_CHECK_FAILED);
        }
        if (Integer.parseInt(request.action()) != ACTION_COMPLETE) {
            return refuse(request, ClickResult.ACTION_NOT_FOUND);
        }
        PaymentTopup topup = paymentTopupRepository.findByPaymentIdForUpdate(request.merchantTransId())
                .filter(ClickMerchantService::isPaidByClick)
                .orElse(null);
        if (topup == null) {
            return refuse(request, ClickResult.TOPUP_NOT_FOUND);
        }
        PaymentProviderTransaction transaction = paymentProviderTransactionRepository
                .findByIdForUpdate(Long.parseLong(request.merchantPrepareId()))
                .filter(found -> found.provider() == PaymentMethod.CLICK
                        && found.providerTransactionId().equals(request.clickTransId())
                        && found.topupId() == topup.id())
                .orElse(null);
        if (transaction == null) {
            return refuse(request, ClickResult.TRANSACTION_NOT_FOUND);
        }
        if (transaction.state() == ProviderTransactionState.PERFORMED) {
            return refuse(request, ClickResult.ALREADY_PAID);
        }
        if (transaction.state() == ProviderTransactionState.CANCELLED) {
            return refuse(request, ClickResult.TRANSACTION_CANCELLED);
        }
        if (!ClickRules.amountMatches(new BigDecimal(request.amount()), topup)) {
            return refuse(request, ClickResult.INCORRECT_AMOUNT);
        }

        int clickError = Integer.parseInt(request.error());
        if (clickError < 0) {
            // Click could not take the money (card declined, not enough funds…): the payment
            // is off, and the top-up with it, as when Payme cancels one.
            paymentProviderTransactionRepository.save(transaction.cancel(clock.instant(), clickError));
            paymentTopupRepository.cancelById(topup.id());
            log.info("Click transaction {} on top-up {} cancelled by Click: {} {}",
                    request.clickTransId(), topup.paymentId(), clickError, request.errorNote());
            return refuse(request, ClickResult.TRANSACTION_CANCELLED);
        }
        // Paid meanwhile through another Click transaction, or closed — nothing to credit.
        if (topup.status() == TopupStatus.COMPLETED) {
            return refuse(request, ClickResult.ALREADY_PAID);
        }
        if (!topup.isSettleable() || !topupSettlementService.complete(topup)) {
            return refuse(request, ClickResult.TRANSACTION_CANCELLED);
        }
        PaymentProviderTransaction performed = paymentProviderTransactionRepository.save(
                transaction.perform(clock.instant()));
        return new ClickResponse(Long.parseLong(request.clickTransId()), request.merchantTransId(),
                null, performed.id(), ClickResult.SUCCESS.code(), ClickResult.SUCCESS.note());
    }

    /** The sign Click sent over this request and our secret key; Complete's includes merchant_prepare_id. */
    private boolean isSignedByClick(ClickRequest request, String merchantPrepareId) {
        PaymentClickProperties click = paymentProperties.click();
        if (!click.configured()) {
            return false;
        }
        String expected = ClickRules.sign(request.clickTransId(), request.serviceId(), click.secretKey(),
                request.merchantTransId(), merchantPrepareId, request.amount(), request.action(), request.signTime());
        return ClickRules.signMatches(expected, request.signString());
    }

    /** Every field this callback needs is there, and the numeric ones are numbers. */
    private static boolean isWellFormed(ClickRequest request, boolean complete) {
        return isLong(request.clickTransId())
                && isPresent(request.serviceId())
                && isPresent(request.merchantTransId())
                && isDecimal(request.amount())
                && isInteger(request.action())
                && isPresent(request.signTime())
                && isPresent(request.signString())
                && (!complete || (isLong(request.merchantPrepareId()) && isInteger(request.error())));
    }

    private static ClickResponse refuse(ClickRequest request, ClickResult result) {
        Long clickTransId = isLong(request.clickTransId()) ? Long.parseLong(request.clickTransId()) : null;
        return new ClickResponse(clickTransId, request.merchantTransId(), null, null, result.code(), result.note());
    }

    /** A top-up raised for Payme or a bank transfer is not Click's to pay: it is "not found" there. */
    private static boolean isPaidByClick(PaymentTopup topup) {
        return topup.paymentMethod() == PaymentMethod.CLICK;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDecimal(String value) {
        if (value == null) {
            return false;
        }
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
