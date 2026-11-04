package uz.murodjon.robotcallv2.agent.turn;

/**
 * The text end-of-turn model ({@link TextTurnDetector}, NAMO) as a {@link TurnSignal}: scores the
 * recognizer's latest interim.
 */
public final class TextTurnSignal implements TurnSignal {

    private final TextTurnDetector detector;
    private final float threshold;
    private final float earlyThreshold;

    public TextTurnSignal(TextTurnDetector detector, float threshold, float earlyThreshold) {
        this.detector = detector;
        this.threshold = threshold;
        this.earlyThreshold = earlyThreshold;
    }

    @Override
    public String name() {
        return "text-turn";
    }

    @Override
    public TurnOpinion judge(TurnMoment moment) {
        return TurnOpinion.fromProbability(detector.completion(moment.interim()), threshold, earlyThreshold);
    }
}
