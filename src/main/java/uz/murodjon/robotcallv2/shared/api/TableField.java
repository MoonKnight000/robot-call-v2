package uz.murodjon.robotcallv2.shared.api;

/**
 * A column a {@link FilterInterface} is allowed to sort by. Implemented by an enum per
 * sortable entity, so a sort request can only ever name a known column — never an
 * arbitrary string that would otherwise have to be trusted straight into SQL.
 */
public interface TableField {

    String column();

    /**
     * {@link #column()} as a JPA entity property path, for building a Spring Data
     * {@link org.springframework.data.domain.Sort}. Derived by converting {@code snake_case}
     * to {@code camelCase} — every entity in this project maps that way by Spring Boot's
     * default naming strategy, so no enum needs to declare this separately. Not meaningful
     * for a table-aliased raw-SQL column (e.g. {@code "a.id"} in {@code CallTableField}),
     * whose only consumer builds a SQL {@code ORDER BY} clause directly and never calls this.
     */
    default String property() {
        String column = column();
        StringBuilder sb = new StringBuilder(column.length());
        boolean upperNext = false;
        for (int i = 0; i < column.length(); i++) {
            char c = column.charAt(i);
            if (c == '_') {
                upperNext = true;
                continue;
            }
            sb.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return sb.toString();
    }
}
