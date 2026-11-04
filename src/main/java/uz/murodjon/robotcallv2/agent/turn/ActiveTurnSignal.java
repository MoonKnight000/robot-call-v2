package uz.murodjon.robotcallv2.agent.turn;

/**
 * The {@link TurnSignal} that decides a call's pauses, with the waits its settings give it.
 *
 * @param signal      the signal
 * @param maxExtendMs silence added to a pause it calls unfinished
 * @param earlyWaitMs silence after which a pause it is sure of closes early; 0 = never early
 */
public record ActiveTurnSignal(TurnSignal signal, int maxExtendMs, int earlyWaitMs) {
}
