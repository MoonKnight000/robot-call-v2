package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs a {@code CODE} stage's JavaScript over the call's variables and hands back what it
 * returned.
 *
 * <p>The script is the company's, so it runs in GraalJS's {@link SandboxPolicy#CONSTRAINED}
 * sandbox: no Java classes, no files, no network, no threads, nothing printed. It sees the
 * variables as a plain {@code vars} object (a JSON copy, not the call's own map) and returns
 * an object whose fields become outcome values. A script that throws, loops or runs past
 * {@link #TIMEOUT_MS} returns nothing — the call is never held up or broken by it.
 */
@Component
public class StageScriptRunner {

    private static final Logger log = LoggerFactory.getLogger(StageScriptRunner.class);

    /** The caller is listening to silence while this runs. */
    static final long TIMEOUT_MS = 1000;

    /** Bounds a loop the timeout would otherwise have to cut; plenty for arithmetic over a few facts. */
    private static final long STATEMENT_LIMIT = 1_000_000;

    /** More values than this is not a stage computing facts but a script dumping data into the outcome. */
    private static final int MAX_RESULT_FIELDS = 50;

    private static final String VARIABLES_BINDING = "__vars";

    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile Engine engine;

    public StageScriptRunner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param variables what the script reads as {@code vars}: the call's facts and its outcome so far
     * @return the fields of the object the script returned; empty when it returned something
     *         else, failed or ran too long
     */
    public Map<String, Object> run(String channelId, String code, Map<String, Object> variables) {
        if (code == null || code.isBlank()) {
            return Map.of();
        }
        try (Context context = newContext()) {
            String variablesJson = objectMapper.writeValueAsString(variables == null ? Map.of() : variables);
            Future<String> result = executor.submit(() -> evaluate(context, code, variablesJson));
            String json;
            try {
                json = result.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                context.close(true);
                log.warn("[{}] CODE stage script ran past {} ms and was stopped", channelId, TIMEOUT_MS);
                return Map.of();
            }
            return toFields(channelId, json);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof PolyglotException polyglot && polyglot.isResourceExhausted()) {
                log.warn("[{}] CODE stage script hit the statement limit", channelId);
            } else {
                log.warn("[{}] CODE stage script failed: {}", channelId, cause.getMessage());
            }
            return Map.of();
        }
    }

    private static String evaluate(Context context, String code, String variablesJson) {
        context.getBindings("js").putMember(VARIABLES_BINDING, variablesJson);
        // The script is a function body: it reads `vars` and returns an object. JSON in and
        // out, so no polyglot value ever crosses into the host.
        String wrapped = "JSON.stringify((function (vars) {\n" + code + "\n})(JSON.parse(" + VARIABLES_BINDING
                + ")) ?? null)";
        return context.eval(Source.create("js", wrapped)).asString();
    }

    private Map<String, Object> toFields(String channelId, String json) throws Exception {
        if (json == null || !json.trim().startsWith("{")) {
            log.info("[{}] CODE stage script returned no object", channelId);
            return Map.of();
        }
        Map<String, Object> fields = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
        });
        if (fields.size() > MAX_RESULT_FIELDS) {
            log.warn("[{}] CODE stage script returned {} fields; only {} are kept",
                    channelId, fields.size(), MAX_RESULT_FIELDS);
            Map<String, Object> kept = new LinkedHashMap<>();
            fields.entrySet().stream().limit(MAX_RESULT_FIELDS).forEach(e -> kept.put(e.getKey(), e.getValue()));
            return kept;
        }
        return fields;
    }

    private Context newContext() {
        return Context.newBuilder("js")
                .engine(engine())
                .sandbox(SandboxPolicy.CONSTRAINED)
                .out(OutputStream.nullOutputStream())
                .err(OutputStream.nullOutputStream())
                .resourceLimits(ResourceLimits.newBuilder().statementLimit(STATEMENT_LIMIT, null).build())
                .build();
    }

    /** Built on the first CODE stage rather than at startup: most deployments never run one. */
    private Engine engine() {
        Engine current = engine;
        if (current == null) {
            synchronized (this) {
                current = engine;
                if (current == null) {
                    current = Engine.newBuilder("js")
                            .sandbox(SandboxPolicy.CONSTRAINED)
                            .out(OutputStream.nullOutputStream())
                            .err(OutputStream.nullOutputStream())
                            .option("engine.WarnInterpreterOnly", "false")
                            .build();
                    engine = current;
                }
            }
        }
        return current;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
        Engine current = engine;
        if (current != null) {
            current.close(true);
        }
    }
}
