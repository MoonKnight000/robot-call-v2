package uz.murodjon.robotcallv2.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PostgresClusterJobLockTest {

    private static final String LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(?)";

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement lockStatement;
    private PreparedStatement unlockStatement;
    private PostgresClusterJobLock clusterJobLock;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        lockStatement = mock(PreparedStatement.class);
        unlockStatement = mock(PreparedStatement.class);
        ResultSet unlocked = resultOf(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(LOCK_SQL)).thenReturn(lockStatement);
        when(connection.prepareStatement(UNLOCK_SQL)).thenReturn(unlockStatement);
        when(unlockStatement.executeQuery()).thenReturn(unlocked);
        clusterJobLock = new PostgresClusterJobLock(dataSource);
    }

    @Test
    void runsTheJobAndReleasesTheLockOnTheSameConnection() throws SQLException {
        givenLockAcquired(true);
        AtomicBoolean ran = new AtomicBoolean();

        boolean result = clusterJobLock.runExclusively("dialer.dispatch", () -> ran.set(true));

        assertThat(result).isTrue();
        assertThat(ran).isTrue();
        long key = PostgresClusterJobLock.lockKeyOf("dialer.dispatch");
        InOrder order = inOrder(lockStatement, unlockStatement, connection);
        order.verify(lockStatement).setLong(1, key);
        order.verify(lockStatement).executeQuery();
        order.verify(unlockStatement).setLong(1, key);
        order.verify(unlockStatement).executeQuery();
        order.verify(connection).close();
    }

    @Test
    void skipsTheJobWhenAnotherInstanceHoldsTheLock() throws SQLException {
        givenLockAcquired(false);
        AtomicBoolean ran = new AtomicBoolean();

        boolean result = clusterJobLock.runExclusively("dialer.dispatch", () -> ran.set(true));

        assertThat(result).isFalse();
        assertThat(ran).isFalse();
        verify(connection, never()).prepareStatement(UNLOCK_SQL);
        verify(connection).close();
    }

    @Test
    void releasesTheLockWhenTheJobFails() throws SQLException {
        givenLockAcquired(true);

        assertThatThrownBy(() -> clusterJobLock.runExclusively("dialer.dispatch", () -> {
            throw new RuntimeException("job failed");
        })).hasMessage("job failed");

        verify(unlockStatement).executeQuery();
        verify(connection).close();
    }

    @Test
    void skipsTheJobWhenTheDatabaseIsUnreachable() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));
        AtomicBoolean ran = new AtomicBoolean();

        boolean result = clusterJobLock.runExclusively("dialer.dispatch", () -> ran.set(true));

        assertThat(result).isFalse();
        assertThat(ran).isFalse();
    }

    /**
     * Pinned: a key that changed between releases would let an old and a new instance run the
     * same job side by side during a rolling deploy.
     */
    @Test
    void derivesAStableKeyPerJob() {
        assertThat(PostgresClusterJobLock.lockKeyOf("dialer.dispatch")).isEqualTo(-6243999722998853609L);
        assertThat(PostgresClusterJobLock.lockKeyOf("dialer.dispatch"))
                .isNotEqualTo(PostgresClusterJobLock.lockKeyOf("dialer.reclaim-expired-claims"));
    }

    private void givenLockAcquired(boolean acquired) throws SQLException {
        ResultSet result = resultOf(acquired);
        when(lockStatement.executeQuery()).thenReturn(result);
    }

    private static ResultSet resultOf(boolean value) throws SQLException {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(value);
        return result;
    }
}
