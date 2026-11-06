package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.SpeakingWarmth;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoiceEmotion;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.scenario.domain.entity.*;
import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.util.PersonNames;
import uz.murodjon.robotcallv2.shared.util.PromptText;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Builds the per-turn prompt from a session's bound scenario, facts and FSM stage
 * (PROJECT.md §4.1, §4.4, ROADMAP A.3).
 *
 * <p>Deliberately split in two. {@link #stablePrefix} is everything that cannot change
 * during a call — role, today's date, the facts, the guardrails, the style rules — and
 * is built once per call and reused verbatim as the system message. {@link #turnAnnex}
 * is the part that moves with the FSM (current stage, its purpose, the allowed
 * transitions, a barge-in note) and is appended <em>after</em> the chat history as a
 * transient system aside.
 *
 * <p>The split is what makes the request cacheable. Gemini's implicit context caching
 * only pays off when consecutive requests share a byte-identical <em>prefix</em>; with
 * the state block sitting in the middle of the system message, every transition
 * invalidated the whole conversation and each turn was billed at full price. Keeping
 * the prefix append-only — stable system message, then the growing history — means a
 * turn only pays full rate for what is genuinely new. Putting the state last also puts
 * it closest to the generation point, which is where models follow instructions best.
 */
@Component
public class SystemPromptFactory {

    /**
     * How every note addressed to the model rather than to the caller opens. The model is
     * supposed to act on these, never to repeat them — a reply that carries the marker is
     * an echo of the annex and must not reach the caller ({@link #isSystemNote}).
     */
    public static final String SYSTEM_NOTE_MARKER = "[SYSTEM";

    /**
     * Whether {@code text} is the model reciting a note meant for it. Caught here rather
     * than prompted away, because the one time it happened the caller heard the annex's
     * "current stage: IDENTITY_CHECK" read out loud.
     */
    public static boolean isSystemNote(String text) {
        return text != null && text.contains(SYSTEM_NOTE_MARKER);
    }

    /**
     * Platform-level rules that apply to every scenario regardless of what it declares
     * (ROADMAP A.3/§5.1): a custom scenario's own {@code guardrails} cannot remove or
     * soften these. Domain-specific rules (e.g. "never change the debt amount") belong
     * in the scenario's own {@code guardrails} instead — see the {@code debt-collection}
     * seed for an example. The text lives in {@code resources/prompts/platform-guardrails.txt}.
     */
    static final List<String> PLATFORM_GUARDRAILS = PromptText.lines("platform-guardrails.txt");

    /**
     * How a HUMAN_LIKE agent sounds like the person it says it is. The stage texts carry sample lines,
     * and an agent that said them word for word on every call sounded read off a card; a person also
     * reacts to what was said (an illness, a lost job) before going on, and answers a side question
     * before coming back to the point.
     */
    static final String HUMAN_SPEECH = """
            TALK LIKE A REAL PERSON ON THE PHONE:
            - The stage texts' sample lines show the meaning, not a script: say it in your own everyday words, \
            and never use the same sentence twice in a call.
            - React to what the client actually said, in a few words, the way a person would, then go on: \
            illness — "Voy, tuzalib keting"; lost job — "Eh, bo'lib turadi"; good news — "Zo'r-ku". \
            Plain yes/no needs no reaction.
            - Match the client: rushed — shorter and quicker; upset — calmer and softer; friendly — warmer. \
            Never sound cheerful about a debt.
            - A side question ("qayerga to'layman?", "qancha qoldi?") — answer it briefly from FACTS, then come \
            back to where you were.
            - Spoken Uzbek, not written: "xo'p", "mayli", "bo'ladi", "-ku", "-da" are natural; long \
            subordinate clauses are not.
            """;

    private static final String RULE_WITH_TRANSFER = "@transfer ";
    private static final String RULE_WITHOUT_TRANSFER = "@no-transfer ";

    /**
     * {@link #PLATFORM_GUARDRAILS} for a call that does or does not have somewhere to hand the
     * caller to: a rule marked {@code @transfer} is kept only when it does, {@code @no-transfer}
     * only when it does not, so no rule tells the model to call a tool it is not offered.
     */
    static List<String> findPlatformGuardrails(boolean transferOffered) {
        List<String> rules = new ArrayList<>();
        for (String rule : PLATFORM_GUARDRAILS) {
            if (rule.startsWith(RULE_WITH_TRANSFER)) {
                if (transferOffered) {
                    rules.add(rule.substring(RULE_WITH_TRANSFER.length()));
                }
            } else if (rule.startsWith(RULE_WITHOUT_TRANSFER)) {
                if (!transferOffered) {
                    rules.add(rule.substring(RULE_WITHOUT_TRANSFER.length()));
                }
            } else {
                rules.add(rule);
            }
        }
        return rules;
    }

    /** What the AI persona does with a question it cannot answer: hand it to a person only if one can be reached. */
    static String unknownAnswerAction(boolean transferOffered) {
        return (transferOffered
                ? " Sizni mutaxassisimizga ulab beraman\" and call requestHumanTransfer.\n"
                : "\" and offer what you can help with instead.\n")
                + "  Only for a question the CLIENT asked: a tool that returned no data is not one — then never "
                + "say this sentence and do not transfer, carry on with the conversation.\n";
    }

    /**
     * A retrieved passage is about a chunk long; the cap guards against a source whose
     * extractor produced one enormous run of text, not a normal trim. Sanitizing matters
     * more than the length: the text comes out of a file an operator uploaded, so anything
     * in it that looks like a system marker is neutralised before it reaches the model.
     */
    /** Characters of one knowledge passage shown to the model; cascade and realtime share it. */
    static final int MAX_PASSAGE_CHARS = 1200;
    private static final int MAX_SOURCE_NAME_CHARS = 80;
    /** A recorded outcome is a date, a code or a short note — enough for the model to see it is in. */
    private static final int MAX_RECORDED_CHARS = 120;

    /**
     * The caller's own zone: what day and hour it is where they are. The server may run
     * anywhere — on a UTC container an evening call was being told the next day's date —
     * and both the date the agent states and the remembered calls it dates are the
     * caller's, not the machine's.
     */
    static final ZoneId CALLER_ZONE = ZoneId.of("Asia/Tashkent");

    /** The agent's own zone (studio "Current Time Awareness"), else the platform's. Validated on save. */
    static ZoneId findCallerZone(AiAgent agent) {
        return agent != null && agent.timezone() != null ? ZoneId.of(agent.timezone()) : CALLER_ZONE;
    }

    /**
     * Cross-call memory: what earlier conversations with this phone established, plus the
     * operator's notes. Newest call first, so the model leans on the latest state. Shared
     * with {@link RealtimeSystemPromptFactory} so both pipelines remember the same way.
     */
    static void appendMemory(StringBuilder sb, ClientMemory memory, ZoneId zone) {
        if (memory == null || memory.isEmpty()) {
            return;
        }
        // The topic of each remembered call is named because the same phone gets more than
        // one kind of call: a debt call's summary handed to a lead follow-up, untagged, read
        // as what this call was about.
        sb.append("\nMULTI-CALL MEMORY (earlier conversations with this client and operator notes — ")
                .append("use them naturally, but never say \"it is written in our database\". ")
                .append("Earlier conversations may have been about a DIFFERENT topic: this call's topic is ")
                .append("what CALL TYPE and CALL PURPOSE say. Do not bring up another topic unless the client asks):\n");
        if (memory.preferredName() != null) {
            sb.append("- Preferred form of address: ").append(memory.preferredName()).append('\n');
        }
        if (memory.preferredLanguage() != null && !memory.preferredLanguage().isBlank()) {
            sb.append("- Preferred language: ").append(memory.preferredLanguage()).append('\n');
        }
        if (memory.operatorNotes() != null) {
            sb.append("- Operator note: ").append(memory.operatorNotes()).append('\n');
        }
        for (RememberedCall call : memory.recentCalls()) {
            sb.append("- Earlier conversation");
            if (call.at() != null) {
                sb.append(" (").append(LocalDate.ofInstant(call.at(), zone)).append(')');
            }
            if (call.scenarioKey() != null && !call.scenarioKey().isBlank()) {
                sb.append(", topic: ").append(call.scenarioKey());
            }
            if (call.disposition() != null) {
                sb.append(", outcome: ").append(call.disposition());
            }
            sb.append(": ").append(call.summary()).append('\n');
        }
        memory.facts().forEach((name, value) -> sb.append("- ")
                .append(FACT_LABELS.getOrDefault(name, name)).append(" (from an earlier conversation): ")
                .append(value).append('\n'));
        LocalDate broken = findBrokenPromise(memory, LocalDate.now(zone));
        if (broken != null) {
            sb.append("- BROKEN PROMISE: on an earlier call the client promised to pay by ").append(broken)
                    .append(", that day has passed and they are being called about the debt again — ")
                    .append("the promise was not kept.\n");
        }
    }

    /**
     * The day an earlier call's payment promise fell due, when it is already behind {@code today};
     * {@code null} when there was no promise, it is still ahead, or the remembered value is not a date.
     */
    static LocalDate findBrokenPromise(ClientMemory memory, LocalDate today) {
        Object promised = memory != null ? memory.facts().get("promisedDate") : null;
        if (promised == null) {
            return null;
        }
        try {
            LocalDate date = promised instanceof LocalDate d ? d : LocalDate.parse(promised.toString().trim());
            return date.isBefore(today) ? date : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Which way the call went and what it is about, said right after the role. Both used
     * to be left for the model to infer: the prompt said "you are calling" on every call,
     * so a client who rang in about a lead was greeted like a debtor being dialled, and a
     * debt call, a reminder and a lead follow-up differed only in the role prompt's wording.
     * Shared with {@link RealtimeSystemPromptFactory} so both pipelines frame a call the same way.
     *
     * @param callPurpose the scenario's {@code callPurpose}, may carry {@code {{fact}}} templates; null = not stated
     * @param goal        the campaign's goal for this call; null = none
     */
    static void appendCallFrame(StringBuilder sb, boolean inbound, String callPurpose, String goal,
                                Map<String, Object> facts) {
        if (inbound) {
            sb.append("CALL TYPE: INBOUND — the client called the company THEMSELVES and you are answering. ")
                    .append("First listen to why they called. Do not bring up a topic they have not raised (debt, ")
                    .append("payment, reminder, etc.). Do not ask \"Men [Ism] aka bilan gaplashayapmanmi?\" — if you ")
                    .append("need their name, ask \"Ismingizni bilsam bo'ladimi?\".\n");
        } else {
            sb.append("CALL TYPE: OUTBOUND — YOU called the client. They do not know why they are being ")
                    .append("called — you tell them.\n");
        }
        if (callPurpose != null && !callPurpose.isBlank()) {
            sb.append("CALL PURPOSE: ")
                    .append(PromptTemplateEngine.render(callPurpose.trim(), facts == null ? Map.of() : facts))
                    .append("\nThe conversation stays on this topic only.\n");
        }
        if (goal != null && !goal.isBlank()) {
            sb.append("CAMPAIGN GOAL: ").append(goal).append('\n');
        }
        sb.append('\n');
    }

    /** Who the agent speaks for — the one company name it may introduce itself with. */
    static void appendCompany(StringBuilder sb, String companyName, boolean inbound) {
        if (companyName == null || companyName.isBlank()) {
            return;
        }
        sb.append("COMPANY: you are ")
                .append(inbound ? "answering the call" : "calling")
                .append(" on behalf of \"").append(companyName.trim())
                .append("\". When you introduce yourself, use only this name — even if the text above mentions another.\n\n");
    }

    /**
     * How a call the client made opens. The outbound opening goes straight to an identity
     * check — "am I speaking with Aziz aka?" — which a caller who has just rung the company
     * hears as the agent not knowing who called whom.
     *
     * @param greeted whether the greeting and the disclosure were already spoken
     */
    static String inboundOpening(boolean greeted) {
        return greeted
                ? "[SYSTEM: The greeting and the disclosure have already been spoken — do not greet again or "
                        + "reintroduce yourself. Listen to why the client called and answer accordingly; if they "
                        + "have not said yet, ask \"Sizga qanday yordam bera olaman?\".]\n"
                : "[SYSTEM: In your first reply, greet briefly, say the company name and ask \"Sizga qanday "
                        + "yordam bera olaman?\". Once the client says why they called, continue with the scenario.]\n";
    }

    /**
     * English labels for the well-known facts. The scenario texts that predate the English
     * prompt name some of them in Uzbek ("Peniya", "Shartnoma bekor bo'lishiga qolgan kun"),
     * so those keep the Uzbek name next to the English one.
     */
    static final Map<String, String> FACT_LABELS = Map.ofEntries(
            Map.entry("clientName", "Name"),
            Map.entry("debtAmount", "Amount"),
            Map.entry("currency", "Currency"),
            Map.entry("dueDate", "Due date"),
            Map.entry("contractNumber", "Contract number"),
            Map.entry("penaltyAmount", "Penalty (Peniya)"),
            Map.entry("contractCancelDays", "Days until the contract is cancelled (Shartnoma bekor bo'lishiga qolgan kun)"),
            Map.entry("brokenPromiseDate", "Broken promise date (the client promised to pay by this day and did not)"),
            // Flats and products from earlier conversations (searchFlats, CatalogTools). A price
            // in them may be stale: the agent searches again before stating one.
            Map.entry("flatSearch", "Flats the client looked for"),
            Map.entry("offeredFlats", "Flats offered to the client (re-check the price with searchFlats before saying it)"),
            Map.entry("interestedFlat", "Flat the client liked or chose"),
            Map.entry("productInterest", "Products the client asked about"),
            Map.entry("productSkus", "Product SKUs the client was shown")
    );

    private final SentimentDetector sentimentDetector;

    public SystemPromptFactory() {
        this(new SentimentDetector());
    }

    public SystemPromptFactory(SentimentDetector sentimentDetector) {
        this.sentimentDetector = sentimentDetector != null ? sentimentDetector : new SentimentDetector();
    }

    public static StageDef stageOf(ScenarioDefinition def, String stageId) {
        if (def == null) {
            return null;
        }
        return def.findStage(stageId);
    }

    /**
     * The agent settings the call's current stage replaces ({@link StageOverrides}), or null
     * when it keeps the agent's. Read at the moment of use — a barge-in, an utterance, a
     * search — so a stage change takes effect on the next one.
     */
    public static StageOverrides overridesOf(DialogSession s) {
        StageDef stage = stageOf(s.scenario(), s.state());
        return stage != null ? stage.overrides() : null;
    }

    /**
     * The unchanging half of the prompt: who the agent is, the facts it may state, and
     * the rules it must not break. Cache this per call ({@link DialogSession#systemPrefix()}) —
     * rebuilding it produces the same string and only risks breaking the cached prefix
     * (e.g. across midnight).
     */
    public String stablePrefix(DialogSession s) {
        ScenarioDefinition def = s.scenario();
        CallContext c = s.context();
        StringBuilder sb = new StringBuilder();
        // What differs from one call to the next (facts, the time, the client's name) goes into
        // `call`, appended last: Gemini caches a request by its leading bytes, so everything above
        // it is then the same on every call of this agent and is served cached from the first turn.
        StringBuilder call = new StringBuilder();
        // Fixed for the whole call, so it does not disturb the cached prefix.
        boolean singleStage = StageEntry.hasNowhereToTransition(def);
        boolean transferOffered = TransferTargets.isOfferedAnywhere(s.agent(), s.isTransferTargetAvailable(), def);

        sb.append(def.rolePrompt()).append(' ')
                .append("You talk with the client over the phone, naturally, in ").append(languageName(s.language()))
                .append(". These instructions are in English for you only — every word the client hears must be in ")
                .append(languageName(s.language())).append(".\n\n");

        if (s.detectLanguages().size() > 1) {
            sb.append("MULTILINGUAL CALL: This agent supports multiple speech languages: ")
                    .append(String.join(", ", s.detectLanguages()))
                    .append(". Start in ").append(languageName(s.language()))
                    .append(". If the client speaks in another supported language, switch naturally and continue in that language.")
                    // A Russian-speaking caller heard "Рахмат, хорошо. Ассалому алайкум, Нигина-опа, яхшимисиз?"
                    // (eval #31): the Uzbek sample lines were transliterated instead of translated.
                    .append(" Once switched, EVERY word is in that language: translate the meaning of the sample lines,"
                            + " never carry their words over (no Uzbek greetings, thanks or forms of address in"
                            + " a Russian reply).\n\n");
        }

        appendCallFrame(call, s.isInbound(), def.callPurpose(), c.goal(), c.facts());
        appendCompany(sb, s.companyName(), s.isInbound());

        if (s.agentPersona() == AgentPersona.HUMAN_LIKE) {
            String personaName = resolveVoicePersonaName(s.ttsVoice(), s.language());
            sb.append("PERSONA AND ROLE (A REAL HUMAN SPECIALIST):\n")
                    .append("- You are a professional, real human employee of ")
                    .append(s.companyName() != null ? s.companyName().trim() : "the company")
                    .append(". Your name: ").append(personaName).append(".\n")
                    .append("- STRICTLY FORBIDDEN: calling yourself a robot, bot, artificial intelligence or automated system!\n")
                    .append("- If the client asks 'Sen robotsanmi?', 'AI bilan gaplashyapmanmi?', 'Tirik odammisan?', ")
                    .append("answer warmly and convincingly: \"Yo'q, men ").append(s.companyName() != null ? s.companyName().trim() : "kompaniya")
                    .append(" mutaxassisi ").append(personaName).append("man, siz bilan jonli gaplashyapman. Sizga qanday yordam bera olaman?\"\n")
                    .append("- Answer the client's questions warmly and briefly, like a person, from what these instructions, the facts and tools give you. ")
                    .append("What none of them gives you, you do not make up (see UNKNOWN ANSWER).\n")
                    .append(HUMAN_SPEECH).append('\n');
        } else {
            sb.append("PERSONA AND ROLE (ARTIFICIAL INTELLIGENCE / AI ASSISTANT):\n")
                    .append("- You are the official AI voice assistant (a robot) of ")
                    .append(s.companyName() != null ? s.companyName().trim() : "the company").append(".\n")
                    .append("- IF YOU CANNOT FIND THE ANSWER: if the client asks something that is not in the scenario or the facts, that you do not know, or that is unclear, never invent information. Say plainly that you are an AI: ")
                    .append("\"Men sun'iy intellekt yordamchisiman, ushbu savolingiz bo'yicha aniq ma'lumotga ega emasman.")
                    .append(unknownAnswerAction(transferOffered))
                    .append("- If the client asks 'Sen robotsanmi?', say openly: \"Ha, men ").append(s.companyName() != null ? s.companyName().trim() : "")
                    .append(" kompaniyasining sun'iy intellekt yordamchisiman\".\n\n");
        }

        ZoneId zone = findCallerZone(s.agent());
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        call.append("TODAY (BUGUNGI SANA): ").append(today).append(" (")
                .append(today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .append("), CURRENT TIME: ").append(now.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
                .append(".\n");
        call.append(dateAnchors(today)).append(TIME_ANCHORS).append('\n');

        List<FactField> factSchema = def.factSchema();
        if (factSchema != null && !factSchema.isEmpty()) {
            call.append("FACTS (FAKTLAR — state only these, never change them):\n");
            for (FactField f : factSchema) {
                call.append("- ").append(FACT_LABELS.getOrDefault(f.name(), f.name())).append(": ")
                        .append(orDash(c.fact(f.name()))).append('\n');
            }
        }
        appendMemory(call, c.memory(), zone);

        Object nameObj = c != null ? c.fact("clientName") : null;
        if ((nameObj == null || nameObj.toString().isBlank()) && c != null && c.memory() != null && c.memory().preferredName() != null && !c.memory().preferredName().isBlank()) {
            nameObj = c.memory().preferredName();
        }
        // The first name only: the samples below are said as written, and "Anvar Jo'rayev aka" is not how anyone talks.
        String clientName = nameObj != null && !nameObj.toString().isBlank()
                ? PersonNames.findFirstName(nameObj.toString().trim()) : null;
        // Name unknown: a person greets and asks it, rather than calling the client "hurmatli mijoz".
        String askNameUz = "Ismingizni bilsam bo'ladimi?";
        String sampleIdentityUz = clientName != null ? clientName + " aka, yaxshimisiz?" : askNameUz;

        if (s.isInbound()) {
            call.append('\n').append(inboundOpening(s.isDisclosureSpoken()));
        } else if (s.isDisclosureSpoken()) {
            // How a name is said and which forms are banned is in CLIENT'S NAME and the speech rules; this note
            // only says what is already done and what the first reply opens with.
            call.append("\n[SYSTEM: The greeting and the \"automated service, the call is being recorded\" ")
                    .append("disclosure have already been spoken — the client has heard them. ")
                    .append("So NOWHERE IN THE CALL greet, reintroduce yourself or the company, or say again that the ")
                    .append("call is recorded — unless the client asks. Your first reply goes straight to the scenario's ")
                    .append("next stage: ")
                    .append(clientName != null ? "\"" + clientName + " aka, yaxshimisiz?\"" : "\"" + askNameUz + "\"")
                    .append(".]\n");
        } else {
            if (s.agentPersona() == AgentPersona.HUMAN_LIKE) {
                String personaName = resolveVoicePersonaName(s.ttsVoice(), s.language());
                call.append("\n[SYSTEM: In your first reply, greet and introduce yourself like a person: \"Salom, mening ismim ")
                        .append(personaName).append(". ")
                        .append(s.companyName() != null ? s.companyName().trim() + " kompaniyasidan qo'ng'iroq qilyapman. " : "")
                        .append(singleStage
                                ? "\" and IMMEDIATELY go on to the purpose of the call: write what you say as plain text. Do not just greet and stop.]\n"
                                : "\" and IMMEDIATELY move to the scenario's next stage (e.g. to confirm identity: \""
                                + sampleIdentityUz + "\", or to the main purpose): "
                                + "write what you say as plain text, then call the transitionTo tool. Do not just greet and stop.]\n");
            } else if (singleStage) {
                call.append("\n[SYSTEM: In your first reply, greet briefly, introduce yourself and the company, ")
                        .append("and IMMEDIATELY go on to the purpose of the call. Do not just greet and stop.]\n");
            } else {
                call.append("\n[SYSTEM: In your first reply, greet briefly, introduce yourself and the company, ")
                        .append("and IMMEDIATELY move to the scenario's next stage (e.g. to confirm identity: \"")
                        .append(sampleIdentityUz).append("\", or to the main purpose), then call transitionTo. ")
                        .append("Do not just greet and stop.]\n");
            }
            // The name rule itself is CLIENT'S NAME (platform guardrails); only the unknown name needs a line here.
            if (clientName == null) {
                boolean russian = s.language() != null && s.language().toLowerCase(Locale.ROOT).startsWith("ru");
                call.append("[SYSTEM: The client's name is unknown — right after the greeting ask it once: \"")
                        .append(russian ? "Как я могу к вам обращаться?" : askNameUz)
                        .append("\", then go on to the purpose.]\n");
            }
        }

        sb.append("\nSTRICT RULES:\n");
        List<String> scenarioGuardrails = def.guardrails();
        if (scenarioGuardrails != null) {
            for (String rule : scenarioGuardrails) {
                sb.append("- ").append(rule).append('\n');
            }
        }
        for (String rule : findPlatformGuardrails(transferOffered)) {
            if (s.agentPersona() != AgentPersona.HUMAN_LIKE && rule.startsWith("NATURAL FILLERS AND BRIDGES")) {
                continue;
            }
            if (rule.startsWith("LANGUAGE RESTRICTION")) {
                String restriction = languageRestriction(s.language(), s.detectLanguages());
                if (restriction != null) {
                    sb.append("- ").append(restriction).append('\n');
                    continue;
                }
            }
            sb.append("- ").append(rule).append('\n');
        }
        HandbookPrompt.append(sb, s.agent());
        sb.append('\n');
        appendGlobalNodes(sb, def);

        if (singleStage) {
            sb.append("SCENARIO: follow the instructions above for the whole call. ")
                    .append("Do not end the call without a reason.\n\n");
        } else {
            sb.append("SCENARIO: go stage by stage. Once a stage's goal is met, move to the next allowed stage ")
                    .append("with transitionTo and say what that stage needs. ")
                    .append("Do not end the call without a reason. ")
                    .append("Each stage's text is an overall GOAL, not an order for every reply: once you have met ")
                    .append("the goal, do not repeat it — move to the next stage.\n\n");
        }

        // Numbered, one line each, and last of the shared part. The same rules as prose in one
        // paragraph were being dropped: a real call opened with "Ushbu to'lov qachon amalga
        // oshirilishi kutilmoqda?", two banned words in a sentence whose form was banned too.
        sb.append("SPEECH RULES — check every reply against this list:\n");
        int n = 1;
        for (String rule : speechRules(isRussian(s.language()), warmthOf(s.agent()), emotionStyleLine(s.agent()))) {
            sb.append(n++).append(". ").append(rule).append('\n');
        }
        sb.append('\n').append(examples(def, s.language())).append('\n');

        sb.append("THIS CALL:\n").append(call);
        return sb.toString();
    }

    /**
     * The relative dates a caller actually names, worked out in advance.
     *
     * <p>The scenario asks the model to turn "keyingi oyning 20-sanasi" into a
     * {@code yyyy-MM-dd} tool argument, and calendar arithmetic is what it then spends its
     * thinking budget on. On a real call the turn that recorded the payment promise — the
     * one turn the whole call exists for — took 5.2s to its first token, against 1.1s for
     * the turns either side of it. Handing it the answers makes that a lookup.
     *
     * <p>Part of the stable prefix, so it is written once per call and cached with the
     * rest of it (the prefix is already rebuilt per call, which is what keeps it right
     * across midnight).
     */
    private static String dateAnchors(LocalDate today) {
        LocalDate nextMonth = today.plusMonths(1);
        LocalDate nextMonthEnd = nextMonth.withDayOfMonth(nextMonth.lengthOfMonth());
        return "DATES (precomputed — do not calculate them yourself):\n"
                + "- \"ertaga\" (tomorrow): " + today.plusDays(1) + "\n"
                + "- \"indinga\" (the day after tomorrow): " + today.plusDays(2) + "\n"
                + "- \"kelasi hafta shu kun\" (same day next week): " + today.plusWeeks(1) + "\n"
                + "- \"shu oyning oxiri\" (end of this month): " + today.withDayOfMonth(today.lengthOfMonth()) + "\n"
                + "- \"keyingi oy\" (next month): " + nextMonth.withDayOfMonth(1) + " to " + nextMonthEnd + ". "
                + "So \"keyingi oyning N-sanasi\" = " + nextMonth.withDayOfMonth(1).toString().substring(0, 8)
                + "N (e.g. the 20th = " + nextMonth.withDayOfMonth(Math.min(20, nextMonth.lengthOfMonth())) + "). "
                + "The client names N — never pick it yourself.\n"
                + weekdayAnchors(today);
    }

    private static final String[] UZBEK_WEEKDAYS =
            {"dushanba", "seshanba", "chorshanba", "payshanba", "juma", "shanba", "yakshanba"};

    /**
     * The coming seven weekdays with their dates, and what to do when the client names today's.
     * "Juma kuni to'layman" said on a Friday was booked as that same day and confirmed as "jumaga" with
     * no date (eval #23), while another model on the same words took the next Friday — it is ambiguous,
     * so the client is asked rather than either being guessed.
     */
    static String weekdayAnchors(LocalDate today) {
        StringBuilder anchors = new StringBuilder("- weekdays: ");
        for (int day = 1; day <= 7; day++) {
            LocalDate date = today.plusDays(day);
            anchors.append('"').append(UZBEK_WEEKDAYS[date.getDayOfWeek().getValue() - 1]).append("\" = ")
                    .append(date).append(day < 7 ? ", " : "");
        }
        String todayName = UZBEK_WEEKDAYS[today.getDayOfWeek().getValue() - 1];
        return anchors.append(".\n- If the client names TODAY's weekday (\"").append(todayName)
                .append("\"), it may mean today (").append(today).append(") or next week (")
                .append(today.plusWeeks(1)).append("): ask once which one, in one short question "
                        + "(\"Bugunmi yoki kelasi ").append(todayName).append(", ")
                .append(today.plusWeeks(1).getDayOfMonth()).append("-sanami?\"), never pick it yourself. "
                        + "When you confirm a day, say its date too, not the weekday alone.\n")
                .toString();
    }

    /**
     * The parts of the day a caller books by (UNIVERSAL-OPERATOR-PLAN B.7). Nobody asks for
     * 14:00; they ask for "ertaga tushdan keyin", and a booking tool takes hours. Left to
     * itself the model picks an hour of its own each time, so the same request lands on a
     * different slot on two calls — and, without the hour it is now, it offers a slot that
     * is already past.
     *
     * <p>Fixed text, unlike {@link #dateAnchors}: the boundaries are the same every day, so
     * only the "CURRENT TIME" line above it moves. Shared with {@link
     * RealtimeSystemPromptFactory} so both pipelines mean the same hours by the same word.
     * The text lives in {@code resources/prompts/time-anchors.txt}.
     */
    static final String TIME_ANCHORS = String.join("\n", PromptText.lines("time-anchors.txt")) + "\n";

    /** The agent's {@link SpeakingWarmth}; a call without an agent (a test call) speaks {@code BALANCED}. */
    static SpeakingWarmth warmthOf(AiAgent agent) {
        return agent != null ? agent.voice().warmth() : SpeakingWarmth.BALANCED;
    }

    /**
     * How big a reaction to what the caller said may be. RESERVED is the original rule: it
     * was written against staged sympathy, and overshot into a bot that only ever said
     * "Tushunarli" — on the Uzbek Yandex voices, which have no emotional roles, the
     * wording is all the warmth a call has.
     */
    private static String reactionSize(SpeakingWarmth warmth, boolean russian) {
        return switch (warmth) {
            case RESERVED -> "Keep the reaction to ONE WORD — do not add a separate sentence expressing emotion.";
            case BALANCED -> "The reaction is one word or a short phrase tied to what the client said "
                    + (russian ? "(\"Да, без работы непросто\", \"Хорошо, тогда так и сделаем\")"
                            : "(\"Ha, ish bo'lmasa qiyin\", \"Yaxshi, unda shunday qilamiz\")") + ".";
            case WARM -> "The reaction is one short, sincere sentence tied to what the client said "
                    + (russian ? "(\"Да, сейчас многим непросто, понимаю\")"
                            : "(\"Ha, hozir ko'pchilikka oson emas, tushunaman\")") + ".";
        };
    }

    /** How a worry or a complaint is acknowledged before going back to the task. */
    private static String acknowledgementSize(SpeakingWarmth warmth, boolean russian) {
        return switch (warmth) {
            case RESERVED -> "acknowledge it in two words at most " + (russian ? "(\"Понимаю.\")" : "(\"Tushunaman.\")");
            case BALANCED -> "acknowledge it in one short sentence, using the client's own words "
                    + (russian ? "(\"Понимаю, сроки поджимают.\")" : "(\"Tushunaman, muddat yaqin qolgan.\")");
            case WARM -> "acknowledge it in one or two short, warm sentences "
                    + (russian ? "(\"Понимаю, ситуация неприятная. Давайте вместе решим.\")"
                            : "(\"Tushunaman, vaziyat yoqimsiz. Keling, birga hal qilamiz.\")");
        };
    }

    /**
     * The spoken register a BALANCED or WARM agent talks in. Shared with {@link
     * RealtimeSystemPromptFactory}; null for RESERVED, which keeps the original style.
     */
    static String warmthStyleLine(SpeakingWarmth warmth, boolean russian) {
        if (warmth == SpeakingWarmth.RESERVED) {
            return null;
        }
        return "Speak in a lively spoken register, like a person: use words like "
                + (russian ? "\"ну\", \"хорошо, тогда\", \"-то\", \"давайте\"" : "\"mayli\", \"xo'sh\", \"unda\", \"-da\", \"-ku\"")
                + " where they fit naturally, and vary the length of your sentences. "
                + (warmth == SpeakingWarmth.WARM
                        ? "Keep your tone warm and sincere, but stay on task."
                        : "Keep your tone calm and friendly — no excess excitement or praise.");
    }

    /**
     * The agent's own mood ({@code AiAgentVoice#emotion}) as wording, so the words carry the mood the
     * voice is given — on the Uzbek Yandex voices, which have no roles, the words are all of it.
     * Shared with {@link RealtimeSystemPromptFactory}; null for NEUTRAL.
     */
    static String emotionStyleLine(AiAgent agent) {
        VoiceEmotion emotion = agent != null ? agent.voice().emotion() : VoiceEmotion.NEUTRAL;
        return switch (emotion) {
            case NEUTRAL -> null;
            case CHEERFUL -> "Your mood is CHEERFUL: upbeat, positive words, a smile you can hear — but never joke "
                    + "about the client's problem, and stay brief.";
            case FRIENDLY -> "Your mood is FRIENDLY: warm and caring, like a helpful acquaintance — gentle words, "
                    + "never pushy.";
            case STRICT -> "Your mood is SERIOUS: calm, firm and to the point — polite, never cold or threatening.";
            case WHISPER -> "Your mood is SOFT: quiet, gentle, unhurried wording.";
            case SAD -> "Your mood is SYMPATHETIC: soft, understanding wording — no cheerfulness.";
        };
    }

    /**
     * The style rules, one line each. Numbered by the caller, so the uz-only entries can
     * drop out of a Russian call without leaving a hole in the list.
     */
    private static List<String> speechRules(boolean russian, SpeakingWarmth warmth, String emotionLine) {
        // Only what platform-guardrails.txt does not already say: the two lists are read together, and a
        // rule stated twice in different words was being read as two rules that disagree (name "once" vs
        // "now and then", "do not echo" vs "Uch xonali, yaxshi", numbers in digits vs in words).
        List<String> rules = new ArrayList<>();
        rules.add("Speak only natural spoken " + (russian ? "Russian" : "Uzbek") + ": never English words, "
                + "codes, variable names or placeholders (thought, undefined, dynamic_...).");
        rules.add("Keep it short: at most two short sentences and one question. Answer a short question briefly. "
                + "Put a number, an amount and a date in separate sentences, not one long one. Each reply "
                + "continues the conversation — it never starts it over.");
        rules.add("Everything you write is spoken aloud: plain sentences only — no markdown (*, _, #), lists, "
                + "numbering, headings, brackets, emoji or special characters.");
        // A reaction only acknowledges NEW information; waiting lines belong to the system. "Tushunarli" is
        // not offered: offered as a permitted opener, the model began reply after reply with it.
        rules.add("Most replies start straight with the substance. Only after NEW information (an answer, a reason, "
                + "an objection, a date) "
                + "may you sometimes open your reply with a brief reaction "
                // No "Aha": the synthesized voice draws it out into a long "Aaa…".
                + (russian ? "(\"Хорошо\", \"Понятно\", \"Да, конечно\")" : "(\"Xo'p\", \"Yaxshi\", \"Mayli\")")
                + ", never the same word twice in a row; after a plain yes, none. If the client says they already told you "
                + (russian ? "(\"я же сказал\")" : "(\"aytdimku\")")
                + " — say " + (russian ? "\"Извините\"" : "\"Kechirasiz\"") + " and go on with what they said; "
                + "never ask it again. Never open with a waiting phrase "
                + (russian ? "(\"Секунду\", \"Один момент\", \"Сейчас проверю\")" : "(\"Bir lahza\", \"Bir soniya\", \"Hozir tekshiraman\")")
                + " — the system says those itself. "
                + reactionSize(warmth, russian) + " Staged sympathy is forbidden "
                + (russian ? "(\"я вас прекрасно понимаю\", \"мне очень жаль\")" : "(\"vaziyatni to'g'ri tushunaman\", \"sizni juda yaxshi tushunib turibman\", \"juda afsusdaman\")")
                + ": it sounds artificial.");
        // The old "Exception: the agreed outcome is read back once at the end" licensed the second
        // confirmation of every promise in evals #30–#32: the date was confirmed when agreed, then again.
        rules.add("Do not restate a fact you have already said (amount, date) and do not re-ask a question the client "
                + "has answered. The agreed outcome is confirmed ONCE, in the reply that agrees it — never again.");
        // OpenAI's realtime prompting guide: the model copies sample lines closely, so variety needs its own rule.
        rules.add("VARIETY: the sample lines in these instructions show the meaning, not the words. Never say the "
                + "same sentence twice in a call; reword instead.");
        rules.add("If the client has only just started and paused to think (a thinking sound, or a sentence that is "
                + "clearly to be continued) — call waitForCaller and write nothing.");
        // A garbled answer used to cost the whole question: the model said "Gapingizni to'liq tushunolmadim"
        // and asked the next stage's question, so the reason it had just asked for was never given.
        // Re-asking in simpler words is NEVER INVENT's (platform guardrails); this keeps only what it does not say.
        rules.add("An unintelligible answer: " + (russian ? "\"Плохо слышно.\"" : "\"Ovoz yaxshi kelmadi.\"")
                + " and the same question once more; after a second failed attempt, go on with the scenario. "
                + "A few garbled words whose meaning is clear (a reason, a date, yes or no) ARE an answer — act on it. "
                + "Dialect and mixed Uzbek-Russian are normal; never correct them.");
        if (russian) {
            rules.add("Banned formal written words: \"данный\", \"осуществлять\", \"уважаемый клиент\", "
                    + "\"собеседник\". Choose the lively spoken form instead.");
        } else {
            // One rule, written form → spoken form: a banned list and a separate list of replacements said
            // everything twice.
            rules.add("Banned bookish words → their spoken form: ushbu/mazkur → shu/bu; amalga oshirasiz → qilasiz, "
                    + "to'laysiz; mablag' → pul, to'lov; ma'lumot beraman/taqdim etaman → aytaman; ko'maklashaman → "
                    + "yordam beraman; qondirildi → hal bo'ldi; zahmat chekmang → xavotir olmang; aniqlik kiritib olsak "
                    + "→ bilib olaylik; also never \"tafsilotlar\", \"bag'oyat mamnunman\", \"iltifot qiling\".");
            // When and how often the name is said is CLIENT'S NAME (platform guardrails).
            rules.add("\"aka\"/\"opa\" goes after the first name only, never the surname. Never \"Siz [Ism]misiz?\": "
                    + "the name in the greeting confirms identity unless they object. Name unknown — ask it once "
                    + "(\"Ismingizni bilsam bo'ladimi?\").");
            rules.add("Write in standard literary Uzbek; always write o' and g' with an apostrophe "
                    + "(so'm, to'lov, bo'yicha). A misspelt or clipped word sounds broken in the voice too.");
        }
        rules.add("State the FACTS firmly — they are verified. No hedging words "
                + (russian ? "(\"кажется\", \"вроде\", \"похоже\")" : "(\"shekilli\", \"bo'lsa kerak\", \"chiqibdi\")")
                + " and do not ask the client to confirm a fact: you are informing them of it.");
        // FactGuard checks every amount in a reply against FACTS by its digits, and SpeechTextNormalizer
        // reads digits out as words — an amount written in words passes neither.
        rules.add("Write amounts and dates in digits exactly as in FACTS (\"1500000 so'm\", \"15-dekabr\") — the voice "
                + "reads them out as words. Never write them in words, never abbreviate (\"1,5 mln\") and never add symbols.");
        rules.add("For a date in the current year, do not say the year — \"3-sentabr\" is enough. If the client said "
                + "\"ertaga\" or \"dushanba\", keep that form when confirming (\"ertaga, 3-sentabrda\"); the full "
                + "yyyy-MM-dd date goes only into tool parameters.");
        // "Aniq sana" had to be spelled out: a caller said "keyingi oy", the model recorded 2026-10-05 — a day
        // nobody named — and closed the call. A promise the caller never made is worse than one more question.
        rules.add("AN EXACT DATE = a day is named ("
                + (russian ? "\"20 октября\", \"завтра\", \"5-го\"" : "\"20-oktabr\", \"ertaga\", \"oyning 5-si\"")
                + "). Only a month or a vague time ("
                + (russian ? "\"в следующем месяце\", \"в конце месяца\"" : "\"keyingi oy\", \"oy oxirida\"")
                + ") is NOT: never pick the day yourself and never put it into a tool — ask once which day.");
        rules.add("If the client voices worry or displeasure — " + acknowledgementSize(warmth, russian)
                + " and get straight back to the task. A long apology only lengthens the call.");
        if (warmth != SpeakingWarmth.RESERVED) {
            rules.add(warmthStyleLine(warmth, russian));
        }
        if (emotionLine != null) {
            rules.add(emotionLine);
        }
        rules.add("You introduce yourself and the company once — never again later in the call.");
        rules.add("Say goodbye (\"xayr\", \"salomat bo'ling\") ONLY together with the endCall tool, and only once. "
                + "If the client says \"rahmat\" after the outcome is recorded — say goodbye with endCall, nothing else.");
        return rules;
    }

    /**
     * Contrast pairs, because a banned phrase is easier to avoid next to the sentence that
     * should have replaced it. The platform's own pair is the identity check every scenario
     * makes; the rest are the scenario's ({@link #appendStyleExamples}), because a pair only
     * teaches in the words of its own domain — the debt call's pairs used to sit here and
     * went into every scenario's calls.
     */
    private static String examples(ScenarioDefinition scenario, String language) {
        StringBuilder sb = new StringBuilder("EXAMPLES (✗ wrong → ✓ right):\n");
        sb.append(isRussian(language)
                ? "✗ \"Вы Мурод?\" → ✓ \"Я говорю с Мурод-ака?\"\n"
                : "✗ \"Siz Murodjonmisiz?\" → ✓ \"Assalomu alaykum, Murodjon aka, yaxshimisiz?\"\n");
        // How the conversation itself goes, the same in every domain: answering a repair
        // request, a caller asking who is calling, and a caller still mid-thought.
        sb.append(isRussian(language)
                ? "✗ (client: \"А? Что вы сказали?\") repeating the whole introduction from the start → "
                        + "✓ \"Я спросил, удобно ли вам сейчас говорить?\"\n"
                        + "✗ (client: \"А кто это?\") \"Извините, ошиблись номером.\" → "
                        + "✓ \"Я звоню из компании. Я говорю с Мурод-ака?\"\n"
                        + "✗ (client: \"Ну я, это, хотел...\") \"Понятно, спасибо!\" → "
                        + "✓ say nothing — call waitForCaller and let them finish\n"
                : "✗ (client: \"A? Nima dedingiz?\") repeating the whole introduction from the start → "
                        + "✓ \"Hozir gaplashishga qulaymi, deb so'radim.\"\n"
                        + "✗ (client: \"Kim bu?\") \"Uzr, adashibmiz.\" → "
                        + "✓ \"Aka, kompaniyadan telefon qilyapman. Murodjon aka o'zlarimi?\"\n"
                        + "✗ (client: \"Men... endi, haligi...\") \"Tushunarli, rahmat!\" → "
                        + "✓ say nothing — call waitForCaller and let them finish\n"
                        + "✗ (client: \"Ha, to'g'ri.\") \"Tushunarli, tasdiqlaganingiz uchun rahmat. Ushbu to'lovni qachon "
                        + "amalga oshirasiz?\" → ✓ \"Qachonga qilasiz to'lovni?\"\n"
                        + "✗ (client: \"Hozir gaplasholmayman, mashinadaman.\") \"Tushundim, noqulaylik tug'dirganimdan "
                        + "afsusdaman.\" → ✓ \"Mayli, xalaqit bermay. Qachon qaytib telefon qilay?\"\n");
        appendStyleExamples(sb, scenario, language);
        return sb.toString();
    }

    /**
     * The scenario's questions answered at any stage ({@link GlobalNode}). In the stable
     * prefix, not the turn annex: they hold for the whole call, and the prefix is what is
     * cached. Shared with {@link RealtimeSystemPromptFactory}.
     */
    static void appendGlobalNodes(StringBuilder sb, ScenarioDefinition scenario) {
        if (scenario == null || scenario.globalNodes() == null || scenario.globalNodes().isEmpty()) {
            return;
        }
        sb.append("QUESTIONS ANSWERED AT ANY STAGE — if the client asks something like this, ")
                .append("answer briefly and continue the current stage WITHOUT calling transitionTo:\n");
        for (GlobalNode node : scenario.globalNodes()) {
            sb.append("- \"").append(node.question()).append("\" → ").append(node.answer()).append('\n');
        }
        sb.append('\n');
    }

    /**
     * The scenario's own ✗ → ✓ pairs for the language this call is held in
     * (UNIVERSAL-OPERATOR-PLAN A.2). Shared with {@link RealtimeSystemPromptFactory}, so a
     * scenario's examples reach the model whichever pipeline runs the call.
     */
    static void appendStyleExamples(StringBuilder sb, ScenarioDefinition scenario, String language) {
        if (scenario == null || scenario.styleExamples() == null) {
            return;
        }
        for (StyleExample example : scenario.styleExamples()) {
            if (example.appliesTo(language)) {
                sb.append("✗ ").append(example.wrong()).append(" → ✓ ").append(example.right()).append('\n');
            }
        }
    }

    /**
     * The current stage's own ✗ → ✓ pairs ({@code StageDef.examples}) for the call's language.
     * In the turn annex, not the cached prefix with the scenario's pairs: they change with the stage.
     */
    private static void appendStageExamples(StringBuilder sb, StageDef stage, String language) {
        if (stage == null || stage.examples() == null) {
            return;
        }
        StringBuilder pairs = new StringBuilder();
        for (StyleExample example : stage.examples()) {
            if (example.appliesTo(language)) {
                pairs.append("✗ ").append(example.wrong()).append(" → ✓ ").append(example.right()).append('\n');
            }
        }
        if (!pairs.isEmpty()) {
            sb.append("HOW TO PHRASE IT IN THIS STAGE:\n").append(pairs);
        }
    }

    /**
     * The dynamic tail of the prompt: moves with the FSM, and is appended to the message
     * sequence as a system aside rather than baked into {@link #stablePrefix}.
     */
    public String turnAnnex(DialogSession s) {
        StageDef stage = stageOf(s.scenario(), s.state());
        StringBuilder sb = new StringBuilder();
        sb.append("[SYSTEM: CURRENT STAGE: ").append(s.state()).append(" — ")
                .append(stage != null ? stage.purpose() : "").append('\n');
        // Fixed for the whole call, like the stable prefix: a single-prompt agent has no stages to list.
        boolean singleStage = StageEntry.hasNowhereToTransition(s.scenario());
        if (!singleStage) {
            sb.append("Allowed next stages: ").append(allowedNext(s.scenario(), stage)).append('\n');
        }
        appendStaticLines(sb, s.scenario(), stage, s.context() != null ? s.context().facts() : Map.of());
        appendRecorded(sb, s.outcome());
        appendClientFacts(sb, s.clientFacts());
        appendStageExamples(sb, stage, s.language());
        if (stage != null && Boolean.TRUE.equals(stage.skipWhenKnown())) {
            sb.append("ALREADY KNOWN IS NOT ASKED: before asking anything this stage collects, check the client's ")
                    .append("memory, the FACTS and ALREADY ESTABLISHED. Whatever is already there — do not ask for it ")
                    .append("again; confirm it in one short sentence and move on to the next allowed stage.\n");
        }
        // The FSM refuses an edge that is not in `allowedNext`, and a model that spoke a
        // later stage's content first then tried to jump there got the refusal mid-turn
        // and started reasoning aloud about which tool to call instead — the caller heard
        // it. What bounds that is the "one of the allowed next stages, never further"
        // half of the rule below, and that half is kept.
        //
        // The other half used to say "transition now, say that stage's line next turn",
        // which contradicts both the scenario rule in the prefix ("move, and say what
        // that stage needs") and the text-before-tool latency rule right underneath. On a
        // recorded call the model resolved the contradiction by speaking each stage's
        // line one turn before transitioning into it, so `s.state()` — and therefore this
        // whole annex — described a stage whose content the caller had already heard:
        // REASON_INQUIRY's annex arrived on the turn the agent was asking for the date,
        // and the reason it was supposed to collect was never collected. Pairing the text
        // and the transitionTo on the same stage in the same turn is what removes the lag.
        if (singleStage) {
            sb.append("In this reply: just write your reply to the client.\n");
        } else if (s.isDisclosureSpoken() && "GREETING".equalsIgnoreCase(s.state())) {
            sb.append("NOTE: the greeting and the system disclosure have already been spoken to the client. ")
                    .append("Do NOT REPEAT the GREETING line! Without greeting again, move straight to ONE of the ")
                    .append("allowed stages above: first write that stage's line, ")
                    .append("then call transitionTo for exactly that stage.\n");
        } else {
            sb.append("In this reply: if you are moving to the next stage, move to ONE of the allowed ")
                    .append("stages above: first write that stage's line, then call transitionTo ")
                    .append("for exactly that stage. ")
                    .append("If you are staying in the current stage (e.g. the client asked a question, did not hear, or asked for an explanation) — ")
                    .append("do not call transitionTo, just write your reply to the client.\n");
        }
        // Dropped for a single-prompt agent: its line may be an inbound support or lead line,
        // where a closing statement is a fine end of a reply; the debt example does not fit it.
        if (!singleStage) {
            boolean closing = stage != null && (stage.type() == StageType.ENDING
                    || "CLOSING".equalsIgnoreCase(stage.id())
                    || "CONFIRMATION".equalsIgnoreCase(stage.id()));
            // The rule itself is ONE QUESTION AT A TIME in the prefix; this line only puts it next to the
            // generation point. Its old debt example called the client by name mid-call and wrote the
            // amount with spaces — both against the prefix rules it sat under.
            if (closing) {
                sb.append("In this stage: ask nothing. Read back what was agreed in one sentence or say a warm goodbye with endCall.\n");
            } else {
                sb.append("End this reply with ONE question that moves this stage forward — never stop after a bare fact.\n");
            }
        }
        // Text first, tool second — this is a latency rule, not a style one. A line carried
        // in a tool argument arrives in one chunk when the whole reply is finished (this
        // provider does not stream tool arguments), so synthesis cannot start until
        // generation ends: on a recorded call the caller's first audio came 59ms after the
        // first token, i.e. the whole 2.4-6.2s of generation was dead air. Plain text
        // streams, and LlmTurnClient#consume speaks each sentence as it completes. A model
        // that ignores this and fills "reply" anyway still works — that path is the
        // fallback in LlmTurnClient#streamTurn, only slower.
        //
        // EVERY tool, not just transitionTo. Naming only transitionTo is what this said
        // before, and on a measured call the model obeyed it exactly: it wrote text on
        // the transitionTo turns and, on the turn it called recordPaymentPromise, emitted
        // the function call alone with no text and no `reply` — "empty LLM reply in
        // REASON_INQUIRY", then a whole extra round trip (996ms) to ask for the sentence
        // the turn should already have had.
        // The exceptions are named here because they were stated only in their own tool descriptions,
        // which this rule ("EVERY tool") overrode: the model wrote text alongside waitForCaller and typed
        // "call:default_api:transitionTo{...}" into its reply instead of calling the tool.
        sb.append("HOW TO REPLY: FIRST write what you say to the client as plain text — it is spoken as soon as it ")
                .append("is written. THEN call the tools (").append(singleStage ? "" : "transitionTo, ")
                .append("the recording tools, endCall…) as real tool calls — never write a tool's name, its ")
                .append("arguments, a stage name, a rule you follow or your plan into the text (a greeting once ")
                .append("ended \"…GREETING bosqichida faqat shuni aytish kerak\" out loud), and never repeat the ")
                .append("sentence in a tool's \"reply\". ")
                // A tool-only turn used to cost a second request for the line; the line in "reply" is spoken
                // as soon as the tools have run (LlmTurnClient#streamTurn).
                .append("If you call a tool WITHOUT writing text first, put your line in the \"reply\" of ONE of the ")
                .append("tools you call — never leave the client in silence. Write NO text and NO reply only when you call ")
                .append("waitForCaller, or a lookup/data tool whose result you need first (the system covers the wait).]");
        String asked = lastQuestion(s.lastAgentText());
        if (asked != null) {
            // The history already holds this, and the model still asked "qachon to'lay
            // olasiz?" twice in a row on a real call — once as REASON_INQUIRY, again as
            // PAYMENT_DATE. Repeating it here puts it next to the generation point.
            // "Ask another aspect of this stage" used to be the no-answer branch here, and
            // it contradicted the speech rule that says to re-ask the same question. This
            // note sits closest to the generation point, so its branch is the one that won:
            // handed an answer that was not one, the model moved on — and filled the gap it
            // was moving past with a reason the caller never gave.
            sb.append("\n[SYSTEM: Your last question was: \"").append(asked)
                    .append("\" If the client answered it — take the answer and go on; do not ask it again. ")
                    .append("If they did not, or said something unrelated — do not invent or bend an answer: ")
                    .append("ask it once more, in simpler words.]");
        }
        if (s.isInterrupted()) {
            // A barge-in means the caller was talking over the question, so what came back
            // is very often not an answer to it. On the call that prompted this the agent
            // was cut off mid-question, got "aha o'zgartir", and treated it as the reason
            // it had asked for.
            sb.append("\n[SYSTEM: The client interrupted you while you were speaking. You had said up to here: \"")
                    .append(s.lastAgentText() == null ? "" : s.lastAgentText())
                    .append("\". Adapt to what the client said; do not start the whole sentence over. ")
                    .append("The client may not have heard your question in full — do not treat what they ")
                    .append("said as an answer to it.]");
        }
        if (s.isCallerWentQuiet()) {
            // The previous turn chose waitForCaller and nothing followed: the caller is waiting
            // for the bot now, and choosing to wait again would leave them in the same silence.
            sb.append("\n[SYSTEM: You waited for the client to finish, but they have been silent since. ")
                    .append("They said all they meant to — reply to what they said now. ")
                    .append("Do not call waitForCaller again.]");
        }
        appendKnowledge(sb, s);
        String catalog = s.catalogResult();
        if (catalog != null) {
            // Held here rather than left in a tool result: a cascade turn does not see its own
            // tool results afterwards, and the order the caller is placing needs these SKUs
            // (UNIVERSAL-OPERATOR-PLAN B.4). Replaced by the next search.
            sb.append("\n[SYSTEM: Result of the last catalog search — pass the order tool only one of ")
                    .append("these SKUs:\n").append(catalog).append(']');
        }
        if (s.isLowConfidenceInput()) {
            // The recognizer itself said it was unsure. Acting on a misheard date or sum is
            // how a promise gets recorded for a day the caller never named — a person in
            // this position repeats it back before writing anything down.
            sb.append("\n[SYSTEM: The client's last sentence was heard unclearly through noise. ")
                    .append("Do not act on it: briefly repeat back what you heard and have them confirm it — ")
                    .append("the one case where NO ECHOING does not apply ")
                    .append("(e.g. \"To'g'ri eshitdimmi — ...?\"). Until it is confirmed, do not write a date or ")
                    .append("an amount into a tool.]");
            if (s.agent() != null && s.agent().callBehaviour().dtmfInputEnabled()) {
                // A number said into noise is misheard again on the second try just as
                // easily; typed, it arrives exactly (DtmfInputRouter).
                String terminationKey = s.agent().callBehaviour().dtmfTerminationKey();
                String endKey = terminationKey == null ? "the hash (#) key" : "the " + terminationKey + " key";
                sb.append("\n[SYSTEM: If it is a number (card, passport, PINFL, phone, date) — ")
                        .append("ask the client to type it on the phone keypad and press ").append(endKey)
                        .append(" at the end.]");
            }
        }
        if (s.lastCustomerSentiment() != null && s.lastCustomerSentiment() != CustomerSentiment.NEUTRAL) {
            String directive = sentimentDetector.empathyDirective(s.lastCustomerSentiment(), s.language(), warmthOf(s.agent()));
            if (directive != null && !directive.isBlank()) {
                sb.append(directive);
            }
        }
        if (s.disposition() != null) {
            // The outcome tools (recordPaymentPromise, recordRefusalReason,
            // scheduleCallback) record a result without ending the call, and on a real
            // call the model then kept the conversation open with nothing left to ask:
            // it recorded the promise, said "Kelishdik, 2-oktabr deb belgilab qo'ydim",
            // and sat there until the caller hung up on it.
            sb.append("\n[SYSTEM: The call outcome has already been recorded (")
                    .append(s.disposition())
                    .append("). Ask nothing else and do not reopen what was agreed: ")
                    .append("confirm the agreement in one short sentence and IMMEDIATELY ")
                    .append("say goodbye with the endCall tool.]");
        }
        int stageAttempts = s.stageAttempts(s.state());
        // One-stage conversations count every turn in that stage, so the count says nothing there.
        if (stageAttempts >= 2 && !singleStage) {
            // Agrees with the speech rule "after a second failed attempt, go on" and with requestHumanTransfer's
            // own description (only when the client asks for a person) — it used to send a confused client to one.
            sb.append("\n[SYSTEM WARNING: you have already spent ").append(stageAttempts).append(" replies in stage '")
                    .append(s.state())
                    .append("'. Do not ask the same question again: acknowledge what the client said in a few words ")
                    .append("and move on with transitionTo to the allowed stage that fits it")
                    .append(TransferTargets.isOffered(s.agent(), s.isTransferTargetAvailable(), s.scenario(), s.state())
                            ? " (or requestHumanTransfer if they asked for a person).]"
                            : ".]");
        }
        String whisper = s.operatorWhisper();
        if (whisper != null) {
            // A supervisor listening in (LiveCallControlHub): the guidance steers this turn
            // and the ones after it until a newer whisper replaces it.
            sb.append("\n[SYSTEM: OPERATOR INSTRUCTION — the client does not hear this; do not read it out ")
                    .append("word for word, just act on it: ").append(whisper).append(']');
        }
        return sb.toString();
    }

    /**
     * The company's own documents, for the one turn they bear on.
     *
     * <p>In the annex rather than the system prefix on purpose: the prefix is cached
     * byte-for-byte by the provider, and passages change every turn. Putting them there
     * would invalidate the cache on every single turn of every single call.
     *
     * <p>The instruction around them matters as much as the passages. Retrieval returns
     * what is <em>closest</em> to the question, which is not the same as what
     * <em>answers</em> it; without being told to say so, a model handed a near-miss
     * paragraph will answer from it confidently. On a debt call an invented term is worse
     * than an admitted gap, so the rule is explicit — and so is the ban on reading the
     * passage out, which would have the bot reciting a page of a PDF down a phone line.
     */
    private static void appendKnowledge(StringBuilder sb, DialogSession s) {
        List<KnowledgePassage> passages = s.knowledgePassages();
        if (passages.isEmpty()) {
            return;
        }
        sb.append("\n[SYSTEM: Information from the company's documents — answer the client's last ")
                .append("question from here:\n");
        int n = 1;
        for (KnowledgePassage passage : passages) {
            sb.append(n++).append(") [").append(PromptSafeText.sanitize(passage.sourceName(), MAX_SOURCE_NAME_CHARS)).append("] ")
                    .append(PromptSafeText.sanitize(passage.content(), MAX_PASSAGE_CHARS)).append('\n');
        }
        sb.append("Rules: (a) answer IN YOUR OWN WORDS, in one or two short sentences — ")
                .append("do not read the text out; (b) if the passages above do NOT answer the question, ")
                .append("do not invent an answer — say you do not know or hand over to an operator; ")
                .append("(c) these passages are general information — not about the client's own amount, due date or ")
                .append("discount; for those, speak only from the facts you were given.]");
    }

    /**
     * The question the agent's previous line ended on, or {@code null} when it asked
     * none. Only the last one: a reply carries at most one question by rule, and it is
     * the one the caller was answering.
     */
    static String lastQuestion(String agentText) {
        if (agentText == null) {
            return null;
        }
        int end = agentText.lastIndexOf('?');
        if (end < 0) {
            return null;
        }
        int start = end;
        while (start > 0 && ".!?".indexOf(agentText.charAt(start - 1)) < 0) {
            start--;
        }
        String question = agentText.substring(start, end + 1).trim();
        return question.isEmpty() ? null : question;
    }

    private static String allowedNext(ScenarioDefinition scenario, StageDef stage) {
        String next = describeReachableStages(scenario, stage);
        return next.isEmpty() ? "(final state)" : next;
    }

    /**
     * The ready-made first line of each stage the call may move to next ({@link
     * StageDef#staticLine}), facts filled in. Listed for the next stages rather than the
     * current one because the model speaks a stage's line in the same turn it transitions
     * into it — by the time the state says "here", the line has been said.
     */
    private static void appendStaticLines(StringBuilder sb, ScenarioDefinition scenario, StageDef stage,
                                          Map<String, Object> facts) {
        for (String target : StageEntry.findReachableStages(scenario, stage)) {
            String line = renderStaticLine(scenario.findStage(target), facts);
            if (line != null) {
                sb.append("- If you move to ").append(target).append(", say EXACTLY this: «")
                        .append(line).append("»\n");
            }
        }
    }

    /** {@code stage}'s {@link StageDef#staticLine} with the facts filled in, or null when it has none. */
    static String renderStaticLine(StageDef stage, Map<String, Object> facts) {
        if (stage == null || stage.staticLine() == null || stage.staticLine().isBlank()) {
            return null;
        }
        return PromptTemplateEngine.render(stage.staticLine().trim(), facts == null ? Map.of() : facts);
    }

    /**
     * What the call's tools have recorded so far. On a long call the model looked for these
     * in the history and asked again for a date it had been given three turns before.
     */
    private static void appendRecorded(StringBuilder sb, Map<String, Object> outcome) {
        if (outcome == null || outcome.isEmpty()) {
            return;
        }
        StringBuilder recorded = new StringBuilder();
        new TreeMap<>(outcome).forEach((name, value) -> {
            if (!recorded.isEmpty()) {
                recorded.append("; ");
            }
            recorded.append(name).append(" = ").append(PromptSafeText.sanitize(String.valueOf(value), MAX_RECORDED_CHARS));
        });
        sb.append("ALREADY ESTABLISHED (do not ask again; if the client changes it, record it again): ").append(recorded).append('\n');
    }

    /**
     * What the caller said that no outcome tool records, noted with noteClientFact. Here, after the
     * history, rather than in the cached prefix — and here on every turn, so it outlives a trimmed history.
     */
    private static void appendClientFacts(StringBuilder sb, Map<String, String> facts) {
        if (facts == null || facts.isEmpty()) {
            return;
        }
        StringBuilder noted = new StringBuilder();
        facts.forEach((name, value) -> {
            if (!noted.isEmpty()) {
                noted.append("; ");
            }
            noted.append(PromptSafeText.sanitize(name, MAX_RECORDED_CHARS)).append(" = ")
                    .append(PromptSafeText.sanitize(value, MAX_RECORDED_CHARS));
        });
        sb.append("THE CLIENT TOLD YOU (keep it in mind, do not ask again): ").append(noted).append('\n');
    }

    /**
     * The stages {@code stage} may move to, each with its {@link StageDef#transitionConditions
     * condition} — {@code "A (if: …), B"} — or empty when it names none.
     */
    static String describeTransitions(StageDef stage) {
        if (stage == null || stage.allowedTransitions() == null || stage.allowedTransitions().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String target : stage.allowedTransitions()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(target);
            String condition = stage.transitionConditions() == null ? null : stage.transitionConditions().get(target);
            if (condition != null && !condition.isBlank()) {
                sb.append(" (if: ").append(condition.trim()).append(')');
            }
        }
        return sb.toString();
    }

    /**
     * {@link #describeTransitions}, followed by the stages the model may also move to without a
     * drawn edge ({@link StageEntry#findReachableStages}): each global stage with its condition
     * and, in a FLEX flow, each other conversation stage with its purpose.
     */
    static String describeReachableStages(ScenarioDefinition scenario, StageDef stage) {
        StringBuilder sb = new StringBuilder(describeTransitions(stage));
        List<String> drawn = stage == null || stage.allowedTransitions() == null ? List.of() : stage.allowedTransitions();
        for (String target : StageEntry.findReachableStages(scenario, stage)) {
            if (drawn.contains(target)) {
                continue;
            }
            StageDef other = scenario.findStage(target);
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(target);
            if (other.globalCondition() != null && !other.globalCondition().isBlank()) {
                sb.append(" (at any time, if: ").append(other.globalCondition().trim()).append(')');
            } else if (other.purpose() != null && !other.purpose().isBlank()) {
                sb.append(" (if the conversation is already there: ").append(other.purpose().trim()).append(')');
            }
        }
        return sb.toString();
    }

    /** The call language's English name; shared with {@link RealtimeSystemPromptFactory}. */
    static String languageName(String code) {
        if (isRussian(code)) return "Russian";
        if (code != null && code.toLowerCase().startsWith("en")) return "English";
        return "Uzbek";
    }

    private static boolean isRussian(String code) {
        return code != null && code.toLowerCase().startsWith("ru");
    }

    /**
     * The language rule for a call that is multilingual or in neither Uzbek nor Russian, or
     * null when the stock "Uzbek or Russian only" rule fits. Shared with {@link RealtimeSystemPromptFactory}.
     */
    static String languageRestriction(String language, List<String> detectLanguages) {
        if (detectLanguages.size() <= 1 && isUzbekOrRussian(language)) {
            return null;
        }
        return "LANGUAGE RESTRICTION: This call starts in " + languageName(language)
                + ". Allowed languages for this call: "
                + String.join(", ", detectLanguages.isEmpty() ? List.of(languageName(language)) : detectLanguages)
                + ". Never switch to an unsupported language.";
    }

    /** Whether the call is held in Uzbek — the language the realtime hearing instruction is written for. */
    static boolean isUzbek(String code) {
        return code == null || code.isBlank() || code.toLowerCase(Locale.ROOT).startsWith("uz");
    }

    private static boolean isUzbekOrRussian(String code) {
        if (code == null) return false;
        String lower = code.toLowerCase(Locale.ROOT);
        return lower.startsWith("uz") || lower.startsWith("ru");
    }

    private static String orDash(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }


    /**
     * The name a HUMAN_LIKE agent gives when it introduces itself, taken from the voice
     * it is speaking with so the name matches what the caller hears.
     *
     * <p>Only voices whose id is already a person's name are used. An id that is not one
     * falls back to the language default rather than being title-cased into a name:
     * Gemini Live's voices are called Fenrir, Charon, Kore, Zephyr, and one real call
     * opened with "Men Default kompaniyasidan Fenrirman".
     */
    public static String resolveVoicePersonaName(String voice, String language) {
        if (voice != null && !voice.isBlank()) {
            String v = voice.trim().toLowerCase(java.util.Locale.ROOT);
            if (v.contains("dilnavoz")) return "Dilnavoz";
            if (v.contains("gulnoza")) return "Gulnoza";
            if (v.contains("zamira")) return "Zamira";
            if (v.contains("yulduz")) return "Yulduz";
            if (v.contains("nigora")) return "Nigora";
            if (v.contains("anvar")) return "Anvar";
            if (v.contains("filipp")) return "Filipp";
            if (v.contains("alena") || v.contains("alyona")) return "Alyona";
            if (v.contains("jane")) return "Jane";
        }
        if (isRussian(language)) return "Анна";
        if (language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("en")) return "Alex";
        return "Dilnoza";
    }
}
