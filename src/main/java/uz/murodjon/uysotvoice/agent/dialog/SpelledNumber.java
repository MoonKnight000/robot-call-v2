package uz.murodjon.uysotvoice.agent.dialog;

import java.math.BigDecimal;

/**
 * One number found written out in words, kept alongside the words that wrote it.
 *
 * <p>Both halves are needed. {@link #value} is what {@link FactGuard} compares against
 * the call's facts; {@link #words} is what goes in the log and the flag on the attempt,
 * because "besh million" is what someone reviewing the call will hear, and telling them
 * the guard tripped on "5000000" leaves them looking for a figure that was never said.
 *
 * @param words the span exactly as it appeared in the transcript
 * @param value what it adds up to
 */
record SpelledNumber(String words, BigDecimal value) {
}
