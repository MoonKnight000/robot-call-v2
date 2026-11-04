package uz.murodjon.robotcallv2.chat.domain.enums;

/** Which text channel a conversation arrived on — whichever of {@code widgetId} / {@code telegramBotId} is set. */
public enum ChatChannel {
    WIDGET,
    TELEGRAM,
    /** The company's own app or site, through /api/v1/chats with an API key. */
    API
}
