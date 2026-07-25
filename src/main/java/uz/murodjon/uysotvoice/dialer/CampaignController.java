package uz.murodjon.uysotvoice.dialer;

import com.fasterxml.jackson.databind.JsonNode;
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

    public record CreateCampaignRequest(
            String name, String type, String goalPrompt, String defaultLanguage,
            LocalTime dialWindowStart, LocalTime dialWindowEnd,
            int maxAttempts, int retryIntervalHours, int maxConcurrentCalls) {
    }

    public record AddTargetRequest(long clientId, String phone, String language, JsonNode contextData) {
    }

    @PostMapping("/campaigns")
    public Map<String, Object> create(@RequestBody CreateCampaignRequest r) {
        long id = service.createCampaign(r.name(), r.type(), r.goalPrompt(), r.defaultLanguage(),
                r.dialWindowStart(), r.dialWindowEnd(), r.maxAttempts(), r.retryIntervalHours(), r.maxConcurrentCalls());
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
