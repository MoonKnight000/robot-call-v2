package uz.murodjon.robotcallv2.quality.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.agent.summary.CallQualityJudge;
import uz.murodjon.robotcallv2.agent.summary.QualityVerdict;
import uz.murodjon.robotcallv2.quality.application.port.output.CallQualityRepository;
import uz.murodjon.robotcallv2.quality.domain.entity.CallQuality;
import uz.murodjon.robotcallv2.quality.domain.entity.FinishedCall;
import uz.murodjon.robotcallv2.quality.domain.enums.CallReviewStatus;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolExecutionUseCase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CallQualityServiceTest {

    private static final long COMPANY_ID = 7L;
    private static final long CALL_ID = 55L;
    private static final String TRANSCRIPT = "AGENT: Buyurtma berasizmi?\nCLIENT: Nima dedingiz?\nAGENT: Buyurtma berasizmi?\n";
    private static final QualityVerdict FLAGGED =
            new QualityVerdict(40, true, false, false, true, false, false, false, "narxni to'qidi");
    private static final QualityVerdict CLEAN =
            new QualityVerdict(90, false, false, false, true, true, false, false, null);

    private final ScenarioDefinition scenario = mock(ScenarioDefinition.class);
    private CallQualityRepository repository;
    private CallQualityJudge qualityJudge;
    private ToolExecutionUseCase toolExecutionUseCase;
    private CallQualityService service;

    @BeforeEach
    void setUp() {
        repository = mock(CallQualityRepository.class);
        qualityJudge = mock(CallQualityJudge.class);
        toolExecutionUseCase = mock(ToolExecutionUseCase.class);
        service = new CallQualityService(repository, qualityJudge, toolExecutionUseCase);
    }

    @Test
    void aFlaggedCallWaitsInTheReviewInbox() {
        when(qualityJudge.judge(TRANSCRIPT, scenario, Disposition.GOAL_NOT_REACHED)).thenReturn(FLAGGED);
        when(toolExecutionUseCase.countFailedExecutions(COMPANY_ID, CALL_ID)).thenReturn(2);

        service.recordCallQuality(COMPANY_ID, call(false, List.of()), scenario);

        CallQuality saved = saved();
        assertThat(saved.score()).isEqualTo(40);
        assertThat(saved.guardrailViolation()).isTrue();
        assertThat(saved.needsReview()).isTrue();
        assertThat(saved.reviewStatus()).isEqualTo(CallReviewStatus.PENDING);
        assertThat(saved.note()).isEqualTo("narxni to'qidi");
        assertThat(saved.toolErrorCount()).isEqualTo(2);
        assertThat(saved.repairCount()).isEqualTo(1);
        assertThat(saved.turnCount()).isEqualTo(4);
        assertThat(saved.bargeInCount()).isEqualTo(3);
        assertThat(saved.noInputPromptCount()).isEqualTo(1);
        assertThat(saved.lowConfidenceCount()).isEqualTo(2);
        assertThat(saved.aiAgentId()).isEqualTo(3L);
    }

    @Test
    void aCleanCallIsKeptButNotQueuedForReview() {
        when(qualityJudge.judge(any(), any(), any())).thenReturn(CLEAN);

        service.recordCallQuality(COMPANY_ID, call(false, List.of()), scenario);

        assertThat(saved().needsReview()).isFalse();
        assertThat(saved().reviewStatus()).isNull();
    }

    /** "Har qo'ng'iroqda call_quality qatori": the signals stand without the judge. */
    @Test
    void anUnscoredCallStillGetsItsRowWithTheSignals() {
        service.recordCallQuality(COMPANY_ID, call(false, List.of()), scenario);

        CallQuality saved = saved();
        assertThat(saved.score()).isNull();
        assertThat(saved.guardrailViolation()).isNull();
        assertThat(saved.reviewStatus()).isNull();
        assertThat(saved.repairCount()).isEqualTo(1);
    }

    @Test
    void aZeroPiiAgentsCallKeepsTheVerdictButNotTheNote() {
        when(qualityJudge.judge(any(), any(), any())).thenReturn(FLAGGED);

        service.recordCallQuality(COMPANY_ID, call(true, List.of()), scenario);

        assertThat(saved().note()).isNull();
        assertThat(saved().guardrailViolation()).isTrue();
    }

    @Test
    void theSameNumbersEarlierCallsAreMarkedAsRepeated() {
        service.recordCallQuality(COMPANY_ID, call(false, List.of(31L, 32L)), scenario);

        verify(repository).markRepeatCalls(COMPANY_ID, List.of(31L, 32L));
    }

    @Test
    void noEarlierCallsMarkNothing() {
        service.recordCallQuality(COMPANY_ID, call(false, List.of()), scenario);

        verify(repository, never()).markRepeatCalls(anyLong(), anyCollection());
    }

    private static FinishedCall call(boolean zeroPii, List<Long> earlierCallIds) {
        return new FinishedCall(CALL_ID, 3L, 2, Disposition.GOAL_NOT_REACHED, TRANSCRIPT, 4, 3, 1, 2, zeroPii,
                earlierCallIds);
    }

    private CallQuality saved() {
        ArgumentCaptor<CallQuality> captor = ArgumentCaptor.forClass(CallQuality.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
