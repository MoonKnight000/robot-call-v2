package uz.murodjon.robotcallv2.campaign.domain.enums;

/**
 * Frequency rule for recurring automated campaigns.
 */
public enum RecurrenceType {
    /** Run once on manual start / creation (default). */
    ONCE,

    /** Run daily within dial window and allowed dial days. */
    DAILY,

    /** Run weekly on specific weekdays (e.g. every Sunday / every Monday). */
    WEEKLY,

    /** Run monthly on a specific day of the month (e.g. 1st or 25th of every month). */
    MONTHLY,

    /** Run according to a standard 6-field Spring/Quartz Cron expression. */
    CRON
}
