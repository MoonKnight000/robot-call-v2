package uz.murodjon.robotcallv2.sms.domain.entity;

import uz.murodjon.robotcallv2.sms.domain.enums.SmsStatus;

import java.time.Instant;

public class SmsMessage {
    private String phone;
    private String message;
    private SmsStatus status;
    private Instant createdAt;

    public SmsMessage(String phone, String message) {
        this.phone = phone;
        this.message = message;
        this.status = SmsStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markSent() {
        this.status = SmsStatus.SENT;
    }

    public void markFailed() {
        this.status = SmsStatus.FAILED;
    }

    public String getPhone() {
        return phone;
    }

    public String getMessage() {
        return message;
    }

    public SmsStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
