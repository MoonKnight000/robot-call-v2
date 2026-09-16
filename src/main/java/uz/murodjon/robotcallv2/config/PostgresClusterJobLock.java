package uz.murodjon.robotcallv2.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * {@link ClusterJobLock} on a PostgreSQL session-level advisory lock.
 *
 * <p>The lock is taken and released on one connection borrowed for the job's whole run and
 * used for nothing else. The job's own queries go through other pooled connections and
 * commit on their own, which is why this is a session lock and not a transaction one — that
 * would end with the first commit. An instance that dies mid-job takes its session with it,
 * and Postgres frees the lock.
 *
 * <p>Costs one pooled connection for as long as a guarded job runs.
 */
@Component
public class PostgresClusterJobLock implements ClusterJobLock {

    private static final Logger log = LoggerFactory.getLogger(PostgresClusterJobLock.class);

    /** Keeps these keys apart from any other advisory lock taken on the same database. */
    private static final String KEY_PREFIX = "robot-call-v2:scheduled-job:";
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final DataSource dataSource;

    public PostgresClusterJobLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean runExclusively(String jobName, Runnable job) {
        long key = lockKeyOf(jobName);
        try (Connection connection = dataSource.getConnection()) {
            if (!executeLockQuery(connection, "SELECT pg_try_advisory_lock(?)", key)) {
                log.debug("Job {} skipped: another instance is running it", jobName);
                return false;
            }
            try {
                job.run();
            } finally {
                unlock(connection, key, jobName);
            }
            return true;
        } catch (SQLException e) {
            // A job that needs the database could not run without it anyway.
            log.warn("Job {} skipped: its cluster lock could not be taken: {}", jobName, e.getMessage());
            return false;
        }
    }

    /**
     * A stable 64-bit key for the job's name (FNV-1a). Computed here rather than with
     * Postgres's {@code hashtext()}, which is 32-bit and not promised to stay the same across
     * server versions: every instance, old release and new, must arrive at the same key.
     */
    static long lockKeyOf(String jobName) {
        long hash = FNV_OFFSET_BASIS;
        for (byte value : (KEY_PREFIX + jobName).getBytes(StandardCharsets.UTF_8)) {
            hash ^= value & 0xff;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    /**
     * Released on the connection that took it — a session lock belongs to its session. An
     * unlock that fails means a broken connection, whose lock goes with the session; at worst
     * it lasts until the pool retires that connection.
     */
    private static void unlock(Connection connection, long key, String jobName) {
        try {
            executeLockQuery(connection, "SELECT pg_advisory_unlock(?)", key);
        } catch (SQLException e) {
            log.warn("Job {}: its cluster lock could not be released: {}", jobName, e.getMessage());
        }
    }

    private static boolean executeLockQuery(Connection connection, String sql, long key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }
}
