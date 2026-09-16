package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A statement's text and the named parameters it binds, built side by side: a condition is
 * added together with its value, so the two cannot drift apart the way a string builder
 * and a separate parameter binder once did.
 *
 * <p>Conditions come before {@link #append} — GROUP BY, ORDER BY and LIMIT close the text.
 */
final class SqlStatement {

    private final StringBuilder sql;
    private final Map<String, Object> params = new LinkedHashMap<>();
    private boolean hasWhere;

    SqlStatement(String head) {
        this.sql = new StringBuilder(head);
    }

    /** The first condition opens the WHERE clause; every later one is ANDed to it. */
    SqlStatement where(String condition) {
        sql.append(hasWhere ? " AND " : " WHERE ").append(condition);
        hasWhere = true;
        return this;
    }

    SqlStatement where(String condition, String name, Object value) {
        return where(condition).bind(name, value);
    }

    /** Text after the conditions: GROUP BY, ORDER BY, LIMIT. */
    SqlStatement append(String text) {
        sql.append(' ').append(text);
        return this;
    }

    /** A parameter the head or an appended clause refers to. */
    SqlStatement bind(String name, Object value) {
        params.put(name, value);
        return this;
    }

    String sql() {
        return sql.toString();
    }

    Map<String, Object> params() {
        return Collections.unmodifiableMap(params);
    }

    JdbcClient.StatementSpec prepare(JdbcClient jdbc) {
        return jdbc.sql(sql()).params(params);
    }
}
