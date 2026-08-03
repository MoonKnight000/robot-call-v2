package uz.murodjon.uysotvoice.notification.enums;

/**
 * {@code notification.type} (UI-DESIGN §8.2 popover toggles). Producers wired:
 * {@code OPERATOR_REQUEST} ({@code AriService.transferToOperator}),
 * {@code ERROR_OCCURRED} ({@code AlertingService.checkSuccessRate}),
 * {@code CAMPAIGN_FINISHED} ({@code CampaignService#checkCompletion}, fired once a
 * campaign's last target leaves the dial loop) and {@code DAILY_REPORT}
 * ({@code ReportScheduleService#sendOne}, fired after a DAILY-periodicity schedule
 * sends).
 */
public enum NotificationType {
    CAMPAIGN_FINISHED,
    ERROR_OCCURRED,
    OPERATOR_REQUEST,
    DAILY_REPORT
}
