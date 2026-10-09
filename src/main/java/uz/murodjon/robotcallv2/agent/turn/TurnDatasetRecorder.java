package uz.murodjon.robotcallv2.agent.turn;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.PiiRedactor;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Writes one labelled example per speech-gate close: the recognizer's interim at the
 * moment the gate shut, and whether the caller in fact carried on within the reopen grace
 * ({@code continue}) or stopped ({@code end}). That pair is exactly what a text
 * end-of-turn model ({@link TextTurnDetector}) predicts, so a few weeks of calls become the
 * fine-tuning set for an Uzbek one — labelled by the callers themselves, not by hand.
 *
 * <p>Output is JSON Lines, one file per day in Tashkent time:
 * <pre>
 *   {"at":"2026-10-08","language":"uz-UZ","text":"ertaga to'layman lekin","waitMs":1200,"label":"continue"}
 * </pre>
 * No call id and no phone: the set is for training, not for tracing a caller. Text goes
 * through {@link PiiRedactor} first.
 *
 * <p>{@link #record} is called on the RTP consumer thread, so it only offers to a bounded
 * queue; one virtual thread does the file I/O. A full queue drops the example — losing a
 * training row costs nothing, stalling a call's audio does.
 */
@Component
public class TurnDatasetRecorder {

    private static final Logger log = LoggerFactory.getLogger(TurnDatasetRecorder.class);
    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static final int QUEUE_CAPACITY = 10_000;

    private final TurnDatasetProperties turnDatasetProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlockingQueue<Map<String, Object>> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    private volatile boolean running;
    private Thread writer;

    public TurnDatasetRecorder(TurnDatasetProperties turnDatasetProperties) {
        this.turnDatasetProperties = turnDatasetProperties;
    }

    @PostConstruct
    void start() {
        if (!turnDatasetProperties.enabled()) {
            return;
        }
        String directory = turnDatasetProperties.directory();
        if (directory == null || directory.isBlank()) {
            log.warn("voice-agent.turn-dataset.enabled is set but no directory is — not recording");
            return;
        }
        try {
            Files.createDirectories(Path.of(directory));
        } catch (IOException e) {
            log.error("Cannot create turn dataset directory {}: {} — not recording", directory, e.getMessage());
            return;
        }
        running = true;
        writer = Thread.ofVirtual().name("turn-dataset").start(this::drain);
        log.info("Recording end-of-turn examples to {}", directory);
    }

    /**
     * One judged close. Blank text is skipped: a close with no interim in front of it has
     * nothing for a text model to read.
     */
    public void record(String language, String transcript, int waitMs, boolean callerContinued) {
        if (!running || transcript == null || transcript.isBlank()) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("at", LocalDate.now(TASHKENT).toString());
        row.put("language", language);
        row.put("text", PiiRedactor.redact(transcript.strip()));
        row.put("waitMs", waitMs);
        row.put("label", callerContinued ? "continue" : "end");
        queue.offer(row);
    }

    private void drain() {
        while (running || !queue.isEmpty()) {
            try {
                Map<String, Object> row = queue.poll(1, TimeUnit.SECONDS);
                if (row != null) {
                    append(row);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void append(Map<String, Object> row) {
        Path file = Path.of(turnDatasetProperties.directory(), "turn-dataset-" + row.get("at") + ".jsonl");
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            out.write(objectMapper.writeValueAsString(row));
            out.write('\n');
        } catch (IOException e) {
            log.warn("Turn dataset write to {} failed: {}", file, e.getMessage());
        }
    }

    @PreDestroy
    void stop() throws InterruptedException {
        running = false;
        if (writer != null) {
            writer.join(TimeUnit.SECONDS.toMillis(5));
        }
    }
}
