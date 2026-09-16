package uz.murodjon.robotcallv2.voicemail.application.port.input;

import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;
import uz.murodjon.robotcallv2.voicemail.application.dto.SaveVoicemailBoxRequest;
import uz.murodjon.robotcallv2.voicemail.application.dto.UpdateVoicemailStatusRequest;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailBox;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailBoxFilter;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailMessage;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailMessageFilter;

public interface VoicemailUseCase {

    VoicemailBox createBox(long companyId, SaveVoicemailBoxRequest request);

    VoicemailBox updateBox(long companyId, long id, SaveVoicemailBoxRequest request);

    void deleteBox(long companyId, long id);

    VoicemailBox requireBox(long companyId, long id);

    /** The box, or null — what a live call asks, where a deleted box is a missing step, not an error. */
    VoicemailBox findBox(long companyId, long id);

    PageableData<VoicemailBox> listBoxes(long companyId, VoicemailBoxFilter filter);

    PageableData<VoicemailMessage> listMessages(long companyId, VoicemailMessageFilter filter);

    VoicemailMessage updateMessageStatus(long companyId, long id, UpdateVoicemailStatusRequest request);

    DownloadableFile downloadAudio(long companyId, long id);

    /** Stores a message a caller just left and tells the company about it. */
    VoicemailMessage recordMessage(long companyId, long boxId, Long callAttemptId, String callerPhone,
                                   byte[] wav, int durationSec, String transcript);
}
