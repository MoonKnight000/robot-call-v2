package uz.murodjon.robotcallv2.chat.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.role.domain.enums.Feature;
import uz.murodjon.robotcallv2.chat.application.dto.RegisterWebhookRequest;
import uz.murodjon.robotcallv2.chat.application.dto.SaveTelegramBotRequest;
import uz.murodjon.robotcallv2.chat.application.dto.TelegramBotRow;
import uz.murodjon.robotcallv2.chat.application.port.input.TelegramUseCase;
import uz.murodjon.robotcallv2.chat.application.port.output.ChatSessionRepository;
import uz.murodjon.robotcallv2.chat.application.port.output.TelegramApiPort;
import uz.murodjon.robotcallv2.chat.application.port.output.TelegramBotRepository;
import uz.murodjon.robotcallv2.chat.domain.entity.*;
import uz.murodjon.robotcallv2.chat.domain.service.TelegramUpdates;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A company's Telegram bot, answering with one of its agents
 * (UNIVERSAL-OPERATOR-PLAN E.4).
 *
 * <p>The channel only: who may write, how the conversation is found, and how the answer is
 * delivered. The conversation is {@link ChatConversations} — the same one the widget chat
 * uses, which is the same turn a call runs. A person writing to the bot is talking to the
 * agent the phone would give them.
 *
 * <p>An update is answered over Telegram, never in the response: Telegram is told only
 * whether the update was received. That is also why so little of what can go wrong here
 * throws — an update Telegram does not get a 2xx for is an update Telegram sends again, and
 * a model that was briefly down would become a bot repeating itself for hours.
 */
@Service
public class TelegramService implements TelegramUseCase {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    /** The path the bot's updates arrive on; the operator's own address is prefixed to it. */
    private static final String WEBHOOK_PATH = "/api/public/telegram/";

    /** Said to the person when the agent cannot answer at all — their message was read, not ignored. */
    private static final String UNAVAILABLE_UZ =
            "Kechirasiz, hozir javob bera olmayapman. Birozdan keyin qayta yozing.";
    private static final String UNAVAILABLE_RU =
            "Извините, сейчас не могу ответить. Напишите, пожалуйста, чуть позже.";

    private static final String SHARE_CONTACT_UZ = "📱 Raqamimni yuborish";
    private static final String SHARE_CONTACT_RU = "📱 Отправить мой номер";

    private final ChatConversations conversations;
    private final ChatSessionRepository chatRepository;
    private final TelegramBotRepository botRepository;
    private final TelegramApiPort telegramApi;
    private final SecretUseCase secretUseCase;
    private final AiAgentUseCase aiAgentUseCase;
    private final AuditUseCase auditUseCase;
    private final CompanyUseCase companyUseCase;

    public TelegramService(ChatConversations conversations, ChatSessionRepository chatRepository,
                           TelegramBotRepository botRepository, TelegramApiPort telegramApi,
                           SecretUseCase secretUseCase, AiAgentUseCase aiAgentUseCase, AuditUseCase auditUseCase,
                           CompanyUseCase companyUseCase) {
        this.conversations = conversations;
        this.chatRepository = chatRepository;
        this.botRepository = botRepository;
        this.telegramApi = telegramApi;
        this.secretUseCase = secretUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.auditUseCase = auditUseCase;
        this.companyUseCase = companyUseCase;
    }

    @Override
    public List<TelegramBotRow> findBots(long companyId) {
        return botRepository.findByCompanyId(companyId).stream().map(bot -> toRow(companyId, bot)).toList();
    }

    @Override
    public TelegramBotRow createBot(long companyId, SaveTelegramBotRequest request) {
        aiAgentUseCase.requireAgent(companyId, request.agentId());
        requireSecret(companyId, request.tokenSecretKey());
        TelegramBot saved = botRepository.save(new TelegramBot(null, companyId, request.agentId(),
                request.name().trim(), request.tokenSecretKey().trim(), newWebhookSecret(), request.language(),
                null, true, null, null));
        auditUseCase.record(companyId, "TELEGRAM_BOT_CREATE", "telegram_bot",
                String.valueOf(saved.id()), saved.name());
        return toRow(companyId, saved);
    }

