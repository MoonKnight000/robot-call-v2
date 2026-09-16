package uz.murodjon.robotcallv2.scenario.domain.entity;

/**
 * One named result a call records.
 *
 * @param goal whether this field is what the scenario exists to record: a call that
 *             recorded it ends {@code GOAL_REACHED}, one that did not {@code GOAL_NOT_REACHED}
 *             (UNIVERSAL-OPERATOR-PLAN A.1). At most one per scenario ({@code ScenarioValidator});
 *             a scenario with none keeps the dispositions its own tools set
 */
public record OutcomeField(
        String name,
        String type,
        String description,
        boolean goal
) {

    /** A field that records a result without being the scenario's goal. */
    public OutcomeField(String name, String type, String description) {
        this(name, type, description, false);
    }
}
