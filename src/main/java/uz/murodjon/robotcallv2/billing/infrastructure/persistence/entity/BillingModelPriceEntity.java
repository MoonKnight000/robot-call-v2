package uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** One model's price list row (V42). */
@Entity
@Table(name = "billing_model_price")
public class BillingModelPriceEntity {

    @Id
    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "input_per_million_uzs", nullable = false)
    private Long inputPerMillionUzs;

    @Column(name = "output_per_million_uzs", nullable = false)
    private Long outputPerMillionUzs;

    @Column(name = "cached_per_million_uzs", nullable = false)
    private Long cachedPerMillionUzs;

    @Column(name = "realtime_per_minute_uzs")
    private Long realtimePerMinuteUzs;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Long getInputPerMillionUzs() { return inputPerMillionUzs; }
    public void setInputPerMillionUzs(Long inputPerMillionUzs) { this.inputPerMillionUzs = inputPerMillionUzs; }
    public Long getOutputPerMillionUzs() { return outputPerMillionUzs; }
    public void setOutputPerMillionUzs(Long outputPerMillionUzs) { this.outputPerMillionUzs = outputPerMillionUzs; }
    public Long getCachedPerMillionUzs() { return cachedPerMillionUzs; }
    public void setCachedPerMillionUzs(Long cachedPerMillionUzs) { this.cachedPerMillionUzs = cachedPerMillionUzs; }
    public Long getRealtimePerMinuteUzs() { return realtimePerMinuteUzs; }
    public void setRealtimePerMinuteUzs(Long realtimePerMinuteUzs) { this.realtimePerMinuteUzs = realtimePerMinuteUzs; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
