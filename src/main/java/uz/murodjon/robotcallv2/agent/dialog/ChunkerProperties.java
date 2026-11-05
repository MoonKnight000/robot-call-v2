package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where {@link SentenceSplitter} cuts a streamed reply into the pieces synthesized one at a
 * time. Bound from {@code voice-agent.dialog.chunker.*}; a value left at 0 keeps its default.
 *
 * <p>Only the opening piece is on the caller's clock — everything after it is synthesized
 * while that one plays — so the opening is cut early, at a comma once it has a few words.
 * The later pieces keep whole sentences: each piece ends on a sentence-final fall, and a
 * short comma cut makes "Murodjon aka," sound like the end of a sentence. The cost of the
 * early opening cut is that one fall; {@code chunk_wait} ({@code TurnLatency}) shows what it buys.
 *
 * @param openingMinChars    shortest opening piece cut at a sentence end
 * @param openingClauseWords words after which the opening piece may be cut at a comma
 * @param minChunkChars      shortest later piece worth its own synthesis round trip
 * @param longClauseChars    shortest later clause that may be cut at a comma
 */
@ConfigurationProperties(prefix = "voice-agent.dialog.chunker")
public record ChunkerProperties(int openingMinChars, int openingClauseWords, int minChunkChars,
                                int longClauseChars) {

    static final int DEFAULT_OPENING_MIN_CHARS = 14;
    static final int DEFAULT_OPENING_CLAUSE_WORDS = 4;
    static final int DEFAULT_MIN_CHUNK_CHARS = 20;
    static final int DEFAULT_LONG_CLAUSE_CHARS = 90;

    /** The defaults, for a caller outside Spring (tests). */
    static final ChunkerProperties DEFAULTS = new ChunkerProperties(0, 0, 0, 0);

    public ChunkerProperties {
        openingMinChars = openingMinChars > 0 ? openingMinChars : DEFAULT_OPENING_MIN_CHARS;
        openingClauseWords = openingClauseWords > 0 ? openingClauseWords : DEFAULT_OPENING_CLAUSE_WORDS;
        minChunkChars = minChunkChars > 0 ? minChunkChars : DEFAULT_MIN_CHUNK_CHARS;
        longClauseChars = longClauseChars > 0 ? longClauseChars : DEFAULT_LONG_CLAUSE_CHARS;
    }
}
