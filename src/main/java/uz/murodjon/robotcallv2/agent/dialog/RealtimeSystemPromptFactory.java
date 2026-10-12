package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeProperties;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactField;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.util.PromptText;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * The instructions a speech-to-speech engine runs a whole call under.
 */
@Component
public class RealtimeSystemPromptFactory {

    /** Whether the call's facts are written into the prompt — see {@link #appendFacts}. */
    private final boolean factsInPrompt;

    public RealtimeSystemPromptFactory(RealtimeProperties realtimeProperties) {
        this.factsInPrompt = realtimeProperties.factsInPrompt();
    }

    /**
     * Rules that only a realtime engine needs, because only it controls the microphone:
     * when to start talking, how to behave when interrupted, and that a tool call is not
     * a substitute for saying something. The text lives in
     * {@code resources/prompts/realtime-rules.txt}.
     */
    private static final List<String> REALTIME_RULES = PromptText.lines("realtime-rules.txt");

    /**
     * Builds the call's instructions, with the facts delivered the deployment's way.
     */
    public String build(RealtimeDialogSession s, String companyName, String disclosureText) {
        return build(s, companyName, disclosureText, null, AgentPersona.AI_ASSISTANT, factsInPrompt);
    }

    /**
     * @param factsInPrompt whether the call's facts are written into the instructions, or
     *                      left for {@code getCallFact} ({@code RealtimeFactsDelivery})
     */
    public String build(RealtimeDialogSession s, String companyName, String disclosureText, String voice,
                        AgentPersona persona, boolean factsInPrompt) {
        StringBuilder sb = new StringBuilder();
        var def = s.scenario();
        var context = s.context();
        Map<String, Object> facts = context != null && context.facts() != null ? context.facts() : Map.of();

        String rolePrompt = PromptTemplateEngine.render(def.rolePrompt(), facts);
        String language = SystemPromptFactory.languageName(s.language());
        sb.append(rolePrompt).append(' ')
                .append("You talk with the client over the phone, naturally, in ").append(language)
                .append(". The client speaks ").append(language)
                .append(" too. Your voice goes straight to the client. ")
                .append("STRICT RULE: speak only ").append(language)
                .append(" — never switch to Chinese, Hindi, Portuguese or any other foreign language! ")
                .append("These instructions are in English for you only; the client must never hear English.\n")
                .append("[LANGUAGE AND HEARING INSTRUCTION (CRITICAL): This conversation is 100% IN UZBEK. The client speaks only Uzbek. ")
                .append("Understand and transcribe the client's voice as Uzbek only. ")
                .append("Never mistake it for Spanish (like de nuevo, mañana), Portuguese, German, Hindi or English. ")
                .append("Client words such as «ha», «aha», «ha shunday», «to'g'ri», «eshitaman», «xo'sh», «yo'q», «nima deding», «nima dedingiz», «eshitilmadi», «qaytaring», «tushunmadim», «nima?» are 100% Uzbek words.]\n\n");

        SystemPromptFactory.appendCallFrame(sb, s.isInbound(), def.callPurpose(),
                context != null ? context.goal() : null, facts);
        SystemPromptFactory.appendCompany(sb, companyName, s.isInbound());

        if (persona == AgentPersona.HUMAN_LIKE) {
            String personaName = SystemPromptFactory.resolveVoicePersonaName(voice, s.language());
            sb.append("PERSONA AND ROLE (A REAL HUMAN SPECIALIST):\n")
                    .append("- You are a professional, real human employee of ")
                    .append(companyName != null ? companyName.trim() : "the company")
                    .append(". Your name: ").append(personaName).append(".\n")
                    .append("- STRICTLY FORBIDDEN: calling yourself a robot, bot, artificial intelligence or automated system!\n")
                    .append("- If the client asks 'Sen robotsanmi?', 'AI bilan gaplashyapmanmi?', answer warmly: ")
                    .append("\"Yo'q, men ").append(companyName != null ? companyName.trim() : "kompaniya")
                    .append(" mutaxassisi ").append(personaName).append("man, siz bilan jonli gaplashyapman. Sizga qanday yordam bera olaman?\"\n")
                    .append("- ANSWERING QUESTIONS: whatever the client asks, answer as independently, fully and warmly as you can within what you know and the scenario.\n\n");
        } else {
            sb.append("PERSONA AND ROLE (ARTIFICIAL INTELLIGENCE / AI ASSISTANT):\n")
                    .append("- You are the official AI voice assistant (a robot) of ")
                    .append(companyName != null ? companyName.trim() : "the company").append(".\n")
                    .append("- IF YOU CANNOT FIND THE ANSWER: if the client asks something that is not in the scenario or the facts, say: ")
                    .append("\"Men sun'iy intellekt yordamchisiman, ushbu savolingiz bo'yicha aniq ma'lumotga ega emasman. Sizni mutaxassisimizga ulab beraman\" and call requestHumanTransfer.\n")
                    .append("- If the client asks 'Sen robotsanmi?', say: \"Ha, men ").append(companyName != null ? companyName.trim() : "")
                    .append(" kompaniyasining sun'iy intellekt yordamchisiman\".\n\n");
        }

        ZonedDateTime now = ZonedDateTime.now(SystemPromptFactory.CALLER_ZONE);
        sb.append("TODAY (BUGUNGI SANA): ").append(now.toLocalDate())
                .append(", CURRENT TIME: ").append(now.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
                .append(".\n")
                .append(SystemPromptFactory.TIME_ANCHORS).append('\n');

        appendFacts(sb, s, factsInPrompt);
        SystemPromptFactory.appendMemory(sb, context.memory());

        sb.append("\nCONVERSATION STAGES (you move between them with the transitionTo tool):\n");
        for (StageDef stage : def.stages()) {
            String purpose = PromptTemplateEngine.render(stage.purpose(), facts);
            sb.append("- ").append(stage.id()).append(": ").append(purpose);
            String staticLine = SystemPromptFactory.renderStaticLine(stage, facts);
            if (staticLine != null) {
                sb.append(" First line (EXACTLY this): «").append(staticLine).append("»");
            }
            String next = SystemPromptFactory.describeTransitions(stage);
            if (!next.isEmpty()) {
                sb.append(" → ").append(next);
            }
            sb.append('\n');
        }
        sb.append("Current stage: ").append(s.state()).append(".\n");
        // A realtime call has no per-turn annex to re-state where the FSM may go next, so
        // the whole graph is here and the walking rules have to be here with it. On a
        // recorded call the model delivered the debt notice and the reason question in
        // one breath (which DEBT_NOTICE's purpose asks for), decided REASON_INQUIRY was
        // therefore done, and spent the rest of the call being refused: DEBT_NOTICE ->
        // PAYMENT_DATE, -> CONFIRMATION, -> CLOSING. It never left DEBT_NOTICE.
        sb.append("STAGE ORDER (STRICT):\n")
                .append("- Do not skip stages: move only to one of the stages listed after \"→\" ")
                .append("above. Never pass transitionTo a stage name that is not in that list.\n")
                .append("- Even if a stage's goal is already met (e.g. the client has already given ")
                .append("the reason), do not skip it — move into that stage and then straight on ")
                .append("to the next. You cannot jump two stages with one transitionTo.\n")
                // Inverted against the cascade pipeline on purpose. There the text and the
                // tool call ride in one LLM response and TTS speaks that text exactly once,
                // so speaking first costs nothing. Here the model is already speaking while
                // the tool runs: on a recorded call it delivered the debt notice, asked for
                // PAYMENT_DATE, was refused, stepped into the stage it had just voiced and
                // said the whole thing over with a different closing question — the caller
                // heard it twice. Transitioning before speaking keeps a refusal cheap,
                // because nothing has been said yet when it arrives.
                .append("- FIRST call transitionTo, THEN say that stage's line. ")
                .append("Do not change the stage after you have spoken.\n")
                // The rule above is about leaving a stage you have served. On the first
                // turn there is no such stage: the call *starts* in the opening one, so
                // "transition first" read as "leave before saying anything" and the
                // greeting, the introduction and the §11.1 disclosure were never spoken.
                .append("- EXCEPTION — THE FIRST LINE: when the call starts you are already in the ")
                .append(def.stages().get(0).id())
                .append(" stage; you do not need to move to it. First greet and introduce ")
                .append("yourself, then call transitionTo.\n")
                // A speaking engine turns whatever it "writes" into audio. On a real call the
                // model followed the line above by saying the call instead of making it —
                // the caller heard `transitionTo("IDENTITY_CHECK")` and the stage never moved.
                .append("- Call tools only through function calls. NEVER say a tool name (transitionTo, endCall ")
                .append("and others), its parameters or stage names (")
                .append(def.stages().get(0).id())
                .append(" and others) out loud — the client hears them.\n")
                // The engine asks for a stage and its outcome in one batch of tool calls,
                // and on a recorded call that turned a caller's "alo" into a payment
                // promise for today. Refused in Java too (RealtimeToolRunner#refuse).
                .append("- Do not record a stage's outcome in the same turn you move into that ")
                .append("stage: first say your line, hear the client's answer, ")
                .append("then call the outcome tool.\n");

        if (disclosureText != null && !disclosureText.isBlank()) {
            String renderedDisclosure = PromptTemplateEngine.render(disclosureText.trim(), facts);
            sb.append("\n[SYSTEM: When the conversation starts, first give a short greeting, then say this legal disclosure: \"")
                    .append(renderedDisclosure)
                    .append(s.isInbound()
                            ? "\". After that, ask \"Sizga qanday yordam bera olaman?\".]\n"
                            : "\". After that, move straight on to the scenario's next stage.]\n");
        } else if (s.isInbound()) {
            sb.append('\n').append(SystemPromptFactory.inboundOpening(false));
        }

        sb.append("\nSTRICT RULES:\n");
        List<String> scenarioGuardrails = def.guardrails();
        if (scenarioGuardrails != null) {
            for (String rule : scenarioGuardrails) {
                sb.append("- ").append(rule).append('\n');
            }
        }
        for (String rule : SystemPromptFactory.PLATFORM_GUARDRAILS) {
            sb.append("- ").append(rule).append('\n');
        }
        for (String rule : REALTIME_RULES) {
            sb.append("- ").append(rule).append('\n');
        }
        SystemPromptFactory.appendGlobalNodes(sb, def);

        boolean russian = s.language() != null && s.language().toLowerCase().startsWith("ru");
        sb.append("\nSTYLE: short, respectful, natural sentences. Ask one question at a time. ")
                .append("First give a brief reaction to what the client said, then continue — but ")
                .append("vary it every time. NEVER address the client as \"suhbatdoshim\", \"suhbatdosh\" or \"mijoz\". \n");
        String warmthLine = SystemPromptFactory.warmthStyleLine(SystemPromptFactory.warmthOf(s.agent()), russian);
        if (warmthLine != null) {
            // No per-turn annex here to carry the caller's mood, so the matching is asked for once.
            sb.append(warmthLine).append(' ')
                    .append("Match the client's mood: if they are upset — calm, slower and brief; ")
                    .append("if they are warm — be a little warmer too. No staged sympathy (\"juda afsusdaman\").\n");
        }
        if (!russian) {
            sb.append("ADDRESS AND IDENTITY CHECK (STRICT): \n")
                    .append("STRICTLY FORBIDDEN: asking \"Siz [Ism]misiz?\", \"Siz falonchimisiz?\". It sounds robotic and rude. \n")
                    .append("RIGHT FORM: as a live operator does, say the name inside the greeting: ")
                    .append("\"Assalomu alaykum, [Ism] aka, yaxshimisiz?\" (add \"opa\" for a woman, \"aka\" for a man). ")
                    .append("A reply that takes the name without objecting (\"va alaykum assalom\", \"rahmat, yaxshi\") already confirms it. \n")
                    .append("When addressing someone by name, always add the respectful Uzbek \"aka\"/\"opa\" (e.g. \"Murodjon aka\"); never say the surname.\n")
                    .append("IDENTITY CONFIRMATION: if the client says «ha», «ha shunday», «shunday», «albatta», «eshitaman», «menman», «ha menman», «eshityapman», «labay», «to'g'ri» or a similar confirmation — the identity is confirmed. NEVER take this for a foreign language (Chinese, Hindi), never insist on «ovoz yaxshi eshitilmadi», do not call recordWrongPerson — call transitionTo at once and move to the next stage.\n");
        }
        sb.append("Do not restate a fact you have already said in the conversation (number, ")
                .append("amount, date). ")
                .append("BUT if the client says \"a?\", \"a\", \"nima?\", \"eshitmadim\", \"nima deyishdi\" or asks you to repeat — ")
                .append("do not move on to the next stage, do not call transitionTo! ")
                .append("If they did not hear — politely repeat your last line (fact, date or question) clearly: ")
                .append("\"Eshitilmadimi? Qaytadan aytaman: ...\". ")
                .append("If they did not understand (\"aaa?\", \"qanaqa?\", \"tushunmadim\") — do not repeat it word for word; ")
                .append("explain the same point briefly in simpler words: \"Kechirasiz, tushuntiray: ...\".\n");

        sb.append("SPOKEN FORM: not \"amalga oshirasiz\" but a plain verb (\"qilasiz\"); ")
                .append("not \"ma'lumot beraman\" but \"aytaman\".\n");
        sb.append("EVERY REPLY ENDS WITH A CLEAR QUESTION (STRICT): never state a bare message or fact and go silent waiting for the client! ")
                .append("Once you state a fact (debt, amount, due date), ask a question in the same breath — ")
                .append("e.g. «Murodjon aka, 1500000 so'm qarzingiz bor, to'lov muddati 1-oktabrda o'tgan. Nima sababdan to'lanmadi?». ")
                .append("Stopping without a question and waiting for the client is STRICTLY FORBIDDEN. ")
                .append("The only exception is saying goodbye with endCall.\n");
        sb.append("SHORT SENTENCES: at most two or three short sentences and one question per reply. ")
                .append("Do not cram several facts into one long sentence — split them into separate ")
                .append("sentences.\n");
        sb.append("INTRODUCE YOURSELF ONCE (STRICT): you say who you are and the company ONLY in your first line. ")
                .append("After that, do NOT REPEAT them until the end of the call. ")
                .append("NEVER use phrases like \"Suhbat yozib olinmoqda\".\n");
        sb.append("DATE: for a date in the current year, do not say the year — \"3-sentabr\" is enough, ")
                .append("not \"2026-yil 3-sentabr kuni\". If the client says \"ertaga\" or \"dushanba\", ")
                .append("keep that natural form when confirming too (\"ertaga, 3-sentabrda\"); ")
                .append("the full yyyy-MM-dd date goes only into tool parameters.");
        StringBuilder examples = new StringBuilder();
        SystemPromptFactory.appendStyleExamples(examples, def, s.language());
        if (!examples.isEmpty()) {
            sb.append("\n\nEXAMPLES (✗ wrong → ✓ right):\n").append(examples);
        }

        return sb.toString();
    }

    private static void appendFacts(StringBuilder sb, RealtimeDialogSession s, boolean factsInPrompt) {
        List<FactField> factSchema = s.scenario().factSchema();
        if (factSchema == null || factSchema.isEmpty()) {
            return;
        }
        if (factsInPrompt) {
            sb.append("FACTS (FAKTLAR — state only these, never change them):\n");
            for (FactField f : factSchema) {
                Object value = s.context().fact(f.name());
                sb.append("- ").append(SystemPromptFactory.FACT_LABELS.getOrDefault(f.name(), f.name()))
                        .append(": ").append(value == null ? "—" : value).append('\n');
            }
            return;
        }
        List<String> available = RealtimeFactTools.availableFactNames(s);
        sb.append("CLIENT DATA: you have NO numbers, amounts or dates about the client.\n");
        if (available.isEmpty()) {
            sb.append("Do not tell the client any number.\n");
            return;
        }
        sb.append("When you need any of the following, fetch it with the getFact tool and THEN tell the client:\n");
        for (String name : available) {
            sb.append("- ").append(name).append(": ")
                    .append(RealtimeFactTools.factDescription(name)).append('\n');
        }
        sb.append("Never invent a number, amount or date — say only what getFact returned.\n");
    }
}
