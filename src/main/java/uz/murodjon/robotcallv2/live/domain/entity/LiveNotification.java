package uz.murodjon.robotcallv2.live.domain.entity;

/**
 * A one-off event for the topbar notification bell (§9), distinct from the KPI/call
 * ticks — a campaign starting or pausing, say, rather than a periodic snapshot.
 *
 * @param level   {@code "info"}, {@code "warning"}, or {@code "danger"}
 * @param title   short headline
 * @param message full text
 */
public record LiveNotification(String level, String title, String message) {
}

