package uz.murodjon.robotcallv2.billing.application.dto;

public record TopupResponse(
        String paymentId,
        String checkoutUrl
) {
}
