package uz.murodjon.robotcallv2.agent.turn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Downloads answered call recordings from the Uysot Open API
 * ({@code POST /v1/open-api/call-history/filter}), as raw material for an Uzbek turn
 * detector: operator-and-customer calls are real Uzbek turn-taking, which no published
 * model was trained on. The recordings then go through
 * {@code scripts/uysot-recordings-to-wav.sh} and {@link TurnDatasetTool}.
 *
 * <p><b>Customer voices.</b> Only use recordings the company has agreed may be used for
 * training. Everything is written under the output directory — keep it in {@code corpus/},
 * which git ignores — and {@code calls.jsonl} carries no phone numbers or names: the call
 * id, when, how long, which direction and which PBX, nothing a person can be found by.
 *
 * <p>The token is read from an environment variable, never from the command line, so it
 * does not end up in shell history. It is sent only to the API host; a recording stored
 * elsewhere is fetched without it.
 *
 * <p>Resumable: a recording already on disk is not fetched again, and its call is not
 * written to {@code calls.jsonl} twice. The API allows 60 requests a minute; every request
 * to its host is spaced to stay under that, and a 429 waits a minute and retries.
 *
 * <p>Usage:
 * <pre>
 *   export UYSOT_OPEN_API_TOKEN=...
 *   java -cp ... uz.murodjon.robotcallv2.agent.turn.UysotCallDownloadTool \
 *        --out=corpus/uysot --from=2026-08-01 [--to=2026-11-03] [--min-talk-seconds=10] \
 *        [--max-calls=5000] [--token-env=UYSOT_OPEN_API_TOKEN] \
 *        [--base-url=https://api.service.app.uysot.uz]
 * </pre>
 */
public final class UysotCallDownloadTool {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static final int PAGE_SIZE = 100;
    /** 60 requests a minute, with a margin. */
    private static final long API_SPACING_MS = 1100;
    private static final int MAX_RETRIES = 5;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final String token;
    private long lastApiRequestAt;

    private UysotCallDownloadTool(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public static void main(String[] args) throws Exception {
        String out = option(args, "--out", null);
        String from = option(args, "--from", null);
        if (out == null || from == null) {
            System.err.println("Usage: UysotCallDownloadTool --out=<dir> --from=<yyyy-mm-dd> [options]");
            System.exit(1);
        }
        String to = option(args, "--to", LocalDate.now(TASHKENT).toString());
        int minTalkSeconds = Integer.parseInt(option(args, "--min-talk-seconds", "10"));
        int maxCalls = Integer.parseInt(option(args, "--max-calls", "5000"));
        String baseUrl = option(args, "--base-url", "https://api.service.app.uysot.uz");
        String token = System.getenv(option(args, "--token-env", "UYSOT_OPEN_API_TOKEN"));
        if (token == null || token.isBlank()) {
            System.err.println("The Open API token environment variable is not set");
            System.exit(1);
        }

        long fromEpoch = LocalDate.parse(from).atStartOfDay(TASHKENT).toEpochSecond();
        long toEpoch = LocalDate.parse(to).plusDays(1).atStartOfDay(TASHKENT).toEpochSecond() - 1;
        new UysotCallDownloadTool(baseUrl, token).run(Path.of(out), fromEpoch, toEpoch, minTalkSeconds, maxCalls);
    }

    private void run(Path outDir, long fromEpoch, long toEpoch, int minTalkSeconds, int maxCalls)
            throws IOException, InterruptedException {
        Path audioDir = outDir.resolve("audio");
        Files.createDirectories(audioDir);
        Path callsFile = outDir.resolve("calls.jsonl");

        int downloaded = 0;
        int skipped = 0;
        int failed = 0;
        int seen = 0;
        int totalPages = 1;
        for (int page = 1; page <= totalPages && seen < maxCalls; page++) {
            JsonNode data = filterPage(page, fromEpoch, toEpoch, minTalkSeconds).path("data");
            totalPages = data.path("totalPages").asInt(0);
            if (page == 1) {
                System.out.printf("%d answered call(s) with a recording in range, %d page(s)%n",
                        data.path("totalElements").asInt(0), totalPages);
            }
            for (JsonNode call : data.path("data")) {
                if (seen >= maxCalls) {
                    break;
                }
                seen++;
                String uuid = call.path("uuid").asText("");
                String url = call.path("recordingUrl").asText("");
                if (uuid.isBlank() || url.isBlank()) {
                    skipped++;
                    continue;
                }
                Path target = audioDir.resolve(safeName(uuid) + extension(url));
                if (Files.exists(target)) {
                    skipped++;
                    continue;
                }
                if (download(url, target)) {
                    appendCall(callsFile, call, target.getFileName().toString());
                    downloaded++;
                } else {
                    failed++;
                }
            }
            System.out.printf("page %d/%d: %d downloaded, %d skipped, %d failed so far%n",
                    page, totalPages, downloaded, skipped, failed);
        }
        System.out.printf("%nDone: %d downloaded, %d skipped (already there or no recording), %d failed%n",
                downloaded, skipped, failed);
        System.out.println("recordings: " + audioDir.toAbsolutePath());
        System.out.println("calls:      " + callsFile.toAbsolutePath());
    }

    private JsonNode filterPage(int page, long fromEpoch, long toEpoch, int minTalkSeconds)
            throws IOException, InterruptedException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page", page);
        body.put("size", PAGE_SIZE);
        body.put("callTimestampFrom", fromEpoch);
        body.put("callTimestampTo", toEpoch);
        body.put("userTalkTimeFrom", minTalkSeconds);
        body.put("contacted", true);
        body.put("hasRecording", true);
        body.putArray("kinds").add("CALL");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/open-api/call-history/filter"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Open-Api-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString(), true);
        if (response.statusCode() != 200) {
            System.err.printf("filter page %d failed (%d): %s%n", page, response.statusCode(), response.body());
            System.exit(1);
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (!root.path("accept").asBoolean(false)) {
            System.err.printf("filter page %d refused: %s%n", page, response.body());
            System.exit(1);
        }
        return root;
    }

    private boolean download(String url, Path target) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        boolean apiHost = uri.getHost() != null && uri.getHost().equals(URI.create(baseUrl).getHost());
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(2)).GET();
        if (apiHost) {
            builder.header("X-Open-Api-Token", token);
        }
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        HttpResponse<Path> response = send(builder.build(), HttpResponse.BodyHandlers.ofFile(partial), apiHost);
        if (response.statusCode() != 200) {
            System.err.printf("  %s: recording download failed (%d)%n", target.getFileName(), response.statusCode());
            Files.deleteIfExists(partial);
            return false;
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /** Spaces requests to the API host, and waits out a 429 before trying again. */
    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler, boolean apiHost)
            throws IOException, InterruptedException {
        for (int attempt = 1; ; attempt++) {
            if (apiHost) {
                long wait = lastApiRequestAt + API_SPACING_MS - System.currentTimeMillis();
                if (wait > 0) {
                    Thread.sleep(wait);
                }
                lastApiRequestAt = System.currentTimeMillis();
            }
            HttpResponse<T> response = client.send(request, handler);
            if (response.statusCode() != 429 || attempt >= MAX_RETRIES) {
                return response;
            }
            System.err.println("  rate limited — waiting a minute");
            Thread.sleep(61_000);
        }
    }

    /** What is kept about a call: enough to group and filter clips, nothing that identifies anyone. */
    private void appendCall(Path callsFile, JsonNode call, String file) throws IOException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uuid", call.path("uuid").asText());
        row.put("file", "audio/" + file);
        row.put("startStamp", call.path("startStamp").asLong());
        row.put("userTalkTime", call.path("userTalkTime").asInt());
        row.put("callDirection", call.path("callDirection").asText(null));
        row.put("callHistoryType", call.path("callHistoryType").asText(null));
        Files.writeString(callsFile, objectMapper.writeValueAsString(row) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** Call ids carry PBX punctuation ({@code UID:1AC_ID:98871PBX:1.179...}) a file name cannot. */
    private static String safeName(String uuid) {
        return uuid.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String extension(String url) {
        String path = URI.create(url).getPath();
        int dot = path == null ? -1 : path.lastIndexOf('.');
        String ext = dot < 0 ? "" : path.substring(dot).toLowerCase();
        return ext.matches("\\.(mp3|wav|ogg|opus|m4a|webm)") ? ext : ".mp3";
    }

    private static String option(String[] args, String name, String fallback) {
        for (String arg : args) {
            if (arg.startsWith(name + "=")) {
                return arg.substring(name.length() + 1);
            }
        }
        return fallback;
    }
}
