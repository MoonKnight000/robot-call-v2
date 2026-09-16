package uz.murodjon.robotcallv2.tool.domain.enums;

/** How one request a company tool sent ended ({@code ToolExecution}). */
public enum ToolExecutionStatus {

    /** The endpoint answered 2xx. */
    OK,

    /** It answered with anything else — it refused what the model sent, or it broke. */
    HTTP_ERROR,

    /** No answer at all: the connection, the timeout or the address failed. */
    FAILED
}
