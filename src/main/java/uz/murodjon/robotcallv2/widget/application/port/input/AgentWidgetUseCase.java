package uz.murodjon.robotcallv2.widget.application.port.input;

import uz.murodjon.robotcallv2.widget.application.dto.*;

import java.util.List;

public interface AgentWidgetUseCase {

    WidgetRow createWidget(long companyId, long agentId, CreateWidgetRequest request);

    WidgetRow updateWidget(long companyId, long widgetId, UpdateWidgetRequest request);

    WidgetRow findWidget(long companyId, long widgetId);

    List<WidgetRow> findWidgetsByAgentId(long companyId, long agentId);

    void deleteWidget(long companyId, long widgetId);

    /**
     * What the embed script on a public page is allowed to know: no company, no ids, and
     * only when the calling page's origin is one the widget lists.
     */
    PublicWidgetConfigResponse findPublicConfig(String widgetKey, String origin);

    /**
     * Books a call for a visitor who pressed the widget's button and hands back what their
     * browser dials in with.
     *
     * <p>Public and therefore rate-limited: it costs recognition, model and synthesis
     * minutes, and the only thing standing between it and the open internet is the
     * widget's allowed-origins list. The agent's own daily and concurrent limits are what
     * cap the damage when that is not enough.
     *
     * @param language the visitor's preferred language, or null for the agent's own
     */
    PublicWidgetSessionResponse startPublicSession(String widgetKey, String origin, String language);
}