    @Override
    public TelegramBotRow registerWebhook(long companyId, long botId, RegisterWebhookRequest request) {
        TelegramBot bot = requireBot(companyId, botId);
        String base = request.publicBaseUrl().trim().replaceAll("/+$", "");
        // The address Telegram will be told to post to has to be one the internet can reach,
        // and the guard is the same one every outbound address in this application passes.
        if (PublicUrlGuard.parsePublic(base) == null || !base.startsWith("https://")) {
            throw new ConflictException(ErrorCode.TELEGRAM_WEBHOOK_URL_INVALID, base);
        }
        String url = base + WEBHOOK_PATH + bot.id();
        telegramApi.registerWebhook(token(bot), url, bot.webhookSecret());
        TelegramBot saved = botRepository.save(bot.withWebhookUrl(url));
        auditUseCase.record(companyId, "TELEGRAM_WEBHOOK_REGISTER", "telegram_bot", String.valueOf(botId), url);
        return toRow(companyId, saved);
    }

    @Override
    public void deleteBot(long companyId, long botId) {
        TelegramBot bot = requireBot(companyId, botId);
        try {
            telegramApi.removeWebhook(token(bot));
        } catch (RuntimeException e) {
            // The row goes either way: a bot this company has deleted must not keep an agent
            // answering, and a webhook pointing at a bot that no longer exists answers 404.
            log.warn("Telegram webhook for bot {} could not be removed: {}", botId, e.getMessage());
        }
        botRepository.deleteByCompanyIdAndId(companyId, botId);
        auditUseCase.record(companyId, "TELEGRAM_BOT_DELETE", "telegram_bot", String.valueOf(botId), bot.name());
    }

