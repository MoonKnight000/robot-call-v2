package uz.murodjon.robotcallv2.pack.infrastructure.adapter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.pack.application.port.output.PackGeneratorPort;
import uz.murodjon.robotcallv2.pack.domain.entity.VerticalPack;
import uz.murodjon.robotcallv2.pack.infrastructure.config.PackGeneratorProperties;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.util.List;

/**
 * Writes a pack draft with a chat model (UNIVERSAL-OPERATOR-PLAN E.1).
 *
 * <p>The model is handed the shape of a pack and the tools that already exist for this API, and
 * writes the conversation around them. It is told twice, in the instructions and by the shape
 * itself, that the {@code tools} array is not its to fill: an endpoint invented here would be a
 * call placed to an address nobody checked.
 *
 * <p>Read leniently on purpose, unlike the packs that ship with the build: those are ours and a
 * misspelt key in one is a defect to stop the application for, while a model adding a field it
 * was not asked for should cost a shrug and not a draft.
 */
@Component
public class SpringAiPackGeneratorAdapter implements PackGeneratorPort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiPackGeneratorAdapter.class);

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String INSTRUCTIONS = """
            Siz ovozli AI agentlar platformasi uchun "soha paketi" yozadigan mutaxassissiz.
            Sizga biznesning o'z so'zlari bilan tavsifi beriladi. Siz shu biznes uchun ovozli
            operator ssenariysini yozasiz.

            JAVOB FAQAT JSON BO'LSIN, aynan shu maydonlar bilan:
            {
              "key": "lotin harflar va tire, qisqa: gul-dokoni",
              "name": "Paket nomi",
              "description": "Bir jumlada: agent nima qiladi",
              "industry": "soha kaliti, inglizcha: flowers, education, fitness",
              "agent": {
                "name": "Agent nomi",
                "description": "Bir jumla",
                "language": "til kodi",
                "firstMessage": "Agent birinchi aytadigan gap",
                "useRag": true,
                "scenario": {
                  "stages": [
                    {"id": "GREETING", "purpose": "Shu bosqichda nima qilinadi", "allowedTransitions": ["..."]}
                  ],
                  "factSchema": [],
                  "tools": [],
                  "outcomeSchema": [
                    {"name": "maydon", "type": "string", "description": "...", "goal": true}
                  ],
                  "rolePrompt": "Agent kimligi va nima qilishi",
                  "guardrails": ["Aytilmasligi kerak bo'lgan narsalar, buyruq gap bilan"],
                  "disclosureText": null,
                  "factWebhook": null,
                  "styleExamples": [{"language": "uz", "wrong": "\\"...\\"", "right": "\\"...\\""}]
                },
                "dataNeeded": [{"id": "...", "type": "string", "name": "...", "description": "..."}],
                "dataEvaluation": [{"id": "...", "name": "...", "criteria": "..."}]
              },
              "conversionGoal": {"goalKey": "...", "name": "...", "attributionWindowHours": 168, "enabled": true},
              "testCases": [
                {"name": "...", "personaPrompt": "Mijoz rolini o'ynaydigan modelga ko'rsatma",
                 "mustNotSay": ["..."], "maxTurns": 10}
              ]
            }

            QAT'IY QOIDALAR:
            - "tools" va "secrets" maydonlarini YOZMANG: tool'lar API hujjatidan olinadi.
              Bosqich matnida tool nomini ishlatishingiz mumkin (pastda ro'yxati bor), lekin
              yangi tool o'ylab topmang va URL yozmang.
            - Bosqichlar: 5 tadan 9 tagacha. Har bir bosqich "allowedTransitions" orqali oxir-oqibat
              END_CALL ga yeta olsin, END_CALL ning o'zida esa bo'sh ro'yxat bo'lsin. Har doim
              ESCALATE_TO_HUMAN va END_CALL bo'lsin.
            - "outcomeSchema" da AYNAN BITTA maydonda "goal": true bo'lsin — qo'ng'iroq nima uchun
              qilinayotganini o'lchaydigan maydon. Agar yozuvchi tool bo'lsa, maqsad shu tool
              qaytaradigan raqam bo'lsin.
            - Guardrails: kamida 4 ta. Ular agentning nimani TO'QIMASLIGI va NIMA VA'DA
              QILMASLIGI haqida bo'lsin (narx, muddat, chegirma, tibbiy yoki huquqiy maslahat),
              hamda karta/parol so'ramaslik.
            - testCases: aynan 5 ta, ulardan kamida 2 tasi "yomon niyatli" mijoz bo'lsin
              (chegirma talab qiladi, boshqa odam nomidan so'raydi, maxfiy ma'lumot aytmoqchi).
              "mustNotSay" — agent aytmasligi kerak bo'lgan aniq iboralar.
            - Hamma matn (firstMessage, purpose, guardrails, personaPrompt) AGENT TILIDA bo'lsin.
            - Biznes tavsifida yo'q faktni (narx, ish vaqti, muddat) ssenariyga yozmang. Kerak
              bo'lsa, agent uni bilimlar bazasidan o'qisin deb yozing.
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final PackGeneratorProperties packGeneratorProperties;

    public SpringAiPackGeneratorAdapter(ObjectProvider<ChatModel> chatModelProvider,
                                        PackGeneratorProperties packGeneratorProperties) {
        this.chatModelProvider = chatModelProvider;
        this.packGeneratorProperties = packGeneratorProperties;
    }

    @Override
    public VerticalPack generate(String business, String language, List<Tool> tools) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new ConflictException(ErrorCode.PACK_GENERATOR_NOT_CONFIGURED);
        }
        String answer;
        try {
            answer = ChatClient.create(chatModel).prompt()
                    .options(options(chatModel))
                    .system(INSTRUCTIONS)
                    .user(describe(business, language, tools))
                    .call()
                    .content();
        } catch (RuntimeException e) {
            throw new ExternalServiceException(ErrorCode.PACK_GENERATION_FAILED, "llm", e);
        }
        return read(answer);
    }

    private ChatOptions options(ChatModel chatModel) {
        String model = packGeneratorProperties.model();
        boolean openAi = chatModel instanceof OpenAiChatModel
                || model.startsWith("gpt-") || model.startsWith("llama") || model.startsWith("qwen");
        return openAi
                ? OpenAiChatOptions.builder().model(model).maxTokens(packGeneratorProperties.maxTokens()).build()
                : GoogleGenAiChatOptions.builder().model(model)
                        .maxOutputTokens(packGeneratorProperties.maxTokens()).build();
    }

    private static String describe(String business, String language, List<Tool> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("BIZNES:\n").append(business).append("\n\n");
        sb.append("AGENT TILI: ").append(language != null && !language.isBlank() ? language : "uz-UZ").append('\n');
        if (tools.isEmpty()) {
            sb.append("\nTOOL'LAR: yo'q. Agent tashqi tizimga yozmaydi — natija faqat suhbatdan "
                    + "yig'iladi, shuning uchun maqsad maydoni ssenariyning o'zida to'ladigan qiymat bo'lsin.\n");
            return sb.toString();
        }
        sb.append("\nAGENTDA BO'LADIGAN TOOL'LAR (API hujjatidan olingan, o'zgartirmang):\n");
        for (Tool tool : tools) {
            sb.append("- ").append(tool.name()).append(" (").append(tool.apiMethod()).append("): ")
                    .append(tool.description() == null ? "" : tool.description()).append('\n');
        }
        sb.append("Bosqichlarda shu nomlarni ishlating va yozuvchi tool chaqirilgandan keyingina "
                + "\"qabul qilindi\" deyilsin.\n");
        return sb.toString();
    }

    /** The model's answer as a pack; a fenced block is unwrapped, anything unreadable is a 502. */
    private static VerticalPack read(String answer) {
        String json = answer == null ? "" : answer.trim();
        if (json.startsWith("```")) {
            int firstBreak = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            json = firstBreak > 0 && lastFence > firstBreak ? json.substring(firstBreak + 1, lastFence).trim() : json;
        }
        try {
            VerticalPack pack = JSON.readValue(json, VerticalPack.class);
            if (pack == null || pack.agent() == null || pack.agent().scenario() == null) {
                throw new ExternalServiceException(ErrorCode.PACK_GENERATION_FAILED, "llm");
            }
            return pack;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Pack generation produced unreadable JSON: {}", e.getMessage());
            throw new ExternalServiceException(ErrorCode.PACK_GENERATION_FAILED, "llm", e);
        }
    }
}
