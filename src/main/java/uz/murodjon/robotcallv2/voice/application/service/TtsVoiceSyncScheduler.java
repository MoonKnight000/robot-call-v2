package uz.murodjon.robotcallv2.voice.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.tts.ProviderVoice;
import uz.murodjon.robotcallv2.agent.tts.TtsProvider;
import uz.murodjon.robotcallv2.agent.tts.TtsProviderSelector;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;
import uz.murodjon.robotcallv2.voice.application.port.output.TtsVoiceRepository;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;
import uz.murodjon.robotcallv2.voice.infrastructure.config.TtsVoiceSyncProperties;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Keeps the voice catalog in step with the providers: once a day every registered TTS
 * provider is asked for its voices ({@link TtsProvider#listVoices()}) and the answer is filed
 * into {@code tts_voice}, so a voice the vendor adds shows up in the agent form without a
 * seed change, and one it drops stops being offered.
 *
 * <p>A provider with no list API answers with nothing and its seeded voices stay as they are.
 * A failed or empty answer is skipped, not applied — otherwise one bad response would switch
 * off every voice that provider has.
 */
@Component
public class TtsVoiceSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(TtsVoiceSyncScheduler.class);

    /** tts_voice.id and tts_voice.name column widths. */
    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 100;

    private final TtsProviderSelector ttsProviderSelector;
    private final TtsVoiceRepository ttsVoiceRepository;
    private final TtsVoiceSyncProperties ttsVoiceSyncProperties;
    private final ClusterJobLock clusterJobLock;
    private final Clock clock;

    public TtsVoiceSyncScheduler(TtsProviderSelector ttsProviderSelector,
                                 TtsVoiceRepository ttsVoiceRepository,
                                 TtsVoiceSyncProperties ttsVoiceSyncProperties,
                                 ClusterJobLock clusterJobLock,
                                 Clock clock) {
        this.ttsProviderSelector = ttsProviderSelector;
        this.ttsVoiceRepository = ttsVoiceRepository;
        this.ttsVoiceSyncProperties = ttsVoiceSyncProperties;
        this.clusterJobLock = clusterJobLock;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${voice-agent.voice-sync.initial-delay-ms:30000}",
               fixedDelayString = "${voice-agent.voice-sync.interval-ms:86400000}")
    public void syncVoices() {
        if (!ttsVoiceSyncProperties.enabled()) {
            return;
        }
        clusterJobLock.runExclusively("voice.tts-voice-sync", this::syncAllProviders);
    }

    private void syncAllProviders() {
        for (String providerName : ttsProviderSelector.names()) {
            TtsProvider provider = ttsProviderSelector.tryFind(providerName);
            List<ProviderVoice> listed;
            try {
                listed = provider.listVoices();
            } catch (RuntimeException e) {
                log.warn("Voice sync for '{}' skipped, list failed: {}", providerName, e.getMessage());
                continue;
            }
            if (listed.isEmpty()) {
                continue;
            }
            List<TtsVoice> voices = toCatalog(providerName, listed);
            ttsVoiceRepository.replaceSyncedVoices(providerName, voices, clock.instant());
            log.info("Voice sync for '{}': {} voice(s), {} catalog row(s)", providerName, listed.size(), voices.size());
        }
    }

    /**
     * One row per voice and configured language the voice is offered for. The id is
     * {@code <provider>-tts-<name>-<lang>}: "-tts-" keeps it clear of the realtime engines'
     * seeded ids ({@code gemini-kore-uz} is a Gemini Live voice), and it is exactly the id
     * the hand-seeded Gemini TTS rows already have, so the sync takes those over instead of
     * listing each voice twice.
     */
    private List<TtsVoice> toCatalog(String providerName, List<ProviderVoice> listed) {
        List<TtsVoice> voices = new ArrayList<>();
        for (ProviderVoice voice : listed) {
            for (String language : ttsVoiceSyncProperties.languages()) {
                String code = language.split("-")[0].toLowerCase(Locale.ROOT);
                if (voice.languages().isEmpty() || voice.languages().contains(code)) {
                    // Lower-cased: the catalog looks ids up lower-cased; name keeps the vendor's case.
                    String id = (providerName + "-tts-" + voice.name() + "-" + code).toLowerCase(Locale.ROOT);
                    if (id.length() > MAX_ID_LENGTH || voice.name().length() > MAX_NAME_LENGTH) {
                        log.warn("Voice sync for '{}': voice '{}' skipped, its id does not fit the catalog",
                                providerName, voice.name());
                        continue;
                    }
                    voices.add(new TtsVoice(id, providerName, language, voice.name(), voice.label(), null));
                }
            }
        }
        return voices;
    }
}
