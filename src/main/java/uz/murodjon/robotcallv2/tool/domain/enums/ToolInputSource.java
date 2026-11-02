package uz.murodjon.robotcallv2.tool.domain.enums;

/** Where one input of a tool's request takes its value from. */
public enum ToolInputSource {

    /** The model fills it from the conversation, guided by the input's description. */
    AI,

    /** A fixed value, the same on every call. */
    VALUE,

    /** A variable of the call ({@code phone}, a fact, an earlier tool's mapped field), named by the value. */
    VARIABLE
}
