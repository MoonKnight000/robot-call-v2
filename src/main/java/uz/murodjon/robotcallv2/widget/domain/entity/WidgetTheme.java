package uz.murodjon.robotcallv2.widget.domain.entity;

/**
 * Visual and behavioral styling of the website voice widget.
 */
public record WidgetTheme(
        String primaryColor,
        String accentColor,
        String surfaceColor,
        String textColor,
        String mutedTextColor,
        String buttonTextColor,
        String borderColor,
        String position,
        String launcherSize,
        Integer panelWidth,
        Integer borderRadius,
        Boolean defaultOpen,
        Boolean showAvatar,
        String avatarImageUrl,
        String avatarOrbColor1,
        String avatarOrbColor2,
        String brandName,
        String actionText,
        String welcomeText,
        String startButtonText,
        String endButtonText,
        String connectingText,
        String listeningText,
        String speakingText,
        String endedText,
        Boolean whiteLabel
) {
    public static WidgetTheme defaultTheme() {
        return new WidgetTheme(
                "#002FA7",
                "#0F172A",
                "#FFFFFF",
                "#111827",
                "#667085",
                "#FFFFFF",
                "#DADDE3",
                "bottom-right",
                "comfortable",
                340,
                16,
                false,
                true,
                null,
                "#002FA7",
                "#00F0FF",
                "AI yordamchi",
                "Biz bilan gaplashing",
                "Savolingizni ovozli bering.",
                "Qo'ng'iroqni boshlash",
                "Tugatish",
                "Ulanmoqda",
                "Tinglayapman",
                "Javob bermoqda",
                "Qo'ng'iroq tugadi",
                false
        );
    }
}
