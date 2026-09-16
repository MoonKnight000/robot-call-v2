package uz.murodjon.robotcallv2.agent.ari;

/**
 * Where a call is in its life on this instance, as {@link CallSessionRegistry} tracks it.
 * A channel with no phase at all is either not ours or already torn down.
 *
 * <pre>
 *   (none) ──StasisStart──▶ SETTING_UP ──setup registers──▶ ACTIVE ──claim──▶ ENDING ──teardown done──▶ (none)
 *                              │                              ▲
 *                          StasisEnd                          │ setup registers, and tears down itself
 *                              ▼                              │
 *                        END_REQUESTED ───────────────────────┘
 * </pre>
 *
 * <p>Every move is one atomic step on the channel's entry, so exactly one thread — the
 * StasisEnd lane, the setup thread, the ghost reaper or the shutdown — ever tears a call down.
 */
enum CallPhase {

    /** StasisStart arrived; the media is being set up and there is no session yet. */
    SETTING_UP,
    /** StasisEnd arrived while the setup was still running; the setup thread tears the call down. */
    END_REQUESTED,
    /** The session is registered and the call is live. */
    ACTIVE,
    /** One thread has claimed the call and is tearing it down. */
    ENDING
}
