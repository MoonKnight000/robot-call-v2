package uz.murodjon.uysotvoice.scenario.dto;

import java.util.List;

/**
 * One FSM state a scenario can be in (ROADMAP A.1/A.3) — a stage id is a plain
 * {@code String} rather than an enum constant, since every scenario declares its own.
 *
 * @param id                 stable state id (e.g. {@code "GREETING"}, {@code "DEBT_NOTICE"})
 * @param purpose            what this stage tries to accomplish, folded into the prompt
 * @param allowedTransitions ids of stages {@code transitionTo} may move to from here;
 *                           empty/null marks this stage terminal (a call may end here)
 * @param allowedTools       scenario tool names callable from this stage, in addition to
 *                           the fixed universal set. {@code null} (the common case) means
 *                           every tool the scenario declares is callable here; an
 *                           explicit list — including an empty one — means exactly those
 *                           tools and no others. Only a scenario that genuinely varies
 *                           tools per stage needs to set this (e.g. debt-collection
 *                           restricting recordPaymentPromise to the stages where it
 *                           makes sense, with {@code []} elsewhere)
 */
public record StageDef(String id, String purpose, List<String> allowedTransitions, List<String> allowedTools) {
}
