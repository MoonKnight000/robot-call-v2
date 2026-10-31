package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.campaign.application.port.output.AutoEnrollContactRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollCandidate;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollRule;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollment;
import uz.murodjon.robotcallv2.campaign.domain.enums.AutoEnrollField;
import uz.murodjon.robotcallv2.shared.util.LikePatterns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns auto-enroll rules into a query over the {@code contact} table. Every value travels as a
 * bind parameter; only column expressions chosen here are ever written into the SQL.
 */
@Component
public class AutoEnrollContactRepositoryAdapter implements AutoEnrollContactRepository {

    private static final String PHONE_DIGITS = "regexp_replace(c.phone, '\\D', '', 'g')";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<AutoEnrollCandidate> findMatching(long companyId, AutoEnrollment autoEnroll,
                                                  Long excludeEnrolledInCampaignId, int limit) {
        return select(companyId, autoEnroll, excludeEnrolledInCampaignId, 0, limit);
    }

    @Override
    public List<AutoEnrollCandidate> findMatchingPage(long companyId, AutoEnrollment autoEnroll, int offset, int limit) {
        return select(companyId, autoEnroll, null, offset, limit);
    }

    @SuppressWarnings("unchecked")
    private List<AutoEnrollCandidate> select(long companyId, AutoEnrollment autoEnroll,
                                             Long excludeEnrolledInCampaignId, int offset, int limit) {
        if (autoEnroll.hasNoRules()) {
            return List.of();
        }
        Map<String, Object> parameters = new HashMap<>();
        String where = buildWhere(companyId, autoEnroll, excludeEnrolledInCampaignId, parameters);
        Query query = entityManager.createNativeQuery(
                "SELECT c.id, c.name, c.phone, c.address, "
                        + "(SELECT string_agg(ct.tag, ',') FROM contact_tag ct WHERE ct.contact_id = c.id) AS tags "
                        + "FROM contact c WHERE " + where
                        + " ORDER BY c.id LIMIT :limit OFFSET :offset");
        parameters.forEach(query::setParameter);
        query.setParameter("limit", limit);
        query.setParameter("offset", offset);
        List<AutoEnrollCandidate> candidates = new ArrayList<>();
        for (Object[] row : (List<Object[]>) query.getResultList()) {
            candidates.add(new AutoEnrollCandidate(((Number) row[0]).longValue(), (String) row[1], (String) row[2],
                    (String) row[3], (String) row[4]));
        }
        return candidates;
    }

    @Override
    public long countMatching(long companyId, AutoEnrollment autoEnroll) {
        if (autoEnroll.hasNoRules()) {
            return 0;
        }
        Map<String, Object> parameters = new HashMap<>();
        String where = buildWhere(companyId, autoEnroll, null, parameters);
        Query query = entityManager.createNativeQuery("SELECT count(*) FROM contact c WHERE " + where);
        parameters.forEach(query::setParameter);
        return ((Number) query.getSingleResult()).longValue();
    }

