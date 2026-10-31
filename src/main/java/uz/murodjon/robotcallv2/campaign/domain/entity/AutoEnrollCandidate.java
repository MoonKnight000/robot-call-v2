package uz.murodjon.robotcallv2.campaign.domain.entity;

/**
 * A contact an auto-enroll rule matched: what becomes the target's phone and facts.
 */
public record AutoEnrollCandidate(long contactId, String name, String phone, String address, String tags) {
}
