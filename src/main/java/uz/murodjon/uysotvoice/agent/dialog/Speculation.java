package uz.murodjon.uysotvoice.agent.dialog;

import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * An LLM reply started on an interim transcript — before the caller had finished
 * speaking, and before anything knew whether they had.
 *
 * <p>The wait between "the caller stopped making noise" and "the recognizer says what
 * they said" is dead time in the §1.3 budget: several hundred milliseconds of silence
 * that exist only so the endpointer can be sure. This spends that silence writing the
 * reply. If the final says what the interim did, the answer is already part-written; if
 * it does not, the work is thrown away and the turn proceeds as it always did.
 *
 * <p>{@code responses} is a cached {@code Flux} with a subscription already open, which
 * is what makes the takeover free: the chunks generated during the wait are buffered, a
 * second subscriber is replayed every one of them at once, and the stream then continues
 * live. So the turn that adopts this speaks from the first sentence immediately and
 * streams the rest exactly as an ordinary turn would — no branch in
 * {@code DialogEngine#consume}, and nothing generated early is lost.
 *
 * @param inputText the interim transcript this was written for. The final has to say the
 *                  same thing, or the reply answers words the caller never said
 * @param responses the cached, already-running reply stream
 * @param warmUp    the subscription keeping it running — and what cancels it
 */
public record Speculation(String inputText, Flux<ChatResponse> responses, Disposable warmUp) {

    /** Stop the request and throw away whatever it had produced. */
    void discard() {
        warmUp.dispose();
    }
}