    @Override
    @Transactional
    public void addTag(long companyId, String phone, String tag) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return;
        }
        entityManager.createNativeQuery(
                "INSERT INTO contact_tag (contact_id, tag) "
                        + "SELECT c.id, :tag FROM contact c "
                        + "WHERE c.company_id = :companyId AND " + PHONE_DIGITS + " = :digits "
                        + "ON CONFLICT (contact_id, tag) DO NOTHING")
                .setParameter("tag", tag.trim())
                .setParameter("companyId", companyId)
                .setParameter("digits", digits)
                .executeUpdate();
    }

    private static String buildWhere(long companyId, AutoEnrollment autoEnroll, Long excludeEnrolledInCampaignId,
                                     Map<String, Object> parameters) {
        parameters.put("companyId", companyId);
        List<String> predicates = new ArrayList<>();
        for (int index = 0; index < autoEnroll.rules().size(); index++) {
            predicates.add(predicate(autoEnroll.rules().get(index), "rule" + index, parameters));
        }
        StringBuilder where = new StringBuilder("c.company_id = :companyId AND (")
                .append(String.join(autoEnroll.matchAll() ? " AND " : " OR ", predicates))
                .append(')');
        if (!autoEnroll.excludedContactIds().isEmpty()) {
            parameters.put("excludedContactIds", autoEnroll.excludedContactIds());
            where.append(" AND c.id NOT IN (:excludedContactIds)");
        }
        if (excludeEnrolledInCampaignId != null) {
            parameters.put("campaignId", excludeEnrolledInCampaignId);
            where.append(" AND NOT EXISTS (SELECT 1 FROM campaign_target t "
                    + "WHERE t.campaign_id = :campaignId AND t.phone = c.phone)");
        }
        return where.toString();
    }

    /** One rule as a SQL condition; its value is bound as {@code :name}. */
    private static String predicate(AutoEnrollRule rule, String name, Map<String, Object> parameters) {
        String value = rule.value().trim().toLowerCase(Locale.ROOT);
        if (rule.field() == AutoEnrollField.DO_NOT_CALL) {
            String listed = "EXISTS (SELECT 1 FROM do_not_call_list d WHERE d.company_id = c.company_id "
                    + "AND d.phone = c.phone AND d.removed_at IS NULL)";
            return Boolean.parseBoolean(value) ? listed : "NOT " + listed;
        }
        String escaped = LikePatterns.escape(value);
        if (rule.field() == AutoEnrollField.TAG) {
            String tagLike = " LIKE :" + name + " ESCAPE '" + LikePatterns.ESCAPE + "'";
            switch (rule.operator()) {
                case EQUALS -> {
                    parameters.put(name, value);
                    return "EXISTS (SELECT 1 FROM contact_tag ct WHERE ct.contact_id = c.id AND lower(ct.tag) = :" + name + ")";
                }
                case NOT_EQUALS -> {
                    parameters.put(name, value);
                    return "NOT EXISTS (SELECT 1 FROM contact_tag ct WHERE ct.contact_id = c.id AND lower(ct.tag) = :" + name + ")";
                }
                case IS_ANY_OF -> {
                    String[] tags = value.split(",");
                    List<String> placeholders = new ArrayList<>();
                    for (int index = 0; index < tags.length; index++) {
                        String tag = tags[index].trim();
                        if (tag.isEmpty()) {
                            continue;
                        }
                        String part = name + "_" + index;
                        parameters.put(part, tag);
                        placeholders.add(":" + part);
                    }
                    return placeholders.isEmpty() ? "FALSE" :
                            "EXISTS (SELECT 1 FROM contact_tag ct WHERE ct.contact_id = c.id AND lower(ct.tag) IN (" + String.join(", ", placeholders) + "))";
                }
                case CONTAINS -> {
                    parameters.put(name, "%" + escaped + "%");
                    return "EXISTS (SELECT 1 FROM contact_tag ct WHERE ct.contact_id = c.id AND lower(ct.tag)" + tagLike + ")";
                }
                case NOT_CONTAINS -> {
                    parameters.put(name, "%" + escaped + "%");
                    return "NOT EXISTS (SELECT 1 FROM contact_tag ct WHERE ct.contact_id = c.id AND lower(ct.tag)" + tagLike + ")";
                }
                case STARTS_WITH -> {
                    parameters.put(name, escaped + "%");
                    return "EXISTS (SELECT 1 FROM contact_tag ct WHERE ct.contact_id = c.id AND lower(ct.tag)" + tagLike + ")";
                }
                case ENDS_WITH -> {
                    parameters.put(name, "%" + escaped);
                    return "EXISTS (SELECT 1 FROM contact_tag ct WHERE ct.contact_id = c.id AND lower(ct.tag)" + tagLike + ")";
                }
                default -> throw new IllegalStateException("unhandled auto-enroll operator " + rule.operator());
            }
        }
        String column;
        switch (rule.field()) {
            case PHONE -> {
                column = PHONE_DIGITS;
                value = value.replaceAll("\\D", "");
                escaped = value;
            }
            case NAME -> column = "lower(c.name)";
            case ADDRESS -> column = "lower(coalesce(c.address, ''))";
            case NOTES -> column = "lower(coalesce(c.notes, ''))";
            default -> throw new IllegalStateException("unhandled auto-enroll field " + rule.field());
        }
        String like = " LIKE :" + name + " ESCAPE '" + LikePatterns.ESCAPE + "'";
        switch (rule.operator()) {
            case EQUALS -> {
                parameters.put(name, value);
                return column + " = :" + name;
            }
            case NOT_EQUALS -> {
                parameters.put(name, value);
                return column + " <> :" + name;
            }
            case ENDS_WITH -> {
                parameters.put(name, "%" + escaped);
                return column + like;
            }
            case CONTAINS -> {
                parameters.put(name, "%" + escaped + "%");
                return column + like;
            }
            case NOT_CONTAINS -> {
                parameters.put(name, "%" + escaped + "%");
                return column + " NOT" + like;
            }
            case STARTS_WITH -> {
                parameters.put(name, escaped + "%");
                return column + like;
            }
            default -> throw new IllegalStateException("unhandled auto-enroll operator " + rule.operator());
        }
    }
}
