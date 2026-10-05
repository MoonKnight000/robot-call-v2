package uz.murodjon.robotcallv2.agent.provider;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;

/**
 * A vendor WebSocket whose messages are JSON: frames are collected until the last one of a
 * message and the whole message is handed to {@link #onMessage}. Subclasses handle the
 * messages, and close/error as their vendor needs.
 *
 * <p>Binary frames are read as JSON too, by default — several endpoints (Gemini, OpenAI)
 * send their JSON as binary frames as readily as text ones, and a listener that dropped
 * them would silently lose messages the session depends on. A stream whose binary frames
 * are audio overrides {@link #onBinaryFrame}.
 *
 * <p>Both callbacks ask for the next frame themselves: overriding {@code onText}/{@code onBinary}
 * replaces the default implementation's own {@code request(1)}, and without it the stream
 * delivers one message and then goes silent.
 *
 * <p>Callbacks arrive one at a time on the WebSocket's thread, so the buffer needs no lock.
 */
public abstract class JsonWebSocketListener implements WebSocket.Listener {

    private final StringBuilder message = new StringBuilder();

    @Override
    public final CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        appendText(data, last);
        webSocket.request(1);
        return null;
    }

    @Override
    public final CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        onBinaryFrame(data, last);
        webSocket.request(1);
        return null;
    }

    /** One complete message, assembled from all its frames. */
    protected abstract void onMessage(String json);

    /** A binary frame; by default part of a JSON message like a text frame. */
    protected void onBinaryFrame(ByteBuffer data, boolean last) {
        appendText(StandardCharsets.UTF_8.decode(data), last);
    }

    private void appendText(CharSequence data, boolean last) {
        message.append(data);
        if (last) {
            String json = message.toString();
            message.setLength(0);
            onMessage(json);
        }
    }
}
