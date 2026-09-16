package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeSession;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

/**
 * The realtime counterpart of {@link SilenceWatchdogRunner}: asks a caller who has gone
 * quiet whether they are still there, gives up on them after that, and closes a call
 * that has run past its time limit.
 *
 * <p>A speech-to-speech engine only speaks when spoken to. On a line where nobody says
 * anything — a phone set down, a caller who walked off — it sits in silence until the
 * duration cap, and the only thing that ever ended such calls was the ARI ghost-call
 * reaper a minute after that. The decisions are the cascade pipeline's own
 * ({@link NoInputWatchdog}, the same thresholds); only the delivery differs: nothing is
 * spoken from code here, because anything but the engine's voice on a realtime call is a
 * second person joining the line. The engine is told what to say instead.
 *
 * <p>Ticks on the shared scheduler thread and only decides; every send goes to the worker.
 */
@Component
public class RealtimeWatchdogRunner {

    private static final Logger log = LoggerFactory.getLogger(RealtimeWatchdogRunner.class);

    private static final Duration TICK = Duration.ofSeconds(1);

    /** Instructions to the engine — never heard by the caller; what they hear is the answer. */
    static final String STILL_THERE = "[TIZIM: mijoz bir necha soniyadan beri jim. Qisqa qilib "
            + "eshityaptimi deb so'rang va oxirgi savolingizni qisqa takrorlang.]";
    static final String GIVE_UP = "[TIZIM: mijoz javob bermayapti. Bir gap bilan muloyim xayrlashing "
            + "va endCall chaqiring.]";
    static final String TIME_UP = "[TIZIM: qo'ng'iroq uchun ajratilgan vaqt tugadi. Bir gap bilan "
            + "muloyim xayrlashing va endCall chaqiring.]";

    private final DialogProperties dialogProperties;
    private final DialogExecutors executors;
    private final VoiceMetrics metrics;
    private final AiModelConfigUseCase aiModelConfigUseCase;
    private final DialogTuningProperties dialogTuning;
    private final CallBillingUseCase callBillingUseCase;
    private final DialogPolicy dialogPolicy;

    public RealtimeWatchdogRunner(DialogProperties dialogProperties, DialogExecutors executors,
                                  VoiceMetrics metrics, AiModelConfigUseCase aiModelConfigUseCase,
                                  DialogTuningProperties dialogTuning, CallBillingUseCase callBillingUseCase,
                                  DialogPolicy dialogPolicy) {
        this.dialogProperties = dialogProperties;
        this.executors = executors;
        this.metrics = metrics;
        this.aiModelConfigUseCase = aiModelConfigUseCase;
        this.dialogTuning = dialogTuning;
        this.callBillingUseCase = callBillingUseCase;
        this.dialogPolicy = dialogPolicy;
    }

    /**
     * Begin watching {@code s}.
     *
     * @param finisher hangs the call up once whatever is playing has drained; run on the
     *                 worker, and expected to be safe to call more than once
     */
    public void start(RealtimeDialogSession s, Consumer<RealtimeDialogSession> finisher) {
        if (dialogProperties.noInputSeconds() > 0) {
            s.setWatchdog(new NoInputWatchdog(Duration.ofSeconds(dialogProperties.noInputSeconds()),
                    dialogProperties.noInputMaxPrompts(), Instant.now()));
        }
        // Decided once, here: the company cap is a database read, and the tick runs every
        // second for the life of the call.
        EffectiveAiModelConfig companyLimits = aiModelConfigUseCase.findEffectiveByCompanyId(s.companyId());
        s.setMaxCallSeconds(CallLimits.capByBalance(
                dialogPolicy.resolveMaxCallSeconds(companyLimits.maxCallSeconds(), s.agent()),
                callBillingUseCase.findAffordableCallSeconds(s.companyId(), true)));
        s.setMaxTokensPerCall(companyLimits.maxTokensPerCall());
        s.setWatchdogTask(executors.scheduleTicks(() -> tick(s, finisher), TICK));
    }

    public void stop(RealtimeDialogSession s) {
        ScheduledFuture<?> task = s.watchdogTask();
        if (task != null) {
            task.cancel(false);
        }
    }

    /**
     * Ask the engine to close the call: mark it ended (keeping any outcome it already
     * reached), tell the engine to say goodbye, and make sure the line drops even if it
     * never does. The engine's own turn completing is what normally hangs up
     * ({@code RealtimeDialogEngine} finishes an ended call once its turn is over).
     */
    public void requestFarewell(RealtimeDialogSession s, String instruction, Consumer<RealtimeDialogSession> finisher) {
        if (s.isEnded()) {
            return;
        }
        s.end(s.disposition() != null ? s.disposition() : Disposition.HUNG_UP);
        RealtimeSession engine = s.engine();
        if (engine != null) {
            executors.submit(() -> CallMdc.with(s.channelId(), () -> engine.sendUserText(instruction)));
        }
        executors.scheduleOnWorker(() -> CallMdc.with(s.channelId(), () -> finisher.accept(s)), dialogTuning.farewellGraceMs());
    }

    private void tick(RealtimeDialogSession s, Consumer<RealtimeDialogSession> finisher) {
        try {
            if (s.isEnded()) {
                return;
            }
            long elapsed = Duration.between(s.startedAt(), Instant.now()).getSeconds();
            int maxSeconds = s.maxCallSeconds();
            if (maxSeconds > 0 && elapsed >= maxSeconds) {
                CallMdc.with(s.channelId(), () -> log.warn("[{}] realtime call reached its {}s limit — closing",
                        s.channelId(), maxSeconds));
                requestFarewell(s, TIME_UP, finisher);
                return;
            }
            NoInputWatchdog watchdog = s.watchdog();
            if (watchdog == null) {
                return;
            }
            boolean answerComing = s.toolsInFlight().get() > 0 || s.isEngineWorking()
                    || s.awaitingAnswer(System.nanoTime());
            switch (watchdog.check(Instant.now(), s.endpoint().isPlaying(), answerComing)) {
                case NONE -> {
                }
                case PROMPT -> {
                    metrics.noInputPrompt();
                    RealtimeSession engine = s.engine();
                    if (engine != null) {
                        executors.submit(() -> CallMdc.with(s.channelId(), () -> {
                            log.info("[{}] no input for {}s — asking the engine to check on the caller ({}/{})",
                                    s.channelId(), dialogProperties.noInputSeconds(), watchdog.prompts(),
                                    dialogProperties.noInputMaxPrompts());
                            engine.sendUserText(STILL_THERE);
                        }));
                    }
                }
                case END -> {
                    metrics.noInputHangup();
                    CallMdc.with(s.channelId(), () -> log.info("[{}] closing realtime call (no input after {} prompt(s))",
                            s.channelId(), watchdog.prompts()));
                    requestFarewell(s, GIVE_UP, finisher);
                }
            }
        } catch (Exception e) {
            log.warn("[{}] realtime watchdog tick failed: {}", s.channelId(), e.getMessage());
        }
    }

}
