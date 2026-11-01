package uz.murodjon.robotcallv2.campaign.domain.enums;

/** Whose clock a campaign's calling window is read on. */
public enum TimezoneMode {
    /** The company's own time zone. */
    COMPANY,
    /** The person being called: their zone from the phone number, the company's when it cannot be told. */
    CONTACT,
    /** One zone named on the campaign, whoever is called. */
    FIXED
}
