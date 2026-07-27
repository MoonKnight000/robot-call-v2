package uz.murodjon.uysotvoice.dialer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.LocalTime;
import java.util.List;

/** JdbcTemplate DAO for {@code campaign} (PROJECT.md §6). */
@Repository
public class CampaignRepository {

    private static final RowMapper<CampaignRow> MAPPER = (rs, i) -> new CampaignRow(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("type"),
            rs.getString("status"),
            rs.getString("goal_prompt"),
            rs.getString("default_language"),
            rs.getObject("dial_window_start", LocalTime.class),
            rs.getObject("dial_window_end", LocalTime.class),
            rs.getString("dial_days"),
            rs.getInt("max_attempts"),
            rs.getInt("retry_interval_hours"),
            rs.getInt("max_concurrent_calls"),
            rs.getString("tts_voice"),
            rs.getInt("daily_call_cap"));

    private final JdbcTemplate jdbc;

    public CampaignRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long create(String name, String type, String goalPrompt, String scriptConfigJson,
                       String defaultLanguage, LocalTime windowStart, LocalTime windowEnd,
                       String dialDays, int maxAttempts, int retryIntervalHours, int maxConcurrentCalls,
                       String ttsVoice, int dailyCallCap) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO campaign(name, type, status, goal_prompt, script_config, default_language, "
                            + "dial_window_start, dial_window_end, dial_days, max_attempts, retry_interval_hours, "
                            + "max_concurrent_calls, tts_voice, daily_call_cap) "
                            + "VALUES (?, ?, 'DRAFT', ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?)", new String[]{"id"});
            ps.setString(1, name);
            ps.setString(2, type);
            ps.setString(3, goalPrompt);
            ps.setString(4, scriptConfigJson != null ? scriptConfigJson : "{}");
            ps.setString(5, defaultLanguage);
            ps.setObject(6, windowStart);
            ps.setObject(7, windowEnd);
            ps.setString(8, dialDays);
            ps.setInt(9, maxAttempts);
            ps.setInt(10, retryIntervalHours);
            ps.setInt(11, maxConcurrentCalls);
            ps.setString(12, ttsVoice);
            ps.setInt(13, dailyCallCap);
            return ps;
        }, key);
        Number id = key.getKey();
        return id != null ? id.longValue() : 0;
    }

    public CampaignRow find(long id) {
        List<CampaignRow> rows = jdbc.query("SELECT * FROM campaign WHERE id = ?", MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<CampaignRow> findAll() {
        return jdbc.query("SELECT * FROM campaign ORDER BY id", MAPPER);
    }

    public List<CampaignRow> findActive() {
        return jdbc.query("SELECT * FROM campaign WHERE status = 'ACTIVE' ORDER BY id", MAPPER);
    }

    public void updateStatus(long id, String status) {
        jdbc.update("UPDATE campaign SET status = ? WHERE id = ?", status, id);
    }
}
