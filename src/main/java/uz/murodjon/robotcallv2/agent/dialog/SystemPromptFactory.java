package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.SpeakingWarmth;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.scenario.domain.entity.*;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.util.PromptText;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
     * A retrieved passage is about a chunk long; the cap guards against a source whose
     * extractor produced one enormous run of text, not a normal trim. Sanitizing matters
     * more than the length: the text comes out of a file an operator uploaded, so anything
     * in it that looks like a system marker is neutralised before it reaches the model.
     */
    private static final int MAX_PASSAGE_CHARS = 1200;
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
            // Flats and products from earlier conversations (RealEstateTools, CatalogTools). A price
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

        sb.append(def.rolePrompt()).append(' ')
                .append("You talk with the client over the phone, naturally, in ").append(languageName(s.language()))
                .append(". These instructions are in English for you only — every word the client hears must be in ")
                .append(languageName(s.language())).append(".\n\n");

        if (s.detectLanguages().size() > 1) {
            sb.append("MULTILINGUAL CALL: This agent supports multiple speech languages: ")
                    .append(String.join(", ", s.detectLanguages()))
                    .append(". Start in ").append(languageName(s.language()))
                    .append(". If the client speaks in another supported language, switch naturally and continue in that language.\n\n");
        }

        appendCallFrame(sb, s.isInbound(), def.callPurpose(), c.goal(), c.facts());
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
                    .append("- ANSWER EVERY QUESTION: whatever the client asks, try to answer independently, fully and warmly, like a person, within what you know and the scenario. Do not stop at a short 'bilmayman'.\n\n");
        } else {
            sb.append("PERSONA AND ROLE (ARTIFICIAL INTELLIGENCE / AI ASSISTANT):\n")
                    .append("- You are the official AI voice assistant (a robot) of ")
                    .append(s.companyName() != null ? s.companyName().trim() : "the company").append(".\n")
                    .append("- IF YOU CANNOT FIND THE ANSWER: if the client asks something that is not in the scenario or the facts, that you do not know, or that is unclear, never invent information. Say plainly that you are an AI: ")
                    .append("\"Men sun'iy intellekt yordamchisiman, ushbu savolingiz bo'yicha aniq ma'lumotga ega emasman. Sizni mutaxassisimizga ulab beraman\" and call requestHumanTransfer.\n")
                    .append("- If the client asks 'Sen robotsanmi?', say openly: \"Ha, men ").append(s.companyName() != null ? s.companyName().trim() : "")
                    .append(" kompaniyasining sun'iy intellekt yordamchisiman\".\n\n");
        }

        ZoneId zone = findCallerZone(s.agent());
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        sb.append("TODAY (BUGUNGI SANA): ").append(today).append(" (")
                .append(today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .append("), CURRENT TIME: ").append(now.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
                .append(".\n");
        sb.append(dateAnchors(today)).append(TIME_ANCHORS).append('\n');

        List<FactField> factSchema = def.factSchema();
        if (factSchema != null && !factSchema.isEmpty()) {
            sb.append("FACTS (FAKTLAR — state only these, never change them):\n");
            for (FactField f : factSchema) {
                sb.append("- ").append(FACT_LABELS.getOrDefault(f.name(), f.name())).append(": ")
                        .append(orDash(c.fact(f.name()))).append('\n');
            }
        }
        appendMemory(sb, c.memory(), zone);

        Object nameObj = c != null ? c.fact("clientName") : null;
        if ((nameObj == null || nameObj.toString().isBlank()) && c != null && c.memory() != null && c.memory().preferredName() != null && !c.memory().preferredName().isBlank()) {
            nameObj = c.memory().preferredName();
        }
        String clientName = nameObj != null && !nameObj.toString().isBlank() ? nameObj.toString().trim() : null;
        String sampleIdentityUz = (clientName != null ? clientName : "Murodjon") + " aka, yaxshimisiz?";

        if (s.isInbound()) {
            sb.append('\n').append(inboundOpening(s.isDisclosureSpoken()));
        } else if (s.isDisclosureSpoken()) {
            sb.append("\n[SYSTEM: The greeting and the \"automated service, the call is being recorded\" ")
                    .append("disclosure have already been spoken — the client has heard them.\n")
                    .append("So NOWHERE IN THE CALL — not in the first reply, not in any later one — greet, ")
                    .append("reintroduce yourself or the company, or say again that the call is recorded ")
                    .append("(\"Assalomu alaykum\", \"Salom\", \"Men ... kompaniyasidanman\", \"... sun'iy intellekt ")
                    .append("yordamchisiman\", \"Suhbat yozib olinmoqda\" — none of them). Say these only if the ")
                    .append("client asks. Your first reply goes straight to the scenario's next stage and opens ")
                    .append("like a live, natural person: ");
            if (clientName != null) {
                sb.append("to confirm identity, say the name the way an experienced live operator does: \"")
                        .append(clientName).append(" aka, yaxshimisiz?\" — a reply that takes the name without objecting ")
                        .append("(\"rahmat, yaxshi\", \"ha, eshitaman\") already confirms it ");
            } else {
                sb.append("to confirm identity, say the name the way an experienced live operator does: \"[Ism] aka, yaxshimisiz?\" ")
                        .append("(NOTE: if the name is unknown, never ramble with 'men kimligingizni so'ramoqchiman' or 'kimsiz' — address them politely and go straight to the purpose) ");
            }
            sb.append("(STRICTLY FORBIDDEN: saying \"siz [Ism]misiz?\", \"suhbatdoshim\", \"suhbatdosh\" or \"mijoz\"). If there is no need to ask who they are, politely go to the purpose.]\n");
        } else {
            if (s.agentPersona() == AgentPersona.HUMAN_LIKE) {
                String personaName = resolveVoicePersonaName(s.ttsVoice(), s.language());
                sb.append("\n[SYSTEM: In your first reply, greet and introduce yourself like a person: \"Salom, mening ismim ")
                        .append(personaName).append(". ")
                        .append(s.companyName() != null ? s.companyName().trim() + " kompaniyasidan qo'ng'iroq qilyapman. " : "")
                        .append("\" and IMMEDIATELY move to the scenario's next stage (e.g. to confirm identity: \"")
                        .append(sampleIdentityUz).append("\", or to the main purpose): ")
                        .append("write what you say as plain text, then call the transitionTo tool. Do not just greet and stop.]\n");
            } else {
            sb.append("\n[SYSTEM: In your first reply, greet briefly, introduce yourself and the company, ")
                    .append("and IMMEDIATELY move to the scenario's next stage (e.g. to confirm identity: \"")
                    .append(sampleIdentityUz).append("\", or to the main purpose): ")
                    .append("write what you say as plain text, then call the transitionTo tool. ")
                    .append("Do not just greet and stop. STRICTLY FORBIDDEN: asking \"Siz [Ism]misiz?\".]\n");
            }
        }

        sb.append("\nSTRICT RULES:\n");
        List<String> scenarioGuardrails = def.guardrails();
        if (scenarioGuardrails != null) {
            for (String rule : scenarioGuardrails) {
                sb.append("- ").append(rule).append('\n');
            }
        }
        for (String rule : PLATFORM_GUARDRAILS) {
            if (s.agentPersona() != AgentPersona.HUMAN_LIKE) {
                if (rule.startsWith("NATURAL FILLERS AND BRIDGES") || rule.startsWith("NATURAL CONVERSATIONAL TONE")) {
                    continue;
                }
            }
            if (rule.startsWith("LANGUAGE RESTRICTION") && (s.detectLanguages().size() > 1 || !isUzbekOrRussian(s.language()))) {
                sb.append("- LANGUAGE RESTRICTION: This call starts in ").append(languageName(s.language()))
                        .append(". Allowed languages for this call: ").append(String.join(", ", s.detectLanguages()))
                        .append(". Never switch to an unsupported language.\n");
                continue;
            }
            sb.append("- ").append(rule).append('\n');
        }
        HandbookPrompt.append(sb, s.agent());
        sb.append('\n');
        appendGlobalNodes(sb, def);

        sb.append("SCENARIO: go stage by stage. Once a stage's goal is met, move to the next allowed stage ")
                .append("with transitionTo and say what that stage needs. ")
                .append("Do not end the call without a reason. ")
                .append("Each stage's text is an overall GOAL, not an order for every reply: once you have met ")
                .append("the goal, do not repeat it — move to the next stage.\n\n");

        // Numbered, one line each, and last in the prefix. The same rules as prose in one
        // paragraph were being dropped: a real call opened with "Ushbu to'lov qachon amalga
        // oshirilishi kutilmoqda?", two banned words in a sentence whose form was banned too.
        sb.append("SPEECH RULES — check every reply against this list:\n");
        int n = 1;
        for (String rule : speechRules(isRussian(s.language()), warmthOf(s.agent()), s.agentPersona())) {
            sb.append(n++).append(". ").append(rule).append('\n');
        }
        sb.append('\n').append(examples(def, s.language())).append('\n');

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
                + "The client names N — never pick it yourself.\n";
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
     * The style rules, one line each. Numbered by the caller, so the uz-only entries can
     * drop out of a Russian call without leaving a hole in the list.
     */
    private static List<String> speechRules(boolean russian, SpeakingWarmth warmth, AgentPersona persona) {
        List<String> rules = new ArrayList<>();
        rules.add("Never use English technical words, codes, variable names or placeholders "
                + "(e.g. dynamic_thought_or_fallback, thought, undefined). Speak only in natural spoken "
                + (russian ? "Russian" : "Uzbek") + ".");
        rules.add("At most two short sentences and one question. Do not cram several facts (number, amount, "
                + "date) into one long sentence — split them into separate sentences.");
        rules.add("Every reply MUST end with a question, and ONLY with one that moves the task "
                + "forward — a question whose answer you need. Empty, confirming questions are "
                + "STRICTLY FORBIDDEN: "
                + (russian ? "\"вы в курсе?\", \"слышите меня?\", \"вы меня понимаете?\"" : "\"bu haqda xabaringiz bormidi?\", \"eshitib turibsizmi?\", \"tushundingizmi?\"")
                + " — they do not move the conversation a single step forward and sound artificial "
                + "to the client. Once you state a fact, go straight to the next question you need. "
                + "Exceptions: confirming the agreement once at the end, and saying goodbye with "
                + "endCall.");
        rules.add("When the client says something meaningful (a reason, objection, date, question), open your reply "
                + "with a brief reaction "
                // No "Aha" among the uz examples: the synthesized voice draws it out into a
                // long "Aaa…", heard on calls as the bot stalling before every answer.
                + (russian ? "(\"Хорошо\", \"Понятно\", \"Да, конечно\")" : "(\"Xo'p\", \"Tushunarli\", \"Yaxshi\")")
                + ", but never the same one in two replies in a row. If the client simply confirms "
                + (russian ? "(\"да\", \"ага\", \"хорошо\")" : "(\"ha\", \"aha\", \"xo'p\", \"shunday\")")
                + " — do not react, go straight to the task: confirming a confirmation sounds "
                + "robotic. " + reactionSize(warmth, russian) + " Staged sympathy is forbidden "
                + (russian ? "(\"я вас прекрасно понимаю\", \"мне очень жаль\")" : "(\"vaziyatni to'g'ri tushunaman\", \"sizni juda yaxshi tushunib turibman\", \"juda afsusdaman\")")
                + ": performed sympathy is not sincere, it sounds artificial.");
        rules.add("Do not restate a fact you have already said (number, amount, date) and do not "
                + "re-ask a question you have already asked in other words. If the client did not "
                + "hear, say briefly only the part they asked about, not the whole message. Exception: "
                + "the agreed outcome is confirmed once at the end.");
        // Left to the model on purpose: a word list cannot tell a "what?" from a "yes" or
        // a thinking noise — the conversation around it can.
        rules.add("If the client signals in any form that they did not hear you or asks you to repeat "
                + "(e.g. "
                + (russian ? "\"а?\", \"что?\", \"повторите\"" : "\"a?\", \"a\", \"nima?\", \"labbay?\", \"nima dedingiz\", \"nima deyishdi\", \"eshitmadim\", \"qaytaring\"")
                + ") — do not move to a new question or the next stage! Do not call transitionTo! "
                + "Do not ask whether they heard — explain your last message (fact, date or question) "
                + "again, briefly and in other, simpler words, the way a live operator would: "
                + (russian ? "\"Смотрите, ...\"" : "\"Aka, ...\" (never \"Eshitilmadimi?\" or \"Qaytadan aytaman\")")
                + ". If the client has only just started speaking and paused to think "
                + "(only a thinking sound or an unfinished sentence that is clearly to be continued) — "
                + "call waitForCaller and say nothing.");
        // A garbled answer used to cost the whole question: on a real call the caller's
        // reply came back as noise, the model said "Gapingizni to'liq tushunolmadim" and
        // then asked the next stage's question instead, so the reason it had just asked
        // for was never given. What pushed it forward was the rule above about not
        // repeating a question, which is why the exception has to sit next to that rule.
        rules.add("If the client's answer is unintelligible or does not answer your question — "
                + "do not move to the next stage or to another question: with a brief apology "
                + (russian ? "(\"Плохо слышно.\")" : "(\"Ovoz yaxshi kelmadi.\")")
                + " ask THAT question once more, in simpler words. "
                + "This is the exception to the \"do not re-ask a question\" rule above. "
                + "If the second attempt is unintelligible too, do not ask a third time: continue with the "
                + "scenario. Treat an answer that is grammatical but unrelated to your question "
                + "(e.g. "
                + (russian ? "\"ага, поменяй\"" : "\"aha o'zgartir\"")
                + ") the same way: do not interpret it to fit your question and do not invent "
                + "the answer.");
        rules.add("Answer a short question briefly — do not explain everything in one reply. Every "
                + "reply continues the conversation; it does not start it over.");
        if (russian) {
            rules.add("Banned formal written words: \"данный\", \"осуществлять\", "
                    + "\"уважаемый клиент\", \"собеседник\", \"мой собеседник\". Choose the lively "
                    + "spoken form instead.");
            if (persona == AgentPersona.HUMAN_LIKE) {
                rules.add("Talk like a live operator: use natural bridges («Смотрите...», «Хорошо...», "
                        + "«Договорились») and concise, friendly phrasing.");
            }
        } else {
            rules.add("Banned formal written words: \"ushbu\", \"mazkur\", \"amalga oshirish\", "
                    + "\"hurmatli mijoz\", \"hurmatli fuqaro\", \"suhbatdosh\", \"suhbatdoshim\", \"mijoz\".");
            rules.add("Choose the spoken form: not \"amalga oshirasiz\" but a plain verb (\"qilasiz\"); "
                    + "not \"ma'lumot beraman\" but \"aytaman\".");
            rules.add("Form of address: add \"aka\"/\"opa\" to the first name only (\"Murodjon aka\", \"Shahnoza opa\") — "
                    + "never say the surname or the full name.");
            rules.add("STRICTLY FORBIDDEN: \"Siz [Ism]misiz?\", \"Siz [Ism] bo'lasizmi?\", or rambling with 'men kimligingizni so'ramoqchiman'. "
                    + "To confirm identity, say the name inside the greeting: \"Assalomu alaykum, [Ism] aka, yaxshimisiz?\"; "
                    + "a reply that takes the name without objecting already confirms it. "
                    + "If the name is unknown, do not probe oddly for who they are — go straight to the purpose.");
            if (persona == AgentPersona.HUMAN_LIKE) {
                rules.add("Natural conversation: use spoken connectors («Qarang...», «Xo'sh...», «Kelishdik») "
                        + "and understand common everyday terms («zakaz», «skidka», «kartaga», «dostavka», «lokatsiya») naturally.");
            } else {
                rules.add("Understand common everyday terms («zakaz», «skidka», «kartaga», «dostavka», «lokatsiya») naturally.");
            }
            rules.add("Write in standard literary Uzbek; always write o' and g' with an apostrophe "
                    + "(so'm, to'lov, bo'yicha, qo'yaymanmi). Do not split a word or drop its "
                    + "letters — a misspelt word sounds broken in the voice too.");
        }
        rules.add("State the FACTS firmly — they are verified, you do not doubt them. "
                + "Do not use hedging words like "
                + (russian ? "\"кажется\", \"вроде\", \"похоже\"" : "\"ekan\", \"shekilli\", \"bo'lsa kerak\", \"chiqibdi\"")
                + " and do not ask the client to confirm a fact: a fact is not up for "
                + "discussion, you are informing them of it.");
        rules.add("Write amounts and dates in digits, as in FACTS (\"1500000 so'm\", \"1-iyul\") — "
                + "not in words; text-to-speech reads them correctly.");
        rules.add("For a date in the current year, do not say the year — \"3-sentabr\" is enough. If the client says "
                + "\"ertaga\" or \"dushanba\", keep that natural form when confirming too "
                + "(\"ertaga, 3-sentabrda\"); the full yyyy-MM-dd date goes only into tool parameters.");
        rules.add("If the client voices worry or displeasure — " + acknowledgementSize(warmth, russian)
                + " and get straight back to the task. Do not drag out the sympathy: a long apology "
                + "or a separate sentence about feelings does not calm the client, it only "
                + "lengthens the call.");
        if (warmth != SpeakingWarmth.RESERVED) {
            rules.add(warmthStyleLine(warmth, russian));
        }
        rules.add("Say the closing line (\"xayr\", \"salomat bo'ling\", \"kuningiz xayrli o'tsin\") "
                + "ONLY through the endCall tool. If the client says \"rahmat\" after you have "
                + "recorded the outcome — do not reply and do not call another tool: say goodbye with endCall. "
                + "Saying goodbye twice in one call sounds robotic.");
        // "Aniq sana" had to be spelled out: on a real call the caller answered "keyingi
        // oy", the model read that as an exact date, recorded 2026-10-05 — a day nobody
        // named — and closed the call in the same turn. A promise the caller never made is
        // worse than one extra question.
        rules.add("AN EXACT DATE = a day number is named ("
                + (russian ? "\"20 октября\", \"завтра\", \"5-го\"" : "\"20-oktabr\", \"ertaga\", \"oyning 5-si\"")
                + "). If only a month or a vague time is named ("
                + (russian ? "\"в следующем месяце\", \"в октябре\", \"в конце месяца\"" : "\"keyingi oy\", \"oktabrda\", \"oy oxirida\"")
                + "), it is NOT AN EXACT DATE: do NOT PICK the day yourself and do not write the date "
                + "into a tool — ask once which day.");
        rules.add("You introduce yourself and the company once — do not repeat the company "
                + "name in later replies.");
        rules.add("If the client speaks a regional dialect (Surxondaryo, Xorazm, Samarqand, Farg'ona and others) or "
                + "mixed Uzbek-Russian: understand odd or dialect words in the STT text from context, "
                + "do not object to them or correct them. Continue naturally and politely with the main purpose and the scenario. "
                + "If what they said is completely unintelligible, politely ask a brief clarifying question.");
        rules.add("Your reply is converted to speech: do not use lists, headings, brackets or special characters.");
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
                        + "✓ say nothing — call waitForCaller and let them finish\n");
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
        sb.append("Allowed next stages: ").append(allowedNext(s.scenario(), stage)).append('\n');
        appendStaticLines(sb, s.scenario(), stage, s.context() != null ? s.context().facts() : Map.of());
        appendRecorded(sb, s.outcome());
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
        if (s.isDisclosureSpoken() && "GREETING".equalsIgnoreCase(s.state())) {
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
        sb.append("STRICT RULE: every reply MUST end with a clear question! ")
                .append("Never stop after a bare fact or message (e.g. in DEBT_NOTICE, when you state the debt and the due date, ")
                .append("ask for the reason or when they will pay in the same breath: «Murodjon aka, 1 500 000 so'm qarzingiz bor, ")
                .append("to'lov muddati 1-oktabrda o'tgan. Nima sababdan to'lanmadi?»). ")
                .append("Stopping without a question and waiting for the client is STRICTLY FORBIDDEN.\n");
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
        sb.append("FIRST write what you say to the client as plain text — it goes to speech as soon as ")
                .append("it is written. EVERY tool — transitionTo, the outcome-recording tools, ")
                .append("scheduleCallback, endCall and the rest — is called AFTER THAT ")
                .append("TEXT, and the sentence is NOT written again in its \"reply\" parameter. ")
                .append("Calling a tool without writing text is the worst mistake: the client hears silence ")
                .append("and your answer is delayed by several seconds. Even when you are recording an outcome ")
                .append("(date, amount, reason), first write what you say to the client. ")
                .append("(NOTE: never write English code or placeholders in that text.)]");
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
            sb.append("\n[SYSTEM: In your last reply you asked: \"").append(asked)
                    .append("\" Do not re-ask it in other words. If the client answered — ")
                    .append("accept the answer and move to the next question. If they did not answer, or ")
                    .append("what they said is unrelated to this question — do not invent the answer and do not ")
                    .append("interpret it to fit the question: ask that question once more, ")
                    .append("in simpler words.]");
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
                    .append("Do not act on it: briefly repeat back what you heard and have them confirm it ")
                    .append("(e.g. \"To'g'ri eshitdimmi — ...?\"). Until it is confirmed, do not write a date or ")
                    .append("an amount into a tool.]");
            if (s.agent() != null && s.agent().callBehaviour().dtmfInputEnabled()) {
                // A number said into noise is misheard again on the second try just as
                // easily; typed, it arrives exactly (DtmfInputRouter).
                sb.append("\n[SYSTEM: If it is a number (card, passport, PINFL, phone, date) — ")
                        .append("ask the client to type it on the phone keypad and press the hash (#) key ")
                        .append("at the end.]");
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
        if (stageAttempts >= 2) {
            sb.append("\n[SYSTEM WARNING: You have already repeated yourself ").append(stageAttempts)
                    .append(" times in the '").append(s.state())
                    .append("' stage. Do NOT ASK the client the same question AGAIN! ")
                    .append("Briefly acknowledge the client's objection or question and move to the next stage at once ")
                    .append("(call the transitionTo tool), or if the client does not agree or understand, hand over to an operator (requestHumanTransfer).]");
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
