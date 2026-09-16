package uz.murodjon.robotcallv2.operator.application.dto;

import uz.murodjon.robotcallv2.agent.dialog.OperatorSnapshot;

/**
 * What the operator screen shows for a transferred call — the controller's own shape,
 * so the web layer never depends on the voice pipeline's {@link OperatorSnapshot}.
 */
public record OperatorContextResponse(
        String channelId,
        String clientName,
        String debtAmount,
        String currency,
        String dueDate,
        String contractNumber,
        String dialogState,
        String transcript
) {

    public static OperatorContextResponse of(OperatorSnapshot snapshot) {
        return new OperatorContextResponse(snapshot.channelId(), snapshot.clientName(), snapshot.debtAmount(),
                snapshot.currency(), snapshot.dueDate(), snapshot.contractNumber(), snapshot.dialogState(),
                snapshot.transcript());
    }
}
