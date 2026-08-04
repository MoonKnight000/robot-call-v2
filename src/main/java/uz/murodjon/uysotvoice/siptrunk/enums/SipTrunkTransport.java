package uz.murodjon.uysotvoice.siptrunk.enums;

/**
 * PJSIP transport a trunk registers over (report #7). Only {@link #UDP} is actually
 * wired to a real Asterisk transport today ({@code [transport-trunk]} in {@code
 * pjsip.conf}) — {@code TCP}/{@code TLS} are modeled for when that transport exists,
 * {@code SipTrunkService} rejects them until then rather than generating PJSIP config
 * that references a transport Asterisk doesn't have.
 */
public enum SipTrunkTransport {
    UDP,
    TCP,
    TLS
}
