package uz.murodjon.robotcallv2.agent.rtp;

/** How the two call legs are laid out in the recorded WAV. */
public enum RecordingMode {
    /** Hard channel separation: Left = Caller, Right = Bot. */
    STEREO,
    /** Natural spatial cross-feed: Left = Caller + 0.35*Bot, Right = Bot + 0.35*Caller. Eliminates dead-ear silence. */
    SPATIAL_STEREO,
    /** Mixed into both channels: (Caller + Bot) / 2. */
    DUAL_MONO
}
