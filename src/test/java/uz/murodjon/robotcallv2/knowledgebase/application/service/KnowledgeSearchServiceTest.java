package uz.murodjon.robotcallv2.knowledgebase.application.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallEvent;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeInsightsResponse;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeRetrievalUseCase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Insights are read off the call traces: what counts, what is a gap, and what is counted once. */
class KnowledgeSearchServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    @Test
    void gapsAreQuestionsNothingWasFoundForCountedOncePerCall() {
        CallEventUseCase events = mock(CallEventUseCase.class);
        when(events.findByCompanyIdAndType(eq(1L), eq(CallEventType.KNOWLEDGE_RETRIEVED), any(), any(), anyInt()))
                .thenReturn(List.of(
                        // newest first, as the repository returns them
                        event(30, 10, Map.of("agentId", 7, "question", "Ofis qayerda?", "found", false)),
                        event(29, 11, Map.of("agentId", 7, "question", "ofis qayerda", "found", false)),
                        // the same call asking the same thing twice (speculative + final turn) is one
                        event(28, 11, Map.of("agentId", 7, "question", "Ofis qayerda?", "found", false)),
                        event(27, 12, Map.of("agentId", 7, "question", "Narxi qancha?", "found", true,
                                "passages", List.of(Map.of("sourceId", 3, "sourceName", "Narxlar"),
                                        Map.of("sourceId", 3, "sourceName", "Narxlar")))),
                        event(26, 13, Map.of("agentId", 7, "question", "Click bormi?", "found", true,
                                "curatedItemId", 9, "curatedMatchedBy", "MEANING", "passages", List.of())),
                        event(25, 14, Map.of("agentId", 8, "question", "Boshqa agent", "found", false))));
        KnowledgeSearchService service = new KnowledgeSearchService(mock(KnowledgeRetrievalUseCase.class),
                mock(AiAgentUseCase.class), events, Clock.fixed(NOW, ZoneOffset.UTC));

        KnowledgeInsightsResponse insights = service.findInsights(1L, 30, 7L);

        assertThat(insights.searches()).isEqualTo(4);
        assertThat(insights.answered()).isEqualTo(2);
        assertThat(insights.curatedAnswers()).isEqualTo(1);
        assertThat(insights.hitRate()).isEqualTo(0.5);
        assertThat(insights.gaps()).hasSize(1);
        assertThat(insights.gaps().getFirst().count()).isEqualTo(2);
        assertThat(insights.gaps().getFirst().question()).isEqualTo("Ofis qayerda?");
        assertThat(insights.gaps().getFirst().lastCallAttemptId()).isEqualTo(10L);
        assertThat(insights.topSources()).singleElement()
                .satisfies(source -> assertThat(source.hits()).isEqualTo(1));
        assertThat(insights.topItems()).singleElement()
                .satisfies(item -> assertThat(item.byMeaning()).isEqualTo(1));
    }

    private static CallEvent event(int minutesAfterEleven, long callAttemptId, Map<String, Object> payload) {
        return new CallEvent(callAttemptId * 10, 1L, callAttemptId, 0L,
                Instant.parse("2026-11-06T11:00:00Z").plusSeconds(minutesAfterEleven * 60L),
                CallEventType.KNOWLEDGE_RETRIEVED, payload);
    }
}
