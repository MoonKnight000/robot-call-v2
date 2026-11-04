package uz.murodjon.robotcallv2.agent.turn;

import uz.murodjon.robotcallv2.agent.audio.AudioListener;

/**
 * Smart Turn ({@link SmartTurnDetector}) as a {@link TurnSignal}: scores the last seconds of the
 * caller's audio, which it keeps in its own {@link UtteranceBuffer}.
 */
public final class SmartTurnSignal implements TurnSignal {

    private final SmartTurnDetector detector;
    private final UtteranceBuffer buffer;
    private final float threshold;
    private final float earlyThreshold;

    public SmartTurnSignal(SmartTurnDetector detector, float threshold, float earlyThreshold) {
        this.detector = detector;
        this.buffer = new UtteranceBuffer(detector.samples());
        this.threshold = threshold;
        this.earlyThreshold = earlyThreshold;
    }

    @Override
    public String name() {
        return "smart-turn";
    }

    @Override
    public TurnOpinion judge(TurnMoment moment) {
        return TurnOpinion.fromProbability(detector.scoreCompletion(buffer.recent(), buffer.length()),
                threshold, earlyThreshold);
    }

    @Override
    public AudioListener audioListener() {
        return buffer;
    }
}
