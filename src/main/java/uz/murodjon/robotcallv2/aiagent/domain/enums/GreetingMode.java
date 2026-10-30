package uz.murodjon.robotcallv2.aiagent.domain.enums;

/** Who speaks first on a call, and where the opening words come from. */
public enum GreetingMode {

    /** The agent speaks first and says the configured line; with none configured it composes one. */
    AI_CUSTOM,

    /** The agent speaks first and the model composes the opening from the prompt every time. */
    AI_DYNAMIC,

    /** The caller speaks first; the agent answers instead of opening. */
    USER_FIRST
}
