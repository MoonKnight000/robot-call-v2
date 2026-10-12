package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.scenario.domain.entity.OutcomeField;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageBranch;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.enums.BranchOperator;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These tools are the only way the model can change a call's outcome, and the
 * guardrails in them (§4.4) are the last line before a wrong promise reaches the CRM.
 */
class DialogToolsTest {

    private DialogSession session;
    private DialogTools tools;

    @BeforeEach
    void setUp() {
        CallContext context = new CallContext(Map.of(
                "clientName", "Aziz Karimov",
                "debtAmount", new BigDecimal("1500000"),
                "currency", "so'm",
                "dueDate", LocalDate.of(2026, 7, 1),
                "contractNumber", "UY-2026-00123"
        ), "goal");
        // No RTP endpoint: none of these tools touch audio.
        session = DialogSession.builder("chan-1", "uz-UZ")
                .context(context)
                .scenario(ScenarioFixtures.debtCollection())
                .callAttemptId(42L)
                .companyId(1L)
                .companyName("Uysot")
                .build();
        tools = new DialogTools(session);
    }

    @Test
    void dropsUnspeakableReplyInTransitionTo() {
        session.setState("DEBT_NOTICE");

        tools.transitionTo("dynamic_thought_or_fallback", "REASON_INQUIRY");

        assertThat(session.state()).isEqualTo("REASON_INQUIRY");
        assertThat(session.toolReplies()).isNull();
    }

    @Test
    void keepsValidReplyInTransitionTo() {
        session.setState("DEBT_NOTICE");

        tools.transitionTo("Tushunarli. Sabab nimada?", "REASON_INQUIRY");

        assertThat(session.state()).isEqualTo("REASON_INQUIRY");
        assertThat(session.toolReplies()).isEqualTo("Tushunarli. Sabab nimada?");
    }

    @Test
    void allowsStayingInCurrentStage() {
        session.setState("DEBT_NOTICE");

        String result = tools.transitionTo("Qaytadan aytaman: qarzingiz bor.", "DEBT_NOTICE");

        assertThat(result).doesNotStartWith("ERROR:").contains("DEBT_NOTICE");
        assertThat(session.state()).isEqualTo("DEBT_NOTICE");
        assertThat(session.toolReplies()).isEqualTo("Qaytadan aytaman: qarzingiz bor.");
    }

    @Test
    void rejectsAPromiseInThePast() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        String result = tools.recordPaymentPromise("Yaxshi, yozib qo'ydim.", yesterday, null, null, null);

