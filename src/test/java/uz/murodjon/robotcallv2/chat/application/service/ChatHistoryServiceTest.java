package uz.murodjon.robotcallv2.chat.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.chat.application.dto.ChatDetail;
import uz.murodjon.robotcallv2.chat.application.dto.ChatRow;
import uz.murodjon.robotcallv2.chat.application.port.output.ChatSessionRepository;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatFilter;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatMessage;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatSession;
import uz.murodjon.robotcallv2.chat.domain.enums.ChatChannel;
import uz.murodjon.robotcallv2.chat.domain.enums.ChatRole;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatHistoryServiceTest {

    private static final Instant AT = Instant.parse("2026-09-16T10:00:00Z");

    private ChatSessionRepository repository;
    private AiAgentUseCase aiAgentUseCase;
    private ChatHistoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(ChatSessionRepository.class);
        aiAgentUseCase = mock(AiAgentUseCase.class);
        service = new ChatHistoryService(repository, aiAgentUseCase);
    }

    @Test
    void listsBothChannelsWithAgentNames() {
        ChatFilter filter = new ChatFilter(0, 20, null, null, null, null, null, null);
        when(repository.findAll(eq(1L), any())).thenReturn(List.of(widgetChat(10L), telegramChat(11L)));
        when(repository.count(eq(1L), any())).thenReturn(2L);
        when(aiAgentUseCase.findNamesByIds(anyCollection())).thenReturn(Map.of(42L, "Shop agent"));

        PageableData<ChatRow> page = service.findChats(1L, filter);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.data()).extracting(ChatRow::channel)
                .containsExactly(ChatChannel.WIDGET, ChatChannel.TELEGRAM);
        assertThat(page.data()).extracting(ChatRow::agentName).containsOnly("Shop agent");
    }

    @Test
    void detailCarriesOutcomeAndTranscript() {
        List<ChatMessage> transcript = List.of(
                new ChatMessage(1L, 10L, ChatRole.AGENT, "Assalomu alaykum!", AT),
                new ChatMessage(2L, 10L, ChatRole.CLIENT, "Ikkita lavash", AT));
        when(repository.findByCompanyIdAndId(1L, 10L)).thenReturn(widgetChat(10L));
        when(repository.findMessages(10L)).thenReturn(transcript);
        when(aiAgentUseCase.findNamesByIds(anyCollection())).thenReturn(Map.of(42L, "Shop agent"));

        ChatDetail detail = service.requireChat(1L, 10L);

        assertThat(detail.chat().id()).isEqualTo(10L);
        assertThat(detail.outcome()).containsEntry("orderId", "A-7");
        assertThat(detail.messages()).isEqualTo(transcript);
    }

    @Test
    void anotherCompanysChatIsNotFound() {
        when(repository.findByCompanyIdAndId(2L, 10L)).thenReturn(null);

        assertThatThrownBy(() -> service.requireChat(2L, 10L)).isInstanceOf(NotFoundException.class);
    }

    private static ChatSession widgetChat(long id) {
        return new ChatSession(id, "cht_w" + id, 1L, 42L, 5L, null, null, "uz-UZ", "ORDER",
                Map.of("orderId", "A-7"), null, false, 2, AT, AT);
    }

    private static ChatSession telegramChat(long id) {
        return new ChatSession(id, "cht_t" + id, 1L, 42L, null, 3L, "987654321", "uz-UZ", "ORDER",
                Map.of(), null, true, 6, AT, AT);
    }
}
