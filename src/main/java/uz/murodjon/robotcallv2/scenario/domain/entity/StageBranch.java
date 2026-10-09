package uz.murodjon.robotcallv2.scenario.domain.entity;

import uz.murodjon.robotcallv2.scenario.domain.enums.BranchOperator;

/**
 * A decision a stage makes without the model: when the call fact {@code fact} compares to
 * {@code value} by {@code operator}, the call moves on to {@code goTo} the moment it
 * enters the stage. "No birth year on file — skip the identity check", "nothing owed —
 * close" are settled by the data the call was dialled with, and asking a model costs a
 * round trip and can still get it wrong.
 *
 * @param value compared as a number when both sides read as one, otherwise as text,
 *              ignoring case; not read by {@link BranchOperator#EXISTS}/{@link BranchOperator#MISSING}
 */
public record StageBranch(String fact, BranchOperator operator, String value, String goTo) {
}
