package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeRetrievalUseCase;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;

import java.util.List;

/**
 * The company's documents, reachable from a realtime call.
 *
 * <p>A tool rather than prompt context, because a realtime engine has no per-turn prompt
 * to put context in: the session's instructions are set once when the call opens, and by
 * then nobody has asked anything. The cascade pipeline does not need this — it rebuilds an
 * annex every turn and the passages are already in it ({@code SystemPromptFactory}).
 *
 * <p>Registered only for agents with {@code useRag} on, and the cost is the same one the
 * fact tool pays: a beat of silence while the engine waits for the answer. That is the
 * trade — a short pause against the bot inventing the company's policy on a recorded line.
 */
public class RealtimeKnowledgeTools {

    private static final Logger log = LoggerFactory.getLogger(RealtimeKnowledgeTools.class);

    /** Deliberately explicit: the engine must say it does not know rather than fill the gap. */
    private static final String NOTHING_FOUND = "NOT FOUND IN THE DOCUMENTS — do not invent an answer to "
            + "this question. Say you do not know or hand over to an operator.";

    private static final int MAX_PASSAGES = 3;
    private static final int MAX_PASSAGE_CHARS = 900;

    private final RealtimeDialogSession session;
    private final KnowledgeRetrievalUseCase knowledgeRetrieval;

    public RealtimeKnowledgeTools(RealtimeDialogSession session, KnowledgeRetrievalUseCase knowledgeRetrieval) {
        this.session = session;
        this.knowledgeRetrieval = knowledgeRetrieval;
    }

    @Tool(description = "Searches the company's documents (uploaded files and links) for information on the "
            + "client's question. When the client asks about the company's rules, prices, services or "
            + "terms, call THIS TOOL and answer only from the text it returns. Do not add anything of "
            + "your own beyond that text.")
    public String searchKnowledgeBase(@ToolParam(description = "the client's question, in their own words")
                                      String question) {
        try {
            List<KnowledgePassage> passages = knowledgeRetrieval.findRelevantPassages(
                    session.companyId(), agentId(), question, MAX_PASSAGES);
            if (passages.isEmpty()) {
                log.debug("[{}] knowledge search for '{}' found nothing", session.channelId(), question);
                return NOTHING_FOUND;
            }
            StringBuilder answer = new StringBuilder();
            for (KnowledgePassage passage : passages) {
                answer.append("[").append(PromptSafeText.sanitize(passage.sourceName(), 80)).append("] ")
                        .append(PromptSafeText.sanitize(passage.content(), MAX_PASSAGE_CHARS))
                        .append('\n');
            }
            log.debug("[{}] knowledge search for '{}' returned {} passages",
                    session.channelId(), question, passages.size());
            return answer.toString();
        } catch (Exception e) {
            // A failed lookup must read as "nothing found", not as an error the engine
            // might relay to the caller.
            log.warn("[{}] knowledge search failed: {}", session.channelId(), e.getMessage());
            return NOTHING_FOUND;
        }
    }

    private Long agentId() {
        return session.agent() != null ? session.agent().id() : null;
    }
}
