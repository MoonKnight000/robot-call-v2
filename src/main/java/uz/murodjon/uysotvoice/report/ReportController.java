package uz.murodjon.uysotvoice.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uz.murodjon.uysotvoice.audit.AuditRow;
import uz.murodjon.uysotvoice.audit.AuditService;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Reporting API (PROJECT.md §10 Bosqich 12): what the campaigns did, call by call.
 *
 * <p>Read-only, so a viewer-scoped API key is enough (see
 * {@code uz.murodjon.uysotvoice.config.SecurityConfig}) — reading results does not need the
 * key that can dial subscribers.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    /** Local recordings are stored with this prefix to distinguish them from object URLs. */
    private static final String FILE_PREFIX = "file:";

    private final ReportRepository reports;
    private final AuditService audit;

    public ReportController(ReportRepository reports, AuditService audit) {
        this.reports = reports;
        this.audit = audit;
    }

    /** Aggregate outcome of one campaign: statuses, dispositions, promise rate. */
    @GetMapping("/campaigns/{id}")
    public CampaignStats campaign(@PathVariable long id) {
        CampaignStats stats = reports.campaignStats(id);
        if (stats == null) {
            throw new ResponseStatusException(NOT_FOUND, "No campaign " + id);
        }
        return stats;
    }

    /** Calls of one campaign, newest first. */
    @GetMapping("/campaigns/{id}/calls")
    public List<CallRow> campaignCalls(@PathVariable long id,
                                       @RequestParam(defaultValue = "50") int limit,
                                       @RequestParam(defaultValue = "0") int offset) {
        return reports.callsOfCampaign(id, cap(limit), Math.max(0, offset));
    }

    /** Calls across every campaign, newest first — the "what just happened" view. */
    @GetMapping("/calls")
    public List<CallRow> calls(@RequestParam(defaultValue = "50") int limit,
                               @RequestParam(defaultValue = "0") int offset) {
        return reports.recentCalls(cap(limit), Math.max(0, offset));
    }

    /** One call with its full transcript. */
    @GetMapping("/calls/{callId}")
    public CallDetail call(@PathVariable long callId) {
        CallDetail detail = reports.callDetail(callId);
        if (detail == null) {
            throw new ResponseStatusException(NOT_FOUND, "No call " + callId);
        }
        return detail;
    }

    /**
     * The call recording (§11.3 — a recording is evidence in a dispute, so it has to be
     * retrievable without shell access to the box).
     *
     * <p>Recordings live in one of two places depending on whether object storage is on, so
     * this serves a local file directly and redirects to the object store otherwise.
     */
    @GetMapping("/calls/{callId}/recording")
    public ResponseEntity<Resource> recording(@PathVariable long callId) {
        String url = reports.recordingUrl(callId);
        if (url == null || url.isBlank()) {
            throw new ResponseStatusException(NOT_FOUND, "No recording for call " + callId);
        }
        if (!url.startsWith(FILE_PREFIX)) {
            // Object storage owns it; hand the caller a redirect rather than proxying
            // megabytes of audio through this service.
            return ResponseEntity.status(302).location(URI.create(url)).build();
        }
        Path path = Path.of(url.substring(FILE_PREFIX.length()));
        if (!Files.isReadable(path)) {
            // The retention sweep (§11.3) deletes recordings on schedule, so a missing file
            // is an ordinary outcome, not a fault.
            log.info("Recording for call {} is no longer on disk: {}", callId, path);
            throw new ResponseStatusException(NOT_FOUND, "Recording for call " + callId + " is gone");
        }
        audit.record("RECORDING_DOWNLOAD", "call", String.valueOf(callId), path.getFileName().toString());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"call-" + callId + ".wav\"")
                .body(new FileSystemResource(path));
    }

    /** Who changed what through the API (§11). */
    @GetMapping("/audit")
    public List<AuditRow> auditLog(@RequestParam(defaultValue = "100") int limit) {
        return audit.recent(limit);
    }

    /** Bound a page so one request cannot ask for a whole campaign's history at once. */
    private static int cap(int limit) {
        return Math.min(Math.max(1, limit), 500);
    }
}
