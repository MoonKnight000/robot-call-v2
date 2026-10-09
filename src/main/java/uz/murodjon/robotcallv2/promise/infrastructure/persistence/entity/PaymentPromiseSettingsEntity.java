package uz.murodjon.robotcallv2.promise.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "payment_promise_settings")
public class PaymentPromiseSettingsEntity {

    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "reminder_enabled", nullable = false)
    private boolean reminderEnabled;

    @Column(name = "reminder_days_before", nullable = false)
    private int reminderDaysBefore;

    @Column(name = "reminder_template", nullable = false, length = 300)
    private String reminderTemplate;

    @Column(name = "grace_days", nullable = false)
    private int graceDays;

    @Column(name = "recall_on_broken", nullable = false)
    private boolean recallOnBroken;

    @Column(name = "payment_goal_key", length = 64)
    private String paymentGoalKey;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        updatedAt = Instant.now();
    }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public boolean isReminderEnabled() { return reminderEnabled; }
    public void setReminderEnabled(boolean reminderEnabled) { this.reminderEnabled = reminderEnabled; }
    public int getReminderDaysBefore() { return reminderDaysBefore; }
    public void setReminderDaysBefore(int reminderDaysBefore) { this.reminderDaysBefore = reminderDaysBefore; }
    public String getReminderTemplate() { return reminderTemplate; }
    public void setReminderTemplate(String reminderTemplate) { this.reminderTemplate = reminderTemplate; }
    public int getGraceDays() { return graceDays; }
    public void setGraceDays(int graceDays) { this.graceDays = graceDays; }
    public boolean isRecallOnBroken() { return recallOnBroken; }
    public void setRecallOnBroken(boolean recallOnBroken) { this.recallOnBroken = recallOnBroken; }
    public String getPaymentGoalKey() { return paymentGoalKey; }
    public void setPaymentGoalKey(String paymentGoalKey) { this.paymentGoalKey = paymentGoalKey; }
}
