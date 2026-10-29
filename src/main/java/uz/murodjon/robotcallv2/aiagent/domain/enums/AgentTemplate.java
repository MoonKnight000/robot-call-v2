package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * Presets a new agent can be seeded from, so the first screen is not empty. One name per preset:
 * the name is stored with the agent ({@code AiAgentScript.templateId}), so renaming one is a migration.
 */
public enum AgentTemplate {
    APPOINTMENT_BOOKING,
    BUSINESS,
    SUPPORT,
    LEAD_QUALIFICATION,
    DEBT_REMINDER,
    INBOUND_RECEPTIONIST,
    SURVEY,
    MEDICAL,
    ORDER_TRACKING,
    BLANK
}
