package uz.murodjon.robotcallv2.widget.domain.entity;

/**
 * Which agent, of which company, a public widget request reaches — once the widget has been
 * found, checked against the calling page's origin and found enabled.
 *
 * <p>Separate from {@code PublicWidgetConfigResponse}, which is what the page itself is
 * allowed to see and therefore carries no ids at all. This is the other direction: what
 * another feature needs in order to serve that request (UNIVERSAL-OPERATOR-PLAN E.4 — the
 * chat channel), and it never leaves the server.
 */
public record WidgetTarget(long widgetId, long companyId, long agentId) {
}
