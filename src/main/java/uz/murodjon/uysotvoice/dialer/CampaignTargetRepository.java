package uz.murodjon.uysotvoice.dialer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

/** JdbcTemplate DAO for {@code campaign_target} (PROJECT.md §6). */
@Repository
public class CampaignTargetRepository {

    private static final RowMapper<TargetRow> MAPPER = (rs, i) -> new TargetRow(
            rs.getLong("id"),
            rs.getLong("campaign_id"),
            rs.getLong("client_id"),
            rs.getString("phone"),
            rs.getString("language"),
            rs.getString("context_data"),
            rs.getString("status"),
            rs.getInt("attempts"),
            rs.getBoolean("do_not_call"));

    private final JdbcTemplate jdbc;

    public CampaignTargetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long add(long campaignId, long clientId, String phone, String language, String contextDataJson) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO campaign_target(campaign_id, client_id, phone, language, context_data, status) "
                            + "VALUES (?, ?, ?, ?, ?::jsonb, 'PENDING')", new String[]{"id"});
            ps.setLong(1, campaignId);
            ps.setLong(2, clientId);
            ps.setString(3, phone);
            if (language != null) {
                ps.setString(4, language);
            } else {
                ps.setNull(4, Types.VARCHAR);
            }
            ps.setString(5, contextDataJson != null ? contextDataJson : "{}");
            return ps;
        }, key);
        Number id = key.getKey();
        return id != null ? id.longValue() : 0;
    }

    public TargetRow find(long id) {
        List<TargetRow> rows = jdbc.query("SELECT * FROM campaign_target WHERE id = ?", MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<TargetRow> findByCampaign(long campaignId) {
        return jdbc.query("SELECT * FROM campaign_target WHERE campaign_id = ? ORDER BY id", MAPPER, campaignId);
    }

    /**
     * Atomically claim up to {@code limit} targets that are ready to dial: PENDING,
     * not opted out (the per-target flag <em>and</em> the phone-level list from
     * §11.4), and past their retry time. Claimed rows come back already marked
     * IN_PROGRESS with the attempt counted.
     *
     * <p>Select-then-update in two statements would let a second dialer instance — or
     * a tick that overruns its interval — pick the same target and call the client
     * twice. {@code FOR UPDATE SKIP LOCKED} hands each row to exactly one claimer and
     * lets the others move on instead of blocking.
     */
    public List<TargetRow> claimDue(long campaignId, int limit) {
        return jdbc.query(
                "UPDATE campaign_target SET status = 'IN_PROGRESS', attempts = attempts + 1 "
                        + "WHERE id IN ("
                        + "  SELECT t.id FROM campaign_target t"
                        + "  WHERE t.campaign_id = ? AND t.status = 'PENDING' AND t.do_not_call = false"
                        + "    AND NOT EXISTS (SELECT 1 FROM do_not_call_list d WHERE d.phone = t.phone)"
                        + "    AND (t.next_attempt_at IS NULL OR t.next_attempt_at <= now())"
                        + "  ORDER BY t.next_attempt_at NULLS FIRST, t.id"
                        + "  LIMIT ? FOR UPDATE SKIP LOCKED"
                        + ") RETURNING *",
                MAPPER, campaignId, limit);
    }

    public void updateStatus(long id, String status, Instant nextAttemptAt) {
        jdbc.update("UPDATE campaign_target SET status = ?, next_attempt_at = ? WHERE id = ?",
                status, nextAttemptAt != null ? Timestamp.from(nextAttemptAt) : null, id);
    }

    public void setDoNotCall(long id) {
        jdbc.update("UPDATE campaign_target SET do_not_call = true, status = 'DONE' WHERE id = ?", id);
    }
}
