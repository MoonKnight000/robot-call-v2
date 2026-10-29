package uz.murodjon.robotcallv2.chat.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.CallContext;
import uz.murodjon.robotcallv2.agent.dialog.DialogSession;
import uz.murodjon.robotcallv2.agent.dialog.PromptTemplateEngine;
import uz.murodjon.robotcallv2.agent.dialog.TextTurn;
import uz.murodjon.robotcallv2.agent.dialog.TextTurnEngine;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;
import uz.murodjon.robotcallv2.chat.application.port.output.ChatQuotaPort;
import uz.murodjon.robotcallv2.chat.application.port.output.ChatSessionRepository;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatMessage;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatSession;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatTurn;
import uz.murodjon.robotcallv2.chat.domain.entity.StartedChat;
import uz.murodjon.robotcallv2.chat.domain.enums.ChatRole;
import uz.murodjon.robotcallv2.chat.infrastructure.config.ChatProperties;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.dialog.CallLanguage;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A text conversation with an agent, whichever channel it arrived on
 * (UNIVERSAL-OPERATOR-PLAN E.4).
 *
 * <p>The widget and the bot differ in exactly two things: how a request proves it may talk to
 * this agent, and how the answer is delivered. Everything between those — the quota, the
 * session, the turn, the transcript, the limits, the stage — is here, so a channel added
 * later is a controller and an adapter rather than a second copy of the conversation.
 *
 * <p>The turn itself is {@link TextTurnEngine}: the turn an evaluation runs and the turn a
 * call runs, with the audio taken out. The scenario, its stages, its guardrails, the
 * company's REST tools, the MCP tools and the fact guard are the ones the phone gets.
 *
 * <p>Nothing is held in memory between messages: the conversation is rebuilt from its stored
 * transcript and stage every time, which is what lets a person answer an hour later, on
 * whichever instance their message lands on.
 *
 * <p>Two things a call has that a text conversation deliberately does not. It is always an
 * {@link AgentPersona#AI_ASSISTANT}: a chat window or a bot that claims to be a live person
 * is the one thing a text channel must never do, whatever the agent was saved with. And a
 * fact-guard violation cannot be caught before it is read — there is no synthesis step to
 * hold — so it is logged against the conversation rather than swallowed.
 */
@Component
class ChatConversations {

    private static final Logger log = LoggerFactory.getLogger(ChatConversations.class);

    /** Where a conversation keeps the person's number once it learns it. */
    private static final String PHONE_KEY = "phone";
    /** Where it keeps the CRM lead that number belongs to — what its note is written onto. */
    private static final String CRM_LEAD_KEY = "crmLeadId";
    /** Set when the conversation asked the CRM to open a lead for a number it did not know. */
    private static final String CRM_LEAD_REQUESTED_KEY = "crmLeadRequested";

    /** What the agent is told when it has no opening line of its own — the bootstrap a call's greeting turn uses. */
    private static final String GREETING_BOOTSTRAP =
            "[SYSTEM: The client has started a chat. Greet them briefly as planned and ask how you can "
                    + "help.]";

    private final ChatSessionRepository repository;
    private final ChatQuotaPort chatQuota;
    private final AiAgentUseCase aiAgentUseCase;
    private final ToolUseCase toolUseCase;
    private final AiModelConfigUseCase aiModelConfigUseCase;
    private final CompanyUseCase companyUseCase;
    private final ClientMemoryUseCase clientMemoryUseCase;
    private final TextTurnEngine textTurnEngine;
    private final ChatProperties chatProperties;
    private final Clock clock;
    private final CrmUseCase crmUseCase;

    ChatConversations(ChatSessionRepository repository, ChatQuotaPort chatQuota, AiAgentUseCase aiAgentUseCase,
                      ToolUseCase toolUseCase, AiModelConfigUseCase aiModelConfigUseCase,
                      CompanyUseCase companyUseCase, ClientMemoryUseCase clientMemoryUseCase, TextTurnEngine textTurnEngine,
                      ChatProperties chatProperties, Clock clock, CrmUseCase crmUseCase) {
        this.repository = repository;
        this.chatQuota = chatQuota;
        this.aiAgentUseCase = aiAgentUseCase;
        this.toolUseCase = toolUseCase;
        this.aiModelConfigUseCase = aiModelConfigUseCase;
        this.companyUseCase = companyUseCase;
        this.clientMemoryUseCase = clientMemoryUseCase;
        this.textTurnEngine = textTurnEngine;
        this.chatProperties = chatProperties;
        this.clock = clock;
        this.crmUseCase = crmUseCase;
    }

    /**
     * Opens a conversation and greets, once the agent's day has room for one.
     *
     * <p>Deliberately not one transaction, here and in {@link #answer}: greeting and
     * answering are model calls of several seconds, and holding a database connection across
     * one is how a busy afternoon becomes an outage for everything else.
     *
     * @param session what the channel decided about it — {@code ChatSession.startingOn…}
     */
    StartedChat start(ChatSession session) {
        AiAgent agent = requireAgent(session);
        // Counted before a single token is spent, and refused when the counter cannot be
        // reached: both channels are reachable by anything that can send an HTTP request.
        if (!chatQuota.bookSession(agent.id(), chatProperties.dailySessionsPerAgent())) {
            throw new ConflictException(ErrorCode.CHAT_DAILY_LIMIT_REACHED, agent.id());
        }
        ScenarioDefinition scenario = scenarioOf(session, agent);
        String phone = extractPhone(session.outcome());
        ClientMemory memory = findMemory(session.companyId(), phone);
        // The client's remembered language, else the channel's, else the agent's — settled here so
        // the conversation is stored (and rebuilt) with the language it is actually being held in.
        String resolvedLanguage = CallLanguage.resolve(memory != null ? memory.preferredLanguage() : null,
                session.language(), agent.language());
        ChatSession saved = repository.save(session.withLanguage(resolvedLanguage));
        DialogSession dialog = newDialogSession(saved, agent, scenario, phone, memory);
        String greeting = greet(dialog, agent);
        repository.addMessage(ChatMessage.written(saved.id(), ChatRole.AGENT, greeting));
        // The stage from the dialog, not the one it started in: an agent with no opening line
        // of its own greets by taking a turn, and that turn may already have moved the FSM.
        boolean ended = dialog.isEnded();
        Disposition disposition = ended ? dialog.disposition() : null;
        ChatSession afterGreeting = repository.save(saved.afterTurn(dialog.state(), dialog.outcome(), disposition,
                ended, 1, clock.instant()));
        if (ended) {
            rememberIfEnded(afterGreeting, dialog, agent, disposition);
        }
        return new StartedChat(afterGreeting, greeting);
    }

    /** Answers one message in an open conversation and writes both lines down. */
    ChatTurn answer(ChatSession session, String text) {
        AiAgent agent = requireAgent(session);
        DialogSession dialog = rebuild(session, agent);
        TextTurn turn = textTurnEngine.reply(dialog, text);
        if (turn.isEmpty()) {
            throw new ExternalServiceException(ErrorCode.CHAT_NO_REPLY, "llm");
        }
        if (!turn.violations().isEmpty()) {
            // Nothing to withhold — the person is reading it as it arrives. Logged where the
            // quality alerts already look, because a figure the facts do not support is the
            // one failure this system exists to prevent (§4.4).
            log.error("QA: chat {} stated figures the facts do not support: {}",
                    session.sessionKey(), turn.violations());
        }
        repository.addMessage(ChatMessage.written(session.id(), ChatRole.CLIENT, text));
        repository.addMessage(ChatMessage.written(session.id(), ChatRole.AGENT, turn.line()));

        boolean full = session.messageCount() + 2 >= chatProperties.maxMessagesPerSession();
        boolean ended = dialog.isEnded() || full;
        Disposition disposition = ended ? dialog.disposition() : null;
        ChatSession saved = repository.save(session.afterTurn(dialog.state(), dialog.outcome(), disposition,
                ended, 2, clock.instant()));
        if (ended) {
            rememberIfEnded(saved, dialog, agent, disposition);
        }
        return new ChatTurn(turn.line(), saved);
    }

    /**
     * The person's number, learned mid-conversation (a Telegram contact share): kept on the
     * conversation so every later turn has their memory and the agent's CRM tools, and looked up
     * in the CRM once — their name and contract become facts, the lead is where the chat's note
     * goes when it ends. A number already known is left as it is.
     */
    ChatSession attachPhone(ChatSession session, String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        if (phone == null || phone.equals(extractPhone(session.outcome()))) {
            return session;
        }
        Map<String, Object> outcome = new LinkedHashMap<>(session.outcome());
        outcome.put(PHONE_KEY, phone);
        try {
            CrmClientSnapshot client = crmUseCase.findByPhone(session.companyId(), phone);
            if (client != null) {
                putIfAbsent(outcome, "clientName", client.name());
                putIfAbsent(outcome, "contractNumber", client.contractNumber());
                if (client.leadId() != null) {
                    outcome.put(CRM_LEAD_KEY, client.leadId());
                }
            } else if (crmUseCase.createLead(session.companyId(), null, phone,
                    session.telegramBotId() != null ? "TELEGRAM" : "WEBSITE")) {
                // Somebody new: the CRM opens a lead for them, and the chat's note goes onto it once
                // the conversation ends — by then the asynchronous write has long landed.
                outcome.put(CRM_LEAD_REQUESTED_KEY, true);
            }
        } catch (Exception e) {
            log.warn("[chat {}] CRM lookup by phone failed: {}", session.sessionKey(), e.getMessage());
        }
        return repository.save(session.afterTurn(session.stage(), outcome, session.disposition(), session.ended(), 0,
                session.lastMessageAt()));
    }

    /** Whether the person behind this conversation is known by number yet. */
    boolean hasPhone(ChatSession session) {
        return extractPhone(session.outcome()) != null;
    }

    private static void putIfAbsent(Map<String, Object> outcome, String key, String value) {
        if (value != null && !value.isBlank() && !outcome.containsKey(key)) {
            outcome.put(key, value);
        }
    }

    /** Whether this conversation may still be written to, and why not when it may not. */
    void requireOpen(ChatSession session) {
        if (session.ended()) {
            throw new ConflictException(ErrorCode.CHAT_SESSION_ENDED, session.sessionKey());
        }
        if (session.messageCount() >= chatProperties.maxMessagesPerSession()) {
            throw new ConflictException(ErrorCode.CHAT_SESSION_FULL, chatProperties.maxMessagesPerSession());
        }
        if (isSilentTooLong(session)) {
            throw new ConflictException(ErrorCode.CHAT_SESSION_EXPIRED, chatProperties.sessionTtlMinutes());
        }
    }

    /** The same question without the refusal — a bot starts a new conversation instead of complaining. */
    boolean isOver(ChatSession session) {
        return session.ended() || session.messageCount() >= chatProperties.maxMessagesPerSession()
                || isSilentTooLong(session);
    }

    int messagesLeft(ChatSession session) {
        return Math.max(0, chatProperties.maxMessagesPerSession() - session.messageCount());
    }

    int maxMessages() {
        return chatProperties.maxMessagesPerSession();
    }

    boolean modelAvailable() {
        return textTurnEngine.available();
    }

    /** The scenario's entry point, by the same convention a call starts on. */
    String firstStageOf(long companyId, long agentId) {
        AiAgent agent = aiAgentUseCase.requirePublishedAgent(companyId, agentId);
        ScenarioDefinition scenario = aiAgentUseCase.resolveScenario(companyId, agent).definition();
        return scenario != null && scenario.stages() != null && !scenario.stages().isEmpty()
                ? scenario.findStartStage().id()
                : "CONVERSATION";
    }

    /** A handle no one can guess their way into somebody else's conversation with. */
    String newSessionKey() {
        return "cht_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private boolean isSilentTooLong(ChatSession session) {
        Instant silentSince = session.lastMessageAt() != null ? session.lastMessageAt() : session.createdAt();
        return silentSince != null && silentSince
                .plus(Duration.ofMinutes(chatProperties.sessionTtlMinutes()))
                .isBefore(clock.instant());
    }

    /**
     * The agent's own opening line, or — when it has none — one turn seeded the way a call's
     * greeting turn is, so the agent opens in its scenario's words rather than in ours.
     */
    private String greet(DialogSession dialog, AiAgent agent) {
        String firstMessage = agent.script().firstMessage();
        if (firstMessage != null && !firstMessage.isBlank()) {
            Map<String, Object> facts = dialog.context() != null ? dialog.context().facts() : Map.of();
            String rendered = PromptTemplateEngine.render(firstMessage.trim(), facts);
            dialog.history().add(new AssistantMessage(rendered));
            dialog.setLastAgentText(rendered);
            return rendered;
        }
        TextTurn turn = textTurnEngine.reply(dialog, GREETING_BOOTSTRAP);
        if (turn.isEmpty()) {
            throw new ExternalServiceException(ErrorCode.CHAT_NO_REPLY, "llm");
        }
        return turn.line();
    }

    /** The conversation as the agent left it: its stage, what its tools recorded, and everything said. */
    private DialogSession rebuild(ChatSession session, AiAgent agent) {
        String phone = extractPhone(session.outcome());
        DialogSession dialog = newDialogSession(session, agent, scenarioOf(session, agent), phone,
                findMemory(session.companyId(), phone));
        dialog.setState(session.stage());
        session.outcome().forEach(dialog::recordOutcome);
        for (ChatMessage message : repository.findMessages(session.id())) {
            if (message.role() == ChatRole.AGENT) {
                dialog.history().add(new AssistantMessage(message.text()));
                dialog.setLastAgentText(message.text());
            } else {
                dialog.history().add(new UserMessage(message.text()));
            }
        }
        return dialog;
    }

    /**
     * The same session a call builds, minus everything that exists because a call has audio:
     * no endpoint, no watchdog, no hangup, no voice. The company's tools are the real ones,
     * and the MCP tool set is left unbuilt so the turn builds it exactly as a call does.
     */
    private DialogSession newDialogSession(ChatSession session, AiAgent agent, ScenarioDefinition scenario,
                                           String phone, ClientMemory memory) {
        // The chat widget stays on the platform's model even for a company on its own LLM (3.14 covers calls).
        EffectiveAiModelConfig aiModel = aiModelConfigUseCase.findEffectiveByCompanyId(session.companyId())
                .withoutOwnLlm()
                .withOverrides(agent.llmModel(), agent.temperature(), agent.maxOutputTokens());
        Company company = companyUseCase.findById(session.companyId());
        List<Tool> tools = toolUseCase.findAgentToolDefinitions(session.companyId(), agent.id());
        Map<String, Object> facts = new LinkedHashMap<>(session.outcome());
        if (!facts.containsKey("clientName") && memory != null && memory.preferredName() != null && !memory.preferredName().isBlank()) {
            facts.put("clientName", memory.preferredName());
        }

        DialogSession dialog = DialogSession.builder("chat-" + session.sessionKey(), session.language())
                .context(new CallContext(facts, null, memory, phone))
                .scenario(scenario)
                .companyId(session.companyId())
                .disclosureEnabled(false)
                .companyName(company != null ? company.name() : null)
                .aiModel(aiModel)
                .emotionAdaptiveVoice(false)
                .firstMessage(agent.script().firstMessage())
                .tools(tools)
                .agent(agent)
                .build();
        dialog.setFastModel(agent.fastLlmModel());
        return dialog;
    }

    private ClientMemory findMemory(long companyId, String phone) {
        return phone != null ? clientMemoryUseCase.findByCompanyIdAndPhone(companyId, phone) : null;
    }

    private String extractPhone(Map<String, Object> outcome) {
        if (outcome == null || outcome.isEmpty()) {
            return null;
        }
        for (String key : List.of(PHONE_KEY, "clientPhone", "client_phone", "phoneNumber", "phone_number", "callerNumber", "contactPhone")) {
            Object val = outcome.get(key);
            if (val != null && !val.toString().isBlank()) {
                String normalized = PhoneNumbers.normalize(val.toString());
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        return null;
    }

    private void rememberIfEnded(ChatSession session, DialogSession dialog, AiAgent agent, Disposition disposition) {
        String phone = extractPhone(session.outcome());
        if (phone == null) {
            phone = extractPhone(dialog.outcome());
        }
        if (phone == null) {
            return;
        }
        try {
            Scenario scenario = aiAgentUseCase.resolveScenario(session.companyId(), agent);
            String summary = resolveChatSummary(session, dialog, disposition);
            RememberedCall rememberedCall = new RememberedCall(
                    clock.instant(),
                    scenario != null ? scenario.scenarioKey() : null,
                    disposition,
                    summary
            );
            Map<String, Object> facts = !dialog.outcome().isEmpty()
                    ? dialog.outcome()
                    : session.outcome();
            clientMemoryUseCase.rememberCall(session.companyId(), phone, rememberedCall, facts);
            postCrmNote(session, summary);
        } catch (Exception e) {
            log.warn("[chat {}] could not update client memory: {}", session.sessionKey(), e.getMessage());
        }
    }

    /** The conversation on the person's CRM lead, when their number led to one ({@link #attachPhone}). */
    private void postCrmNote(ChatSession session, String summary) {
        Object lead = session.outcome().get(CRM_LEAD_KEY);
        if (!(lead instanceof Number) && Boolean.TRUE.equals(session.outcome().get(CRM_LEAD_REQUESTED_KEY))) {
            CrmClientSnapshot created = crmUseCase.findByPhone(session.companyId(), extractPhone(session.outcome()));
            lead = created != null ? created.leadId() : null;
        }
        if (!(lead instanceof Number leadId)) {
            return;
        }
        String channel = session.telegramBotId() != null ? "Telegram" : "Sayt chati";
        crmUseCase.postNote(session.companyId(), leadId.longValue(),
                "AI agent bilan " + channel + " orqali yozishma bo'ldi.\n" + summary);
    }

    private String resolveChatSummary(ChatSession session, DialogSession dialog, Disposition disposition) {
        Object summaryObj = dialog.outcome().get("summary");
        if (summaryObj != null && !summaryObj.toString().isBlank()) {
            return summaryObj.toString().trim();
        }
        Object noteObj = dialog.outcome().get("note");
        if (noteObj != null && !noteObj.toString().isBlank()) {
            return noteObj.toString().trim();
        }
        String channel = session.telegramBotId() != null ? "Telegram" : "Chat Widget";
        StringBuilder sb = new StringBuilder(channel).append(" conversation");
        if (disposition != null) {
            sb.append(" ended with ").append(disposition);
        }
        if (dialog.lastAgentText() != null && !dialog.lastAgentText().isBlank()) {
            String lastText = dialog.lastAgentText().trim();
            sb.append(": ").append(lastText.length() > 200 ? lastText.substring(0, 200) + "..." : lastText);
        }
        return sb.toString();
    }

    private AiAgent requireAgent(ChatSession session) {
        return aiAgentUseCase.requirePublishedAgent(session.companyId(), session.aiAgentId());
    }

    private ScenarioDefinition scenarioOf(ChatSession session, AiAgent agent) {
        return aiAgentUseCase.resolveScenario(session.companyId(), agent).definition();
    }
}
