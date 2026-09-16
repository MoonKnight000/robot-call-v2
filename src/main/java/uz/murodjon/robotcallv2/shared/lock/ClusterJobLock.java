package uz.murodjon.robotcallv2.shared.lock;

/**
 * Keeps a scheduled job to one instance at a time across every node that shares the
 * database. A job that finds its lock held elsewhere is skipped for this tick, not queued:
 * the instance running it is doing the same work.
 *
 * <p>Mutual exclusion, not "once per interval": two nodes whose ticks do not overlap both
 * run the job, one after the other. Every job guarded by this reads what it has to do from
 * shared state (a claim, a status, a sent-at stamp), so the second run finds the first one's
 * work already done.
 */
@FunctionalInterface
public interface ClusterJobLock {

    /**
     * Runs {@code job} on this thread unless another instance holds the lock named
     * {@code jobName}. An exception from the job reaches the caller, with the lock released.
     *
     * @return true when the job ran here; false when it was skipped — held elsewhere, or the
     *         lock could not be taken at all (database unreachable)
     */
    boolean runExclusively(String jobName, Runnable job);
}
