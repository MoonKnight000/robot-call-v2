package uz.murodjon.uysotvoice.agent.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.dialog.DialogProperties;
import uz.murodjon.uysotvoice.company.dto.Company;
import uz.murodjon.uysotvoice.company.dto.CompanyConfig;
import uz.murodjon.uysotvoice.company.service.CompanyConfigService;
import uz.murodjon.uysotvoice.company.service.CompanyService;
import uz.murodjon.uysotvoice.scenario.dto.Scenario;
import uz.murodjon.uysotvoice.scenario.service.ScenarioService;
import uz.murodjon.uysotvoice.shared.dialog.DialogPhrases;
import uz.murodjon.uysotvoice.shared.dialog.Disclosure;
import uz.murodjon.uysotvoice.voice.dto.EffectiveVoiceSettings;
import uz.murodjon.uysotvoice.voice.dto.TtsVoice;
import uz.murodjon.uysotvoice.voice.service.TtsVoiceService;
import uz.murodjon.uysotvoice.voice.service.VoiceSettingsService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pre-synthesizes the lines the agent always says (§11.1 disclosure, the goodbye, the
 * "say that again" fallback) once the app is up.
 *
 * <p>Two things this buys. The obvious one is money: with a shared Redis cache these
 * lines are bought once for the whole fleet, not once per instance per deploy. The one
 * that shows up on a call is latency — the disclosure is the very first thing a caller
 * hears, and on a cold instance it was paying for a TLS handshake plus a full synthesis
 * round trip while the line was already open.
 *
 * <p>Runs on its own thread and never fails startup: a warm-up error only means the
 * first call pays what it always used to.
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

    public TtsWarmup(TtsProperties ttsProps, DialogProperties dialogProps, TtsRouter router,
                     TtsCache cache, TtsVoiceService catalog, CompanyService companyService,
                     CompanyConfigService companyConfigService,
                     VoiceSettingsService voiceSettingsService, ScenarioService scenarioService) {
        this.ttsProps = ttsProps;
        this.dialogProps = dialogProps;
        this.router = router;
        this.cache = cache;
        this.catalog = catalog;
        this.companyService = companyService;
        this.companyConfigService = companyConfigService;
        this.voiceSettingsService = voiceSettingsService;
        this.scenarioService = scenarioService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!ttsProps.enabled() || !cache.prewarmEnabled()) {
            return;
        }
        Thread.ofVirtual().name("tts-warmup").start(this::synthesizeFixedLines);
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
            log.warn("TTS warm-up: {} line(s) cached, {} failed in {} ms "
                    + "(the first call will synthesize them itself)", done, failed, ms);
        } else {
            log.info("TTS warm-up: {} line(s) cached in {} ms", done, ms);
        }
    }

    /** One language, optionally in one selectable voice ({@code null} = default routing). */
    private record Warm(String language, String voiceId) {
    }

    /** One line as one company will actually hear it — the same text at another speed is other audio. */
    private record Spoken(String line, EffectiveVoiceSettings style) {
    }

    /**
     * The fixed lines to warm for one language, each paired with the voice settings it
     * will be spoken with.
     *
     * <p>Both halves matter for a hit. Only the §11.1 disclosure names a company, so the
     * lines themselves collapse to "the shared ones, plus one disclosure per tenant". The
     * settings do not collapse: {@code speed}/{@code pitch}/{@code provider} are part of
     * the cache key ({@code TtsRouter}), so warming everything at the process defaults
     * left every company that had tuned its voice paying for the whole set on its first
     * call — the one warm-up exists to spare.
     */
    private Set<Spoken> linesToWarm(String language) {
        List<Company> companies = companyService.findAllForWarmup();
        List<Scenario> scenarios = scenarioService.findAllActiveForWarmup();
        if (companies.isEmpty()) {
            return spoken(linesFor(null, language, scenarios, null), EffectiveVoiceSettings.NONE);
        }
        Set<Spoken> lines = new LinkedHashSet<>();
        for (Company company : companies) {
            CompanyConfig config = companyConfigService.find(company.id());
            lines.addAll(spoken(linesFor(company.name(), language, scenarios,
                            config != null ? config.disclosureText() : null),
                    voiceSettingsService.effective(company.id())));
        }
        return lines;
    }

    /**
     * The fixed lines one company speaks in one language: the platform's own, plus every
     * §11.1 disclosure that may replace the platform's — the company's own wording and
     * that of any scenario overriding it.
     *
     * <p>All of them, because which one a given call opens with depends on the scenario
     * it runs, and the disclosure is precisely the line worth having ready: it plays
     * before the caller has said anything, so there is no turn in flight for its
     * synthesis to hide behind.
     */
    private static List<String> linesFor(String companyName, String language, List<Scenario> scenarios,
                                         String companyDisclosureText) {
        List<String> lines = new ArrayList<>(DialogPhrases.forLanguage(language, companyName));
        addIfPresent(lines, Disclosure.resolve(companyDisclosureText, language, companyName));
        for (Scenario scenario : scenarios) {
            addIfPresent(lines, Disclosure.resolve(scenario.definition().disclosureText(), language, companyName));
        }
        return lines;
    }

    private static void addIfPresent(List<String> lines, String line) {
        if (line != null) {
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

    /**
     * What to pre-synthesize: the default routing for the languages this deployment
     * dials in, plus the selectable voices for those same languages — a campaign that
     * picked a voice gets different audio for the same fixed lines, so warming only the
     * default routing would leave it paying for them on its first call.
     *
     * <p>The language filter applies to voices too: warming a language nobody calls in
     * would buy audio that is never played, and the catalog may well offer voices for
     * languages this deployment does not dial.
     */
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
