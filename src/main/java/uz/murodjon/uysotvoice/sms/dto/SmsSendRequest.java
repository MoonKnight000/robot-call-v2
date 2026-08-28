package uz.murodjon.uysotvoice.sms.dto;

public record SmsSendRequest(
        String phone,
        String message,
        Long companyId,
        String provider
) {
}
