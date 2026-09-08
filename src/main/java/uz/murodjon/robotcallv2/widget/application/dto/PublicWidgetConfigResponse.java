package uz.murodjon.robotcallv2.widget.application.dto;

import uz.murodjon.robotcallv2.widget.domain.entity.WidgetTheme;

public record PublicWidgetConfigResponse(
        String widgetKey,
        String name,
        String agentName,
        WidgetTheme theme,
        boolean consentRequired,
        String consentText
) {
}
