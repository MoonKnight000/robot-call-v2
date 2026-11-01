package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * Type of automated action triggered upon call finalization.
 */
public enum PostCallActionType {
    SEND_SMS,
    WEBHOOK,
    TELEGRAM,
    /** Runs JavaScript over the call; what it returns joins the outcome the later actions read. */
    CODE
}