    @Override
    public void handleUpdate(long botId, String secretToken, Map<String, Object> update) {
        TelegramBot bot = botRepository.findById(botId);
        if (bot == null || !bot.enabled()) {
            throw new NotFoundException(ErrorCode.TELEGRAM_BOT_NOT_FOUND, botId);
        }
        // Constant-time: the header is the only thing that makes this update Telegram's, and
        // a check that returns sooner for a nearly-right secret is a check that can be guessed.
        if (!MessageDigest.isEqual(bot.webhookSecret().getBytes(StandardCharsets.UTF_8),
                (secretToken == null ? "" : secretToken).getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException(ErrorCode.TELEGRAM_SECRET_MISMATCH);
        }
        // Acknowledged and dropped, not refused: Telegram resends anything it does not get a 2xx for.
        if (!companyUseCase.isFeatureAvailable(bot.companyId(), Feature.CHAT_AGENT)) {
            log.info("[telegram bot {}] update ignored: company {} is closed or has the chat agent switched off", botId, bot.companyId());
            return;
        }
        TelegramUpdate message = TelegramUpdates.read(update);
        if (message == null) {
            return; // somebody joined, sent a photo, edited something: nothing to answer
        }
        try {
            answer(bot, message);
        } catch (RuntimeException e) {
            // Acknowledged all the same. Telegram resends anything it does not get a 2xx for,
            // and a minute of a model being down would otherwise become an afternoon of the
            // same message arriving again and again.
            log.warn("[telegram bot {}] chat {} could not be answered: {}", botId, message.chatId(), e.getMessage());
            trySend(bot, message.chatId(), unavailableLine(bot));
        }
    }

    /** Continues the person's open conversation, or starts one when they have none. */
    private void answer(TelegramBot bot, TelegramUpdate message) {
        ChatSession open = chatRepository.findOpenByTelegram(bot.id(), message.chatId());
        if (open != null && conversations.isOver(open)) {
            // Quiet for too long or out of messages: closed rather than refused, and the next
            // line starts a fresh conversation. A person writing to a bot should never be told
            // to "start a new session" — they just wrote.
            chatRepository.save(open.afterTurn(open.stage(), open.outcome(), open.disposition(), true, 0,
                    open.lastMessageAt()));
            open = null;
        }
        if (open == null) {
            StartedChat started = conversations.start(ChatSession.startingOnTelegram(conversations.newSessionKey(),
                    bot.companyId(), bot.aiAgentId(), bot.id(), message.chatId(), bot.language(),
                    conversations.firstStageOf(bot.companyId(), bot.aiAgentId())));
            open = started.session();
            if (message.contactPhone() == null && !conversations.hasPhone(open)) {
                // One tap shares the person's number: the agent then knows their contracts and
                // what they said on the phone, and the chat is written onto their CRM lead.
                telegramApi.sendMessageAskingContact(token(bot), message.chatId(), started.greeting(),
                        shareContactLabel(bot));
            } else {
                telegramApi.sendMessage(token(bot), message.chatId(), started.greeting());
            }
        }
        if (message.contactPhone() != null) {
            open = conversations.attachPhone(open, message.contactPhone());
        }
        ChatTurn turn = conversations.answer(open, message.text());
        // Null while a person from the company answers instead: their line goes out when they write it.
        if (turn.reply() != null) {
            telegramApi.sendMessage(token(bot), message.chatId(), turn.reply());
        }
    }

    /** Delivers a line an operator wrote into a Telegram conversation they took over. */
    void deliverOperatorMessage(ChatSession session, String text) {
        TelegramBot bot = botRepository.findById(session.telegramBotId());
        if (bot == null) {
            throw new NotFoundException(ErrorCode.TELEGRAM_BOT_NOT_FOUND, session.telegramBotId());
        }
        telegramApi.sendMessage(token(bot), session.telegramChatId(), text);
    }

    private void trySend(TelegramBot bot, String chatId, String text) {
        try {
            telegramApi.sendMessage(token(bot), chatId, text);
        } catch (RuntimeException e) {
            log.warn("[telegram bot {}] could not even apologise to chat {}: {}", bot.id(), chatId, e.getMessage());
        }
    }

    private static String shareContactLabel(TelegramBot bot) {
        return bot.language() != null && bot.language().toLowerCase().startsWith("ru")
                ? SHARE_CONTACT_RU : SHARE_CONTACT_UZ;
    }

    private static String unavailableLine(TelegramBot bot) {
        return bot.language() != null && bot.language().toLowerCase().startsWith("ru")
                ? UNAVAILABLE_RU : UNAVAILABLE_UZ;
    }

    /** Read at the moment it is needed and held nowhere: the token is the bot. */
    private String token(TelegramBot bot) {
        String token = secretUseCase.findDecryptedSecrets(bot.companyId()).get(bot.tokenSecretKey());
        if (token == null || token.isBlank()) {
            throw new ConflictException(ErrorCode.TELEGRAM_TOKEN_MISSING, bot.tokenSecretKey());
        }
        return token;
    }

    private void requireSecret(long companyId, String key) {
        if (!secretUseCase.findDecryptedSecrets(companyId).containsKey(key.trim())) {
            throw new ConflictException(ErrorCode.TELEGRAM_TOKEN_MISSING, key.trim());
        }
    }

    private TelegramBot requireBot(long companyId, long botId) {
        TelegramBot bot = botRepository.findByCompanyIdAndId(companyId, botId);
        if (bot == null) {
            throw new NotFoundException(ErrorCode.TELEGRAM_BOT_NOT_FOUND, botId);
        }
        return bot;
    }

    private static String newWebhookSecret() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private TelegramBotRow toRow(long companyId, TelegramBot bot) {
        AiAgent agent = aiAgentUseCase.findAgent(companyId, bot.aiAgentId());
        return new TelegramBotRow(bot.id(), bot.aiAgentId(), agent != null ? agent.name() : null, bot.name(),
                bot.tokenSecretKey(), bot.language(), bot.enabled(), bot.webhookUrl(), bot.createdAt());
    }
}
