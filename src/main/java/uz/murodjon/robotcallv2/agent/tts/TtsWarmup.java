package uz.murodjon.robotcallv2.agent.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.DialogProperties;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.company.application.service.CompanyConfigService;
import uz.murodjon.robotcallv2.company.application.service.CompanyService;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.engine.application.service.EngineConfigService;
import uz.murodjon.robotcallv2.engine.domain.entity.EffectiveEngineConfig;
import uz.murodjon.robotcallv2.engine.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.scenario.application.service.ScenarioService;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.shared.dialog.DialogPhrases;
import uz.murodjon.robotcallv2.shared.dialog.Disclosure;
import uz.murodjon.robotcallv2.voice.application.service.TtsVoiceService;
import uz.murodjon.robotcallv2.voice.application.service.VoiceSettingsService;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-synthesizes the lines the agent always says (§11.1 disclosure, greetings, confirmations,
 * the goodbye, the fallback phrases) into the Redis/Memory TTS cache.
 *
 * <p>Warm-up is performed on-demand when a campaign starts (or before calls are dispatched),
 * tailored to that specific campaign's configured language, TTS voice, scenario, and tenant.
 *
 * <p>If a company or campaign runs on {@link PipelineMode#REALTIME}, TTS warm-up is bypassed
 * completely because the live S2S engine generates native audio directly over WebSocket.
 */
@Component
public class TtsWarmup {

    private static final Logger log = LoggerFactory.getLogger(TtsWarmup.class);

    private final TtsProperties ttsProps;
    private final DialogProperties dialogProps;
    private final TtsRouter router;
    private final TtsCache cache;
    private final TtsVoiceService catalog;
    private final CompanyService companyService;
    private final CompanyConfigService companyConfigService;
    private final VoiceSettingsService voiceSettingsService;
    private final ScenarioService scenarioService;
    private final EngineConfigService engineConfigService;
    private final CampaignRepository campaignRepository;

    private final Set<Long> warmedCampaigns = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public TtsWarmup(TtsProperties ttsProps, DialogProperties dialogProps, TtsRouter router,
                     TtsCache cache, TtsVoiceService catalog, CompanyService companyService,
                     CompanyConfigService companyConfigService,
                     VoiceSettingsService voiceSettingsService, ScenarioService scenarioService,
                     EngineConfigService engineConfigService, CampaignRepository campaignRepository) {
        this.ttsProps = ttsProps;
        this.dialogProps = dialogProps;
        this.router = router;
        this.cache = cache;
        this.catalog = catalog;
        this.companyService = companyService;
        this.companyConfigService = companyConfigService;
        this.voiceSettingsService = voiceSettingsService;
        this.scenarioService = scenarioService;
        this.engineConfigService = engineConfigService;
        this.campaignRepository = campaignRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!ttsProps.enabled() || !cache.prewarmEnabled()) {
            return;
        }
        Thread.ofVirtual().name("tts-warmup-startup").start(this::synthesizeFixedLines);
    }

    /**
     * Pre-warms TTS audio specifically for a campaign prior to dialing.
     * Bypassed if the company runs in REALTIME mode.
     */
    public void warmUpForCampaign(long campaignId) {
        if (!ttsProps.enabled() || !cache.prewarmEnabled()) {
            return;
        }
        if (warmedCampaigns.contains(campaignId)) {
            return;
        }
        Thread.ofVirtual().name("tts-warmup-camp-" + campaignId).start(() -> {
            Campaign c = campaignRepository.find(campaignId);
            if (c != null) {
                warmUpForCampaign(c);
            }
        });
    }

    /**
     * Evicts a campaign from the warmed set so it can be re-warmed when updated.
     */
    public void evictCampaign(long campaignId) {
        warmedCampaigns.remove(campaignId);
    }

    /**
     * Synthesizes and caches all static phrases (confirmations, disclosures, fallback phrases)
     * for a given campaign.
     */
    public void warmUpForCampaign(Campaign campaign) {
        if (campaign == null || !ttsProps.enabled() || !cache.prewarmEnabled()) {
            return;
        }
        if (!warmedCampaigns.add(campaign.id())) {
            return;
        }
        EffectiveEngineConfig engineConfig = engineConfigService.findEffectiveByCompanyId(campaign.companyId());
        if (engineConfig.mode() == PipelineMode.REALTIME) {
            log.debug("Skipping TTS warm-up for campaign {} — company {} is in REALTIME mode",
                    campaign.id(), campaign.companyId());
            return;
        }

        Company company = companyService.findById(campaign.companyId());
        String companyName = company != null ? company.name() : null;
        CompanyConfig config = companyConfigService.find(campaign.companyId());
        EffectiveVoiceSettings style = voiceSettingsService.effective(campaign.companyId());

        Scenario scenario = scenarioService.findById(campaign.scenarioId());
        List<Scenario> scenarios = scenario != null ? List.of(scenario) : List.of();

        Set<String> languages = new LinkedHashSet<>();
        if (campaign.defaultLanguage() != null && !campaign.defaultLanguage().isBlank()) {
            languages.add(campaign.defaultLanguage());
        }
        if (campaign.languageVoices() != null) {
            languages.addAll(campaign.languageVoices().keySet());
        }
        if (languages.isEmpty()) {
            languages.add(ttsProps.defaultLanguage());
        }

        int count = 0;
        for (String lang : languages) {
            String voiceId = campaign.voiceFor(lang);
            List<String> lines = linesFor(companyName, lang, scenarios,
                    config != null ? config.disclosureText() : null);

            for (String line : lines) {
                try {
                    router.synthesize(line, lang, voiceId, style);
                    count++;
                } catch (Exception e) {
                    log.debug("Campaign {} warm-up error for {}/{} ('{}'): {}",
                            campaign.id(), lang, voiceId, line, e.getMessage());
                }
            }
        }
        log.info("TTS pre-warmed {} phrase(s) for campaign {} [{}]", count, campaign.id(), campaign.name());
    }

    private void synthesizeFixedLines() {
        int done = 0;
        int failed = 0;
        long startedAt = System.nanoTime();
        for (Warm target : targets()) {
            for (Spoken spoken : linesToWarm(target.language())) {
                try {
                    router.synthesize(spoken.line(), target.language(), target.voiceId(), spoken.style());
                    done++;
                } catch (Exception e) {
                    failed++;
                    log.debug("TTS warm-up failed for {}/{} ('{}'): {}",
                            target.language(), target.voiceId(), spoken.line(), e.getMessage());
                }
            }
        }
        long ms = (System.nanoTime() - startedAt) / 1_000_000;
        if (failed > 0) {
            log.warn("TTS warm-up: {} line(s) cached, {} failed in {} ms", done, failed, ms);
        } else {
            log.info("TTS warm-up: {} line(s) cached in {} ms", done, ms);
        }
    }

    private record Warm(String language, String voiceId) {
    }

    private record Spoken(String line, EffectiveVoiceSettings style) {
    }

    private Set<Spoken> linesToWarm(String language) {
        List<Company> companies = companyService.findAllForWarmup();
        List<Scenario> scenarios = scenarioService.findAllActiveForWarmup();
        if (companies.isEmpty()) {
            return spoken(linesFor(null, language, scenarios, null), EffectiveVoiceSettings.NONE);
        }
        Set<Spoken> lines = new LinkedHashSet<>();
        for (Company company : companies) {
            EffectiveEngineConfig engineConfig = engineConfigService.findEffectiveByCompanyId(company.id());
            if (engineConfig.mode() == PipelineMode.REALTIME) {
                continue;
            }
            CompanyConfig config = companyConfigService.find(company.id());
            lines.addAll(spoken(linesFor(company.name(), language, scenarios,
                            config != null ? config.disclosureText() : null),
                    voiceSettingsService.effective(company.id())));
        }
        return lines;
    }

    private static List<String> linesFor(String companyName, String language, List<Scenario> scenarios,
                                         String companyDisclosureText) {
        List<String> lines = new ArrayList<>(DialogPhrases.forLanguage(language, companyName));
        addIfPresent(lines, Disclosure.resolve(companyDisclosureText, language, companyName));
        for (Scenario scenario : scenarios) {
            if (scenario.definition() != null) {
                addIfPresent(lines, Disclosure.resolve(scenario.definition().disclosureText(), language, companyName));
            }
        }
        return lines;
    }

    private static void addIfPresent(List<String> lines, String line) {
        if (line != null && !line.isBlank()) {
            lines.add(line);
        }
    }

    private static Set<Spoken> spoken(List<String> lines, EffectiveVoiceSettings style) {
        Set<Spoken> out = new LinkedHashSet<>();
        for (String line : lines) {
            out.add(new Spoken(line, style));
        }
        return out;
    }

    private Set<Warm> targets() {
        Set<String> languages = languages();
        Set<Warm> targets = new LinkedHashSet<>();
        for (String language : languages) {
            targets.add(new Warm(language, null));
        }
        for (TtsVoice voice : catalog.findSelectable(null)) {
            if (voice.language() != null && languages.contains(voice.language())) {
                targets.add(new Warm(voice.language(), voice.id()));
            }
        }
        return targets;
    }

    private Set<String> languages() {
        Set<String> languages = new LinkedHashSet<>();
        if (dialogProps.language() != null && !dialogProps.language().isBlank()) {
            languages.add(dialogProps.language());
        }
        if (ttsProps.defaultLanguage() != null && !ttsProps.defaultLanguage().isBlank()) {
            languages.add(ttsProps.defaultLanguage());
        }
        return languages;
    }
}
