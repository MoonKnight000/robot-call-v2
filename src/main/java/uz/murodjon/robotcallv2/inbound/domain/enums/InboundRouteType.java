package uz.murodjon.robotcallv2.inbound.domain.enums;

/**
 * Type of inbound call handling / routing target in the Virtual PBX.
 */
public enum InboundRouteType {
    /** Route to AI Voice Agent (Scenario). */
    SCENARIO,
    /** Route to human operator call queue. */
    OPERATOR_QUEUE,
    /** Route to a specific internal SIP extension (e.g. 101, 102). */
    EXTENSION,
    /** Forward to an external mobile or landline phone number. */
    EXTERNAL_NUMBER,
    /** Sticky agent routing: route directly to the client's assigned manager from CRM. */
    STICKY_AGENT,
    /** Interactive Voice Response (IVR) menu with DTMF or voice choices. */
    IVR_MENU,
    /** Send caller directly to Voicemail. */
    VOICEMAIL
}
