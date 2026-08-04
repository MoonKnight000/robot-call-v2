package uz.murodjon.uysotvoice.storage.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Serves every file this app ever hands to a browser — company logos, user avatars,
 * call recordings — by streaming it out of MinIO itself, so the frontend never needs a
 * MinIO URL (which, behind Docker, is an internal hostname the browser can't resolve;
 * see {@code ObjectStorageService}'s javadoc).
 *
 * <p>Authenticated the same as everything else under {@code /api/**} — a plain {@code
 * <img src>}/{@code <audio src>} can't set {@code X-Api-Key}, so callers fetch this as a
 * blob and turn it into an object URL instead (already the pattern {@code
 * GET /api/reports/calls/{id}/recording} uses on the frontend).
 */
@RequestMapping("/api")
public interface FileController {

    @GetMapping("/files/{id}")
    ResponseEntity<Resource> download(@PathVariable long id);
}
