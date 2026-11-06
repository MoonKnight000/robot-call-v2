package uz.murodjon.robotcallv2.agent.audio;

/**
 * Told once how an {@link AnsweringMachineDetector} decided: a machine, or a window that closed
 * on a person. A call that ends before either never reports.
 */
@FunctionalInterface
public interface AmdVerdictListener {

    /**
     * @param machine whether a machine was detected
     * @param afterMs how long after the call connected the verdict came
     */
    void onVerdict(boolean machine, int afterMs);
}
