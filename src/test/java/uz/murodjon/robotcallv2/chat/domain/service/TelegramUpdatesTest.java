package uz.murodjon.robotcallv2.chat.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.chat.domain.entity.TelegramUpdate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a bot may be sent. Everything here except the first case is something Telegram sends
 * to a normal bot on a normal day, and none of it is an error — an update that is not a
 * question is simply acknowledged.
 */
class TelegramUpdatesTest {

    @Test
    void readsTheChatAndTheText() {
        TelegramUpdate update = TelegramUpdates.read(Map.of(
                "update_id", 1,
                "message", Map.of("chat", Map.of("id", 987654321L, "type", "private"),
                        "text", "  Salom  ")));

        assertThat(update).isNotNull();
        assertThat(update.chatId()).isEqualTo("987654321");
        assertThat(update.text()).isEqualTo("Salom");
    }

    @Test
    void writesAGroupChatIdWholeRatherThanAsADecimal() {
        // Jackson reads a chat id as a Long or an Integer depending on its size, and a group's
        // is large and negative. "-1001234567890.0" would key a different conversation.
        TelegramUpdate update = TelegramUpdates.read(Map.of(
                "message", Map.of("chat", Map.of("id", -1001234567890L), "text", "salom")));

        assertThat(update.chatId()).isEqualTo("-1001234567890");
    }

    @Test
    void anUpdateWithNothingToAnswerIsNotAnError() {
        // A photo, somebody joining, an edit, a callback button, a shape nobody here has seen.
        assertThat(TelegramUpdates.read(null)).isNull();
        assertThat(TelegramUpdates.read(Map.of("update_id", 1))).isNull();
        assertThat(TelegramUpdates.read(Map.of("message", Map.of("chat", Map.of("id", 1L))))).isNull();
        assertThat(TelegramUpdates.read(Map.of("message", Map.of("text", "salom")))).isNull();
        assertThat(TelegramUpdates.read(Map.of("message", Map.of("chat", Map.of("id", 1L), "text", "   ")))).isNull();
    }

    @Test
    void cutsAMessageLongerThanTelegramItselfAllows() {
        String tooLong = "a".repeat(5000);

        TelegramUpdate update = TelegramUpdates.read(Map.of(
                "message", Map.of("chat", Map.of("id", 1L), "text", tooLong)));

        assertThat(update.text()).hasSize(4096);
    }

    @Test
    void readsTheSendersOwnSharedContactAsTheirPhone() {
        TelegramUpdate update = TelegramUpdates.read(Map.of("message", Map.of(
                "chat", Map.of("id", 77L),
                "from", Map.of("id", 77L),
                "contact", Map.of("user_id", 77L, "phone_number", "998901234567"))));

        assertThat(update.contactPhone()).isEqualTo("+998901234567");
        assertThat(update.text()).isEqualTo(TelegramUpdates.CONTACT_SHARED_TEXT + "+998901234567");
    }

    @Test
    void ignoresSomebodyElsesContactCard() {
        TelegramUpdate update = TelegramUpdates.read(Map.of("message", Map.of(
                "chat", Map.of("id", 77L),
                "from", Map.of("id", 77L),
                "text", "mana",
                "contact", Map.of("user_id", 5L, "phone_number", "+998901234567"))));

        assertThat(update.contactPhone()).isNull();
        assertThat(update.text()).isEqualTo("mana");
    }
}