        assertThat(result).startsWith("ERROR:");
        // The line confirmed a promise the guardrail refused — it must not be spoken.
        assertThat(session.toolReplies()).isNull();
        // The usual cause is a wrong year, so today's date has to be in the reply —
        // without it the model has nothing to correct against and re-sends the same date.
        assertThat(result).contains(LocalDate.now().toString());
        assertThat(session.outcome()).doesNotContainKey("promisedDate");
        assertThat(session.disposition()).isNull();
    }

    @Test
    void acceptsAPromiseFromToday() {
        LocalDate today = LocalDate.now();

        tools.recordPaymentPromise("Kelishdik, belgilab qo'ydim.", today, new BigDecimal("500000"), "CLICK", "yarim to'lov");

        // What the caller hears on a tool-only turn, without a second LLM round trip.
        assertThat(session.toolReplies()).isEqualTo("Kelishdik, belgilab qo'ydim.");
        assertThat(session.outcome().get("promisedDate")).isEqualTo(today);
        assertThat((BigDecimal) session.outcome().get("promisedAmount")).isEqualByComparingTo("500000");
        assertThat(session.outcome().get("paymentChannel")).isEqualTo("CLICK");
        assertThat(session.disposition()).isEqualTo(Disposition.PROMISE_TO_PAY);
    }

    @Test
    void optOutEndsTheCallAndKeepsTheReason() {
        // §11.4: the reason has to survive until teardown writes the phone-level entry.
        tools.recordDoNotCall("Uzr so'rayman, boshqa bezovta qilmaymiz.",
                "mijoz boshqa qo'ng'iroq qilinmasligini so'radi");

        assertThat(session.isEnded()).isTrue();
        assertThat(session.disposition()).isEqualTo(Disposition.DO_NOT_CALL);
        assertThat(session.doNotCallReason()).contains("qo'ng'iroq");
    }

    @Test
    void refusalRecordsTheReasonCode() {
        tools.recordRefusalReason("Tushundim, holatingizni yozib qo'ydim.", "JOB_LOSS", "ishdan bo'shadi");

        assertThat(session.outcome().get("reasonCode")).isEqualTo("JOB_LOSS");
        assertThat(session.disposition()).isEqualTo(Disposition.REFUSED);
        assertThat(session.isEnded()).isFalse(); // the agent still has to close the call
    }

    @Test
    void transferEndsTheCallAsTransferred() {
        tools.requestHumanTransfer("Bir daqiqa, operatorga ulayman.", "mijoz operator so'radi");

        assertThat(session.isEnded()).isTrue();
        assertThat(session.disposition()).isEqualTo(Disposition.TRANSFERRED);
    }

    @Test
    void wrongPersonEndsTheCall() {
        tools.recordWrongPerson("Uzr, bezovta qildim.", "boshqa odam ko'tardi");

        assertThat(session.isEnded()).isTrue();
        assertThat(session.disposition()).isEqualTo(Disposition.WRONG_NUMBER);
    }

    @Test
    void waitForCallerKeepsTheCallOpenAndSilent() {
        tools.waitForCaller();

        assertThat(session.isWaitingForCaller()).isTrue();
        assertThat(session.isEnded()).isFalse();
        assertThat(session.toolReplies()).isNull();

        session.clearWaitForCaller();
        assertThat(session.isWaitingForCaller()).isFalse();
    }

    @Test
    void switchLanguageMovesTheCallToASupportedLanguage() {
        session.setDetectLanguages(java.util.List.of("uz-UZ", "ru-RU"));

        String note = tools.switchLanguage("Хорошо, давайте по-русски.", "ru-RU");

        assertThat(session.language()).isEqualTo("ru-RU");
        assertThat(session.toolReplies()).isEqualTo("Хорошо, давайте по-русски.");
        assertThat(note).doesNotContain("ERROR");
    }

    @Test
    void switchLanguageRefusesALanguageTheCompanyDoesNotHold() {
        session.setDetectLanguages(java.util.List.of("uz-UZ"));

        String note = tools.switchLanguage("Okay, in English.", "en-US");

        assertThat(session.language()).isEqualTo("uz-UZ");
        assertThat(session.toolReplies()).isNull();
        assertThat(note).startsWith("ERROR");
    }

    @Test
    void noteCallerMoodSetsTheMoodTheVoiceFollows() {
        tools.noteCallerMood(CustomerSentiment.FRUSTRATED);

        assertThat(session.lastCustomerSentiment()).isEqualTo(CustomerSentiment.FRUSTRATED);
    }

    @Test
    void transitionMovesTheFsm() {
        tools.transitionTo("Murodjon sizmi?", "IDENTITY_CHECK");

        assertThat(session.state()).isEqualTo("IDENTITY_CHECK");
        assertThat(session.toolReplies()).isEqualTo("Murodjon sizmi?");
    }

    @Test
    void walksTheStagesInBetween() {
        // A refused jump strands the call in its stage, so the stages the graph leads
        // through are walked instead.
        String result = tools.transitionTo("Sizning qarzingiz bor.", "DEBT_NOTICE");

        assertThat(session.state()).isEqualTo("DEBT_NOTICE");
        // The model has to be told what it passed, or it keeps asking for those stages.
        assertThat(result).doesNotStartWith("ERROR:").contains("IDENTITY_CHECK");
        assertThat(session.toolReplies()).isEqualTo("Sizning qarzingiz bor.");
    }

    @Test
    void aBranchSkipsTheStageWhenTheFactIsMissing() {
        DialogTools branching = toolsFor(Map.of("clientName", "Aziz"));

        branching.transitionTo(null, "IDENTITY_CHECK");

        assertThat(branchingSession.state()).isEqualTo("DEBT_NOTICE");
    }

    @Test
    void aWalkStopsAtAStageWithBranchesThatDoNotHold() {
        // Jumping from GREETING straight to the debt would skip the identity check.
        DialogTools branching = toolsFor(Map.of("clientName", "Aziz", "birthYear", 1990));

        branching.transitionTo(null, "DEBT_NOTICE");

        assertThat(branchingSession.state()).isEqualTo("IDENTITY_CHECK");
    }

    private DialogSession branchingSession;

    private DialogTools toolsFor(Map<String, Object> facts) {
        ScenarioDefinition scenario = new ScenarioDefinition(
                List.of(
                        new StageDef("GREETING", "Salom", List.of("IDENTITY_CHECK"), List.of()),
                        StageDef.builder()
                                .id("IDENTITY_CHECK")
                                .purpose("Yilni so'rash")
                                .allowedTransitions(List.of("DEBT_NOTICE"))
                                .allowedTools(List.of())
                                .branches(List.of(new StageBranch("birthYear", BranchOperator.MISSING, null, "DEBT_NOTICE")))
                                .build(),
                        new StageDef("DEBT_NOTICE", "Qarz", List.of("END_CALL"), List.of()),
                        new StageDef("END_CALL", "Tamom", List.of(), List.of())),
                List.of(), List.of(), List.of(new OutcomeField("res", "string", "Natija")),
                "Prompt", List.of(), null);
        branchingSession = DialogSession.builder("chan-2", "uz-UZ")
                .context(new CallContext(facts, "goal"))
                .scenario(scenario)
                .callAttemptId(43L)
                .companyId(1L)
                .build();
        return new DialogTools(branchingSession);
    }

    private DialogSession requiredSession;

    private DialogTools toolsWithARequiredOutcome() {
        ScenarioDefinition scenario = new ScenarioDefinition(
                List.of(
                        new StageDef("GREETING", "Salom", List.of("ASK_DATE"), List.of()),
                        StageDef.builder()
                                .id("ASK_DATE")
                                .purpose("Sanani so'rang")
                                .allowedTransitions(List.of("CONFIRM"))
                                .allowedTools(List.of())
                                .requiredOutcomes(List.of("promisedDate", "reasonCode"))
                                .build(),
                        new StageDef("CONFIRM", "Tasdiqlang", List.of("END_CALL"), List.of()),
                        new StageDef("END_CALL", "Tamom", List.of(), List.of())),
                List.of(), List.of(), List.of(new OutcomeField("res", "string", "Natija")),
                "Prompt", List.of(), null);
        requiredSession = DialogSession.builder("chan-3", "uz-UZ")
                .context(new CallContext(Map.of(), "goal"))
                .scenario(scenario)
                .callAttemptId(44L)
                .companyId(1L)
                .build();
        return new DialogTools(requiredSession);
    }

    @Test
    void aStageDoesNotEndBeforeItsRequiredOutcomeIsRecorded() {
        DialogTools required = toolsWithARequiredOutcome();
        requiredSession.setState("ASK_DATE");

        String result = required.transitionTo("Kelishdik.", "CONFIRM");

        assertThat(result).startsWith("ERROR:").contains("promisedDate").contains("reasonCode");
        assertThat(requiredSession.state()).isEqualTo("ASK_DATE");
    }

    @Test
    void aStageEndsOnceAnyOfItsRequiredOutcomesIsRecorded() {
        // Any of, not all of: a client who refuses gives a reason instead of a date and
        // must still be able to move on.
        DialogTools required = toolsWithARequiredOutcome();
        requiredSession.setState("ASK_DATE");
        requiredSession.recordOutcome("reasonCode", "NO_MONEY");

        required.transitionTo("Tushunarli.", "CONFIRM");

        assertThat(requiredSession.state()).isEqualTo("CONFIRM");
    }

    @Test
    void aWalkStopsAtAStageStillWaitingForItsOutcome() {
        DialogTools required = toolsWithARequiredOutcome();
        requiredSession.setState("GREETING");

        required.transitionTo(null, "CONFIRM");

        assertThat(requiredSession.state()).isEqualTo("ASK_DATE");
    }

    @Test
    void refusesAStageTheGraphDoesNotLeadTo() {
        session.setState("CLOSING");

        String result = tools.transitionTo("Qarzingiz bor.", "DEBT_NOTICE");

        assertThat(result).startsWith("ERROR:");
        // The model has to be told where it may go, or it re-sends the same stage.
        assertThat(result).contains("END_CALL");
        assertThat(session.state()).isEqualTo("CLOSING");
        assertThat(session.toolReplies()).isNull();
    }

    @Test
    void endCallKeepsTheDispositionItWasGiven() {
        tools.endCall("Xayr, kuningiz xayrli o'tsin!", Disposition.HUNG_UP);

        assertThat(session.isEnded()).isTrue();
        assertThat(session.disposition()).isEqualTo(Disposition.HUNG_UP);
    }

    @Test
    void bookShowroomVisitRecordsMeetingAndReachesGoal() {
        String res = tools.bookShowroomVisit("Sizni ofisimizda kutamiz!", "2026-09-20 15:00",
                "3 xonali", "60000 USD", "Bo'lib to'lash", "Oilaviy kelishadi");

        assertThat(res).contains("Sotuv ofisiga uchrashuv belgilandi");
        assertThat(session.toolReplies()).isEqualTo("Sizni ofisimizda kutamiz!");
        assertThat(session.outcome().get("meetingBooked")).isEqualTo(true);
        assertThat(session.outcome().get("meetingTime")).isEqualTo("2026-09-20 15:00");
        assertThat(session.outcome().get("roomsNeeded")).isEqualTo("3 xonali");
        assertThat(session.outcome().get("budgetRange")).isEqualTo("60000 USD");
        assertThat(session.outcome().get("paymentMethod")).isEqualTo("Bo'lib to'lash");
        assertThat(session.outcome().get("meetingNote")).isEqualTo("Oilaviy kelishadi");
        assertThat(session.disposition()).isEqualTo(Disposition.GOAL_REACHED);
    }

    @Test
    void bookShowroomVisitRequiresVisitTime() {
        String res = tools.bookShowroomVisit("Xayr", null, "2 xonali", null, null, null);
        assertThat(res).startsWith("ERROR:");
        assertThat(session.outcome().get("meetingBooked")).isNull();
    }

    @Test
    void sendLocationSmsRequestsSmsWhenNoLinkOrAddress() {
        String res = tools.sendLocationSms("Lokatsiyani yuboraman", null, null);
        assertThat(res).contains("qayd etildi");
        assertThat(session.outcome().get("locationSmsRequested")).isEqualTo(true);
    }

    @Test
    void sendCatalogSmsRequestsSmsWhenNoLink() {
        String res = tools.sendCatalogSms("Katalogni yuboraman", null);
        assertThat(res).contains("qayd etildi");
        assertThat(session.outcome().get("catalogSmsRequested")).isEqualTo(true);
    }

    @Test
    void bookAppointmentRecordsDetailsAndReachesGoal() {
        String res = tools.bookAppointment("Sizni 15-oktabr soat 14:00 da kutamiz!", "2026-10-15", "14:00",
                "Ali Valiyev", "Stomatolog ko'rigi", "Birinchi marta kelmoqda");

        assertThat(res).contains("Uchrashuv muvaffaqiyatli band qilindi");
        assertThat(session.toolReplies()).isEqualTo("Sizni 15-oktabr soat 14:00 da kutamiz!");
        assertThat(session.outcome().get("appointmentBooked")).isEqualTo(true);
        assertThat(session.outcome().get("appointmentDate")).isEqualTo("2026-10-15");
        assertThat(session.outcome().get("appointmentTime")).isEqualTo("14:00");
        assertThat(session.outcome().get("appointmentAttendee")).isEqualTo("Ali Valiyev");
        assertThat(session.outcome().get("appointmentService")).isEqualTo("Stomatolog ko'rigi");
        assertThat(session.outcome().get("appointmentNote")).isEqualTo("Birinchi marta kelmoqda");
        assertThat(session.disposition()).isEqualTo(Disposition.GOAL_REACHED);
    }

    @Test
    void bookAppointmentRequiresDateAndTime() {
        String resNoDate = tools.bookAppointment("Rahmat", null, "14:00", "Ali", null, null);
        assertThat(resNoDate).startsWith("ERROR: no appointment date");
        assertThat(session.outcome().get("appointmentBooked")).isNull();

        String resNoTime = tools.bookAppointment("Rahmat", "2026-10-15", "", "Ali", null, null);
        assertThat(resNoTime).startsWith("ERROR: no appointment time");
        assertThat(session.outcome().get("appointmentBooked")).isNull();
    }

    @Test
    void checkCalendarAvailabilityReturnsAvailableSlots() {
        String res = tools.checkCalendarAvailability("Hozir bo'sh vaqtlarni tekshiraman", "2026-10-15", "Konsultatsiya");
        assertThat(res).contains("Free times on 2026-10-15");
        assertThat(session.toolReplies()).isEqualTo("Hozir bo'sh vaqtlarni tekshiraman");
    }

    @Test
    void cancelAppointmentRecordsCancellation() {
        String res = tools.cancelAppointment("Uchrashuvingiz bekor qilindi", "2026-10-15", "14:00", "Vaqt to'g'ri kelmadi");
        assertThat(res).contains("Uchrashuv muvaffaqiyatli bekor qilindi");
        assertThat(session.toolReplies()).isEqualTo("Uchrashuvingiz bekor qilindi");
        assertThat(session.outcome().get("appointmentCancelled")).isEqualTo(true);
        assertThat(session.outcome().get("cancelledAppointmentDate")).isEqualTo("2026-10-15");
        assertThat(session.outcome().get("cancelledAppointmentTime")).isEqualTo("14:00");
        assertThat(session.outcome().get("cancellationReason")).isEqualTo("Vaqt to'g'ri kelmadi");
    }
}
