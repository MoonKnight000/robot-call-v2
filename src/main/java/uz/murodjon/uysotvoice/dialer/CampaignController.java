package uz.murodjon.uysotvoice.dialer;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Campaign management API (PROJECT.md §5.2, §10). Create a campaign, load its
 * targets, then start it — the dialer picks it up on its next tick.
 */
@RestController
@RequestMapping("/api")
public class CampaignController {

    private final CampaignService service;

    public CampaignController(CampaignService service) {
        this.service = service;
    }

    /**
     * @param dialDays comma-separated weekday names ({@code MONDAY,...}); omit for
     *                 Monday-Friday (§11.2)
     * @param ttsVoice id of a voice from {@code GET /api/tts/voices} (§2.5); omit to
     *                 speak with the configured default. An unknown id is rejected
     * @param dailyCallCap most calls this campaign may place in one day; 0 or omitted for
     *                 unlimited. A spend ceiling — every call costs STT, LLM, TTS and trunk
     *                 minutes, and a campaign with 50 000 targets will spend them all
     */
    public record CreateCampaignRequest(
            String name, String type, String goalPrompt, String defaultLanguage,
            LocalTime dialWindowStart, LocalTime dialWindowEnd, String dialDays,
            int maxAttempts, int retryIntervalHours, int maxConcurrentCalls,
            String ttsVoice, int dailyCallCap) {
    }

    public record AddTargetRequest(long clientId, String phone, String language, JsonNode contextData) {
    }

    @PostMapping("/campaigns")
    public Map<String, Object> create(@RequestBody CreateCampaignRequest r) {
        long id = service.createCampaign(r.name(), r.type(), r.goalPrompt(), r.defaultLanguage(),
                r.dialWindowStart(), r.dialWindowEnd(), r.dialDays(),
                r.maxAttempts(), r.retryIntervalHours(), r.maxConcurrentCalls(), r.ttsVoice(),
                r.dailyCallCap());
        return Map.of("id", id, "status", "DRAFT");
    }

    @GetMapping("/campaigns")
    public List<CampaignRow> list() {
        return service.listCampaigns();
    }

    @GetMapping("/campaigns/{id}")
    public CampaignRow get(@PathVariable long id) {
        return service.getCampaign(id);
    }

    @PostMapping("/campaigns/{id}/targets")
    public Map<String, Object> addTargets(@PathVariable long id, @RequestBody List<AddTargetRequest> targets) {
        List<Long> ids = targets.stream()
                .map(t -> service.addTarget(id, t.clientId(), t.phone(), t.language(), t.contextData()))
                .toList();
        return Map.of("campaignId", id, "added", ids.size(), "targetIds", ids);
    }

    /**
     * Bulk-load targets from a CSV export (§10). Send the file body as {@code text/csv}:
     *
     * <pre>
     * clientId,phone,language,clientName,debtAmount,currency,dueDate,contractNumber
     * 1001,998901234567,uz-UZ,Aziz Karimov,1500000,so'm,2026-07-01,UY-2026-00123
     * </pre>
     *
     * <p>Columns are matched by header name, so the order does not matter and extra columns
     * are reported as ignored. Bad rows are rejected individually — the response lists their
     * line numbers, and everything else is loaded.
     */
    @PostMapping(value = "/campaigns/{id}/targets/csv",
            consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    public CampaignService.CsvImportResult addTargetsCsv(@PathVariable long id, @RequestBody String csv) {
        return service.importTargetsCsv(id, csv);
    }

    @GetMapping("/campaigns/{id}/targets")
    public List<TargetRow> targets(@PathVariable long id) {
        return service.listTargets(id);
    }

    @PostMapping("/campaigns/{id}/start")
    public Map<String, String> start(@PathVariable long id) {
        service.setStatus(id, "ACTIVE");
        return Map.of("campaignId", String.valueOf(id), "status", "ACTIVE");
    }

    @PostMapping("/campaigns/{id}/pause")
    public Map<String, String> pause(@PathVariable long id) {
        service.setStatus(id, "PAUSED");
        return Map.of("campaignId", String.valueOf(id), "status", "PAUSED");
    }

    @PostMapping("/targets/{id}/do-not-call")
    public Map<String, Object> doNotCall(@PathVariable long id) {
        service.doNotCall(id);
        return Map.of("targetId", id, "doNotCall", true);
    }
}
