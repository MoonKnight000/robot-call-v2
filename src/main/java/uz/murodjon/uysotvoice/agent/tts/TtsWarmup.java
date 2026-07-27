package uz.murodjon.uysotvoice.agent.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uz.murodjon.uysotvoice.agent.dialog.DialogProperties;
import uz.murodjon.uysotvoice.shared.dialog.DialogPhrases;

import java.util.LinkedHashSet;
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
    private final TtsVoiceCatalog catalog;

    public TtsWarmup(TtsProperties ttsProps, DialogProperties dialogProps, TtsRouter router,
                     TtsCache cache, TtsVoiceCatalog catalog) {
        this.ttsProps = ttsProps;
        this.dialogProps = dialogProps;
        this.router = router;
        this.cache = cache;
        this.catalog = catalog;
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
            for (String line : DialogPhrases.forLanguage(target.language())) {
                try {
                    router.synthesize(line, target.language(), target.voiceId());
                    done++;
                } catch (Exception e) {
                    failed++;
                    log.debug("TTS warm-up failed for {}/{} ('{}'): {}",
                            target.language(), target.voiceId(), line, e.getMessage());
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
        for (TtsProperties.Voice voice : catalog.all()) {
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
