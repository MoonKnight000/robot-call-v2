package uz.murodjon.robotcallv2.missedcall.application.dto;

/**
 * Body of PUT /api/missed-calls/{id}/assign.
 *
 * @param userId who calls back; null un-assigns
 */
public record AssignMissedCallRequest(Long userId) {
}
