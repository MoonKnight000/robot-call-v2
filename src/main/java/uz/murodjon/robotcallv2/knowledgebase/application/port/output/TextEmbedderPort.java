package uz.murodjon.robotcallv2.knowledgebase.application.port.output;

import java.util.List;

/**
 * Turns text into the vector the knowledge search compares with.
 *
 * <p>Every method may return {@code null} (or nulls inside the list): the embedding model
 * is an external service, and a knowledge base that cannot be embedded must still be
 * stored and still be searchable by keyword rather than failing the upload or the call.
 */
public interface TextEmbedderPort {

    boolean available();

    /** One vector per input, positionally; an entry is null if that text could not be embedded. */
    List<float[]> embedAll(List<String> texts);

    /** The caller's question, embedded for search. Null when the model is unreachable. */
    float[] embedQuery(String text);
}
