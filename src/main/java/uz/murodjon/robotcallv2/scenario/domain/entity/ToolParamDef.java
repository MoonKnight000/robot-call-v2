package uz.murodjon.robotcallv2.scenario.domain.entity;

/**
 * @param type one of {@code string}, {@code number}, {@code boolean}, {@code date},
 *             {@code futureDate} (today or later) or {@code pastDate} (today or earlier); the
 *             last two are refused with a message the model can act on, not recorded
 */
public record ToolParamDef(
        String name,
        String type,
        boolean required,
        String constraint
) {
}
