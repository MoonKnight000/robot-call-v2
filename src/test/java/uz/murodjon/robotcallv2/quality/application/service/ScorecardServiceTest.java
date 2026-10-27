package uz.murodjon.robotcallv2.quality.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import uz.murodjon.robotcallv2.agent.metrics.CallMetrics;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.quality.application.dto.ReviewScorecardRequest;
import uz.murodjon.robotcallv2.quality.application.dto.ScorecardAnswerCorrection;
import uz.murodjon.robotcallv2.quality.application.port.output.CallScorecardRepository;
import uz.murodjon.robotcallv2.quality.application.port.output.ScorecardCriterionRepository;
import uz.murodjon.robotcallv2.quality.application.port.output.ScorecardJudgePort;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecard;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecardAnswer;
import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardCriterion;
import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardVerdict;
import uz.murodjon.robotcallv2.quality.domain.enums.CriterionAnswer;
import uz.murodjon.robotcallv2.quality.domain.enums.ScorecardReviewStatus;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScorecardServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long CALL_ID = 42L;
    private static final long OPERATOR_ID = 9L;
    private static final long SUPERVISOR_ID = 5L;
    private static final String TRANSCRIPT = "OPERATOR: Assalomu alaykum\nCLIENT: Va alaykum assalom\n";

    private ScorecardCriterionRepository criteria;
    private CallScorecardRepository scorecards;
    private ScorecardJudgePort judge;
    private CompanyConfigUseCase companyConfig;
    private NotificationUseCase notifications;
    private ScorecardService service;

    private final ScorecardCriterion greeting = criterion(1L, 3);
    private final ScorecardCriterion identity = criterion(2L, 1);

    @BeforeEach
    void setUp() {
        criteria = mock(ScorecardCriterionRepository.class);
        scorecards = mock(CallScorecardRepository.class);
        judge = mock(ScorecardJudgePort.class);
        companyConfig = mock(CompanyConfigUseCase.class);
        notifications = mock(NotificationUseCase.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.id()).thenReturn(Optional.of(SUPERVISOR_ID));
        when(scorecards.upsert(anyLong(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(scorecards.updateReview(anyLong(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        service = new ScorecardService(criteria, scorecards, judge, mock(AuditUseCase.class), companyConfig,
                notifications, currentUser);
    }

    @Test
    void operatorCallIsStoredWithItsAnswersScoreAndTokens() {
        judgeSays(Sentiment.NEUTRAL, CriterionAnswer.YES, CriterionAnswer.NO);

        CallScorecard stored = service.recordCallScorecard(COMPANY_ID, CALL_ID, OPERATOR_ID, TRANSCRIPT);

        ArgumentCaptor<CallScorecard> saved = ArgumentCaptor.forClass(CallScorecard.class);
        verify(scorecards).upsert(eq(COMPANY_ID), saved.capture());
        assertThat(saved.getValue().callId()).isEqualTo(CALL_ID);
        assertThat(saved.getValue().operatorUserId()).isEqualTo(OPERATOR_ID);
        assertThat(saved.getValue().score()).isEqualTo(75);
        assertThat(saved.getValue().judgedScore()).isEqualTo(75);
        assertThat(saved.getValue().answers()).hasSize(2);
        assertThat(saved.getValue().topics()).containsExactly("salomlashish");
        assertThat(stored.promptTokens()).isEqualTo(1000);
    }

    @Test
    void scoreAtOrAboveTheThresholdStaysOutOfTheInbox() {
        judgeSays(Sentiment.NEUTRAL, CriterionAnswer.YES, CriterionAnswer.NO);

        CallScorecard stored = service.recordCallScorecard(COMPANY_ID, CALL_ID, OPERATOR_ID, TRANSCRIPT);

        assertThat(stored.needsReview()).isFalse();
        assertThat(stored.reviewStatus()).isNull();
        verifyNoInteractions(notifications);
    }

    @Test
    void scoreUnderTheThresholdGoesToTheInboxWithANotification() {
        judgeSays(Sentiment.NEUTRAL, CriterionAnswer.NO, CriterionAnswer.YES);

        CallScorecard stored = service.recordCallScorecard(COMPANY_ID, CALL_ID, OPERATOR_ID, TRANSCRIPT);

        assertThat(stored.score()).isEqualTo(25);
        assertThat(stored.needsReview()).isTrue();
        assertThat(stored.reviewStatus()).isEqualTo(ScorecardReviewStatus.PENDING);
        verify(notifications).notify(eq(COMPANY_ID), eq(NotificationType.SCORECARD_REVIEW), anyString(),
                contains(String.valueOf(CALL_ID)), isNull());
    }

    @Test
    void upsetClientGoesToTheInboxWhateverTheScore() {
        judgeSays(Sentiment.HOSTILE, CriterionAnswer.YES, CriterionAnswer.YES);

        CallScorecard stored = service.recordCallScorecard(COMPANY_ID, CALL_ID, OPERATOR_ID, TRANSCRIPT);

        assertThat(stored.score()).isEqualTo(100);
        assertThat(stored.needsReview()).isTrue();
    }

    @Test
    void companyWithoutCriteriaIsNotJudged() {
        when(criteria.findEnabledByCompanyId(COMPANY_ID)).thenReturn(List.of());

        assertThat(service.recordCallScorecard(COMPANY_ID, CALL_ID, OPERATOR_ID, TRANSCRIPT)).isNull();

        verifyNoInteractions(judge, scorecards);
    }

    @Test
    void callWithoutTranscriptIsNotJudged() {
        assertThat(service.recordCallScorecard(COMPANY_ID, CALL_ID, OPERATOR_ID, " ")).isNull();

        verifyNoInteractions(criteria, judge, scorecards);
    }

    @Test
    void judgeThatGaveNoVerdictStoresNothing() {
        when(criteria.findEnabledByCompanyId(COMPANY_ID)).thenReturn(List.of(criterion(1L, 1)));

        assertThat(service.recordCallScorecard(COMPANY_ID, CALL_ID, OPERATOR_ID, TRANSCRIPT)).isNull();

        verify(scorecards, never()).upsert(anyLong(), any());
    }

    @Test
    void callNeverScoredIsNotFound() {
        assertThatThrownBy(() -> service.requireCallScorecard(COMPANY_ID, CALL_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void correctedAnswerReScoresTheCallAndKeepsTheJudges() {
        storedScorecard(CriterionAnswer.NO, CriterionAnswer.YES);

        CallScorecard reviewed = service.recordReview(COMPANY_ID, CALL_ID, new ReviewScorecardRequest(
                List.of(new ScorecardAnswerCorrection(101L, CriterionAnswer.YES)), " transkript xato "));

        assertThat(reviewed.reviewStatus()).isEqualTo(ScorecardReviewStatus.CORRECTED);
        assertThat(reviewed.score()).isEqualTo(100);
        assertThat(reviewed.judgedScore()).isEqualTo(25);
        assertThat(reviewed.answers().getFirst().answer()).isEqualTo(CriterionAnswer.YES);
        assertThat(reviewed.answers().getFirst().judgedAnswer()).isEqualTo(CriterionAnswer.NO);
        assertThat(reviewed.reviewNote()).isEqualTo("transkript xato");
        assertThat(reviewed.reviewedBy()).isEqualTo(SUPERVISOR_ID);
        assertThat(reviewed.reviewedAt()).isNotNull();
    }

    @Test
    void reviewWithoutCorrectionsConfirmsTheJudge() {
        storedScorecard(CriterionAnswer.NO, CriterionAnswer.YES);

        CallScorecard reviewed = service.recordReview(COMPANY_ID, CALL_ID, new ReviewScorecardRequest(null, null));

        assertThat(reviewed.reviewStatus()).isEqualTo(ScorecardReviewStatus.CONFIRMED);
        assertThat(reviewed.score()).isEqualTo(25);
    }

    @Test
    void correctionToTheJudgesOwnAnswerIsAConfirmation() {
        storedScorecard(CriterionAnswer.NO, CriterionAnswer.YES);

        CallScorecard reviewed = service.recordReview(COMPANY_ID, CALL_ID, new ReviewScorecardRequest(
                List.of(new ScorecardAnswerCorrection(101L, CriterionAnswer.NO)), null));

        assertThat(reviewed.reviewStatus()).isEqualTo(ScorecardReviewStatus.CONFIRMED);
    }

    @Test
    void correctionOfAnAnswerNotOnTheScorecardIsRejected() {
        storedScorecard(CriterionAnswer.NO, CriterionAnswer.YES);

        assertThatThrownBy(() -> service.recordReview(COMPANY_ID, CALL_ID, new ReviewScorecardRequest(
                List.of(new ScorecardAnswerCorrection(999L, CriterionAnswer.YES)), null)))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.SCORECARD_ANSWER_UNKNOWN));
        verify(scorecards, never()).updateReview(anyLong(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsScorecardMetricsWhenProviderAvailable() {
        CallMetrics callMetrics = mock(CallMetrics.class);
        ObjectProvider<CallMetrics> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(callMetrics);

        ScorecardService serviceWithMetrics = new ScorecardService(criteria, scorecards, judge,
                mock(AuditUseCase.class), companyConfig, notifications, mock(CurrentUser.class), provider);

        judgeSays(Sentiment.NEUTRAL, CriterionAnswer.NO, CriterionAnswer.YES);

        serviceWithMetrics.recordCallScorecard(COMPANY_ID, CALL_ID, OPERATOR_ID, TRANSCRIPT);

        verify(callMetrics).recordScorecardScore(25);
        verify(callMetrics).scorecardReviewNeeded();
    }

    private void judgeSays(Sentiment sentiment, CriterionAnswer onGreeting, CriterionAnswer onIdentity) {
        when(criteria.findEnabledByCompanyId(COMPANY_ID)).thenReturn(List.of(greeting, identity));
        when(judge.findVerdict(List.of(greeting, identity), TRANSCRIPT)).thenReturn(new ScorecardVerdict(List.of(
                CallScorecardAnswer.of(greeting, onGreeting, "Assalomu alaykum"),
                CallScorecardAnswer.of(identity, onIdentity, null)), sentiment, List.of("salomlashish"), 1000, 200,
                0));
    }

    /** Answer ids 101 (greeting, weight 3) and 102 (identity, weight 1), as the judge left them. */
    private void storedScorecard(CriterionAnswer onGreeting, CriterionAnswer onIdentity) {
        List<CallScorecardAnswer> answers = List.of(
                new CallScorecardAnswer(101L, 1L, "c1", 3, onGreeting, onGreeting, null),
                new CallScorecardAnswer(102L, 2L, "c2", 1, onIdentity, onIdentity, null));
        Integer score = onGreeting == CriterionAnswer.NO && onIdentity == CriterionAnswer.YES ? 25 : null;
        when(scorecards.findByCompanyIdAndCallId(COMPANY_ID, CALL_ID)).thenReturn(new CallScorecard(CALL_ID,
                COMPANY_ID, OPERATOR_ID, score, score, Sentiment.NEUTRAL, List.of(), true, ScorecardReviewStatus.PENDING,
                null, null, null, answers, 1000, 200, 0, null));
    }

    private static ScorecardCriterion criterion(long id, int weight) {
        return new ScorecardCriterion(id, COMPANY_ID, "c" + id, "instruction", weight, true, null);
    }
}
