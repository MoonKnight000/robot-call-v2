package uz.murodjon.robotcallv2.shared.dialog;

/**
 * The telephony transport and media engine that delivers the call audio.
 *
 * <p>Retell-style multi-engine telephony:
 * <ul>
 *   <li>{@link #ASTERISK}: Asterisk 20 ARI + Netty RTP + PJSIP (default, high concurrency, full PBX capabilities)</li>
 *   <li>{@link #LIVEKIT}: LiveKit SIP + WebRTC media plane (ultra-low latency, real-time bi-directional streaming)</li>
 * </ul>
 */
public enum TelephonyEngine {
    ASTERISK,
    LIVEKIT
}
