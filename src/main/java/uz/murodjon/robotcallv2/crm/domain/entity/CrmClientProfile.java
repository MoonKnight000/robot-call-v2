package uz.murodjon.robotcallv2.crm.domain.entity;

import java.util.List;

/**
 * One person as the CRM knows them by phone: their lead and the contracts still in force —
 * what an operator or the agent sees before talking to them.
 *
 * @param lead      the lead the number matched, or {@code null} when none did
 * @param contracts active contracts of the client this phone belongs to, newest first as the CRM lists them
 */
public record CrmClientProfile(String phone, CrmClientSnapshot lead, List<CrmContract> contracts) {
}
