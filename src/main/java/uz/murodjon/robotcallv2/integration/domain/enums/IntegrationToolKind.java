package uz.murodjon.robotcallv2.integration.domain.enums;

/** What runs when the model calls an integration tool. */
public enum IntegrationToolKind {
    /** Code: the {@link CrmTool} the row's key names. */
    BUILT_IN,
    /** A request the platform admin defined: the row's definition, sent to the integration's API. */
    HTTP
}
