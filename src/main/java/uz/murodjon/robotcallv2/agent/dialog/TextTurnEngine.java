package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One turn of the agent, driven by text and answered in text
 * (UNIVERSAL-OPERATOR-PLAN E.4).
 *
 * <p>The same turn {@link TurnRunner} runs on a call, with the telephony taken out: the same
 * cached prompt prefix, the same per-turn annex, the same stage-scoped tool set, the same
 * tools — including the company's own REST tools when the session carries them — and the
 * same fact guard over the line. What is missing is only the parts that exist because a
 * call has audio: recognition, synthesis, barge-in, the silence watchdog and the endpoint.
 *
 * <p>This is what makes "the same agent" true rather than a claim. An evaluation
 * ({@link AgentSimulator}) and a chat both come through here, so a scenario cannot behave
 * one way when it is tested and another way when a person types at it — and a fix to how a
 * turn is built reaches every channel at once.
 *
 * <p>Holds no state. Everything a turn needs is on the {@link DialogSession} it is given,
 * which is what lets a chat rebuild a session from its stored transcript between messages
 * and continue where it left off on whichever instance the next message lands.
 */
@Component
public class TextTurnEngine {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final SystemPromptFactory promptFactory;
    private final TurnTools turnTools;

    public TextTurnEngine(ObjectProvider<ChatModel> chatModelProvider, SystemPromptFactory promptFactory,
                          TurnTools turnTools) {
        this.chatModelProvider = chatModelProvider;
        this.promptFactory = promptFactory;
        this.turnTools = turnTools;
    }

    /** Whether there is a model to answer with at all. */
    public boolean available() {
        return chatModelProvider.getIfAvailable() != null;
    }

    /**
     * Answers {@code clientText} and records the turn on {@code session}: the caller's line
     * and the agent's go into its history, the tools it called have run, and its stage,
     * outcome and ended flag are whatever those tools left behind.
     *
     * @param chatClient the client to answer with; a caller that runs many turns builds it once
     */
    public TextTurn reply(ChatClient chatClient, DialogSession session, String clientText) {
        session.history().add(new UserMessage(clientText));
        List<Message> messages = new ArrayList<>(session.history());
        messages.add(new UserMessage(promptFactory.turnAnnex(session)));
        session.clearToolReplies();

        List<ToolCallback> tools = turnTools.build(session);
        ChatResponse response = chatClient.prompt()
                .system(systemPrefix(session))
                .messages(messages)
                .options(turnTools.buildOptions(session, tools).mutate())
                .call()
                .chatResponse();

        turnTools.run(session, tools, turnTools.extractCalls(response));
        String line = textOf(response);
        if (line == null || line.isBlank()) {
            line = session.toolReplies(); // a tool-only turn carries its line in the tool call
        }
        if (line == null || line.isBlank()) {
            return new TextTurn(null, List.of());
        }
        List<String> violations = FactGuard.violations(line, session.scenario(), session.context(),
                session.toolFigures());
        session.history().add(new AssistantMessage(line));
        session.setLastAgentText(line);
        return new TextTurn(line, violations);
    }

    /** Answers with a client of its own — for a caller that runs a single turn. */
    public TextTurn reply(DialogSession session, String clientText) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return new TextTurn(null, List.of());
        }
        return reply(TurnTools.chatClient(chatModel), session, clientText);
    }

    /** The prompt prefix, built once per conversation exactly as a call caches it. */
    private String systemPrefix(DialogSession session) {
        String prefix = session.systemPrefix();
        if (prefix == null) {
            prefix = promptFactory.stablePrefix(session);
            session.setSystemPrefix(prefix);
        }
        return prefix;
    }

    private static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }
}
