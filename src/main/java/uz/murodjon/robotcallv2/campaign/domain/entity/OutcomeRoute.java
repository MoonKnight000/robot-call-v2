package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeExit;

/**
 * Where a contact goes after an outcome.
 *
 * @param stepId       STEP only: the step it moves to
 * @param delayMinutes STEP only, and only on a step's own outcomes (Retell "Add delay"): how long
 *                     it waits before that step's first attempt
 */
public record OutcomeRoute(OutcomeExit exit, String stepId, int delayMinutes) {

    public static final OutcomeRoute END = new OutcomeRoute(OutcomeExit.END, null, 0);
    public static final OutcomeRoute END_SUCCESSFUL = new OutcomeRoute(OutcomeExit.END_SUCCESSFUL, null, 0);

    public OutcomeRoute {
        exit = exit != null ? exit : OutcomeExit.END;
        delayMinutes = Math.max(0, delayMinutes);
    }
}
