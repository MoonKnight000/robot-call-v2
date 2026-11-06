package uz.murodjon.robotcallv2.crm.domain.entity;

import java.time.Instant;

/**
 * Where an unfinished sync pass stopped: the pass reads changes from {@code since} and its
 * next page is {@code pageToken}.
 *
 * @param since {@code null} — the pass reads every contact
 */
public record CrmContactSyncResume(Instant since, String pageToken) {
}
