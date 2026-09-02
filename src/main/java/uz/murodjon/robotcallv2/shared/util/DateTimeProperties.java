package uz.murodjon.robotcallv2.shared.util;

import java.time.format.DateTimeFormatter;

/**
 * The one date/time display format the whole API uses: {@link #TIME_PATTERN} for
 * {@code LocalTime} fields, {@link #DATE_PATTERN} for {@code LocalDate} fields.
 * Referenced from {@code @JsonFormat(pattern = ...)} on every such field instead of
 * repeating the literal pattern.
 */
public final class DateTimeProperties {

    public static final String DATE_PATTERN = "dd.MM.yyyy";
    public static final String TIME_PATTERN = "HH:mm";

    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(TIME_PATTERN);

    private DateTimeProperties() {
    }
}
