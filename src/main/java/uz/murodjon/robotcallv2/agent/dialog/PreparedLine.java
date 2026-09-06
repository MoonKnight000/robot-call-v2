package uz.murodjon.robotcallv2.agent.dialog;

/**
 * One line of a reply synthesized ahead of its place in the queue ({@link SpeechOutput#prepare}),
 * waiting to be handed to the caller in order ({@link SpeechOutput#deliver}).
 *
 * @param text    what the audio says, as it was sent to synthesis
 * @param pcm     the audio, or {@code null} when the line will not be spoken
 * @param outcome why it will not be, or {@link SpeechOutcome#SPOKEN} when it will
 */
public record PreparedLine(String text, short[] pcm, SpeechOutcome outcome) {

    static PreparedLine refused(String text, SpeechOutcome outcome) {
        return new PreparedLine(text, null, outcome);
    }
}
