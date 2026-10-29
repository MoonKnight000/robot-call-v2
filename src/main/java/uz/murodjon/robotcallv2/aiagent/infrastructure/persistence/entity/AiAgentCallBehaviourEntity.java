package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import uz.murodjon.robotcallv2.aiagent.domain.enums.InterruptionSensitivity;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction;

/** The ai_agent columns behind {@code AiAgentCallBehaviour}: turn-taking, voicemail, SMS and transfer. */
@Embeddable
public class AiAgentCallBehaviourEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "interruption_sensitivity", nullable = false, length = 32)
    private InterruptionSensitivity interruptionSensitivity = InterruptionSensitivity.MEDIUM;

    @Column(name = "dtmf_input_enabled", nullable = false)
    private boolean dtmfInputEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "voicemail_action", nullable = false)
    private VoicemailAction voicemailAction = VoicemailAction.LEAVE_MESSAGE;

    @Column(name = "voicemail_message", columnDefinition = "TEXT")
    private String voicemailMessage;

    @Column(name = "mid_call_sms_enabled", nullable = false)
    private boolean midCallSmsEnabled;

    @Column(name = "mid_call_sms_template", columnDefinition = "TEXT")
    private String midCallSmsTemplate;

    @Column(name = "transfer_phone_number", length = 32)
    private String transferPhoneNumber;

    @Column(name = "transfer_message", columnDefinition = "TEXT")
    private String transferMessage;

    @Column(name = "transfer_queue_id")
    private Long transferQueueId;

    @Column(name = "pre_conversation_audio_file_id")
    private Long preConversationAudioFileId;

    @Column(name = "auto_escalate_on_frustration")
    private Boolean autoEscalateOnFrustration;

    @Column(name = "frustration_threshold")
    private Integer frustrationThreshold;

    protected AiAgentCallBehaviourEntity() {
    }

    public AiAgentCallBehaviourEntity(InterruptionSensitivity interruptionSensitivity,
                                      boolean dtmfInputEnabled, VoicemailAction voicemailAction,
                                      String voicemailMessage, boolean midCallSmsEnabled,
                                      String midCallSmsTemplate, String transferPhoneNumber,
                                      String transferMessage, Long transferQueueId,
                                      Long preConversationAudioFileId,
                                      Boolean autoEscalateOnFrustration, Integer frustrationThreshold) {
        this.interruptionSensitivity = interruptionSensitivity != null
                ? interruptionSensitivity : InterruptionSensitivity.MEDIUM;
        this.dtmfInputEnabled = dtmfInputEnabled;
        this.voicemailAction = voicemailAction != null ? voicemailAction : VoicemailAction.LEAVE_MESSAGE;
        this.voicemailMessage = voicemailMessage;
        this.midCallSmsEnabled = midCallSmsEnabled;
        this.midCallSmsTemplate = midCallSmsTemplate;
        this.transferPhoneNumber = transferPhoneNumber;
        this.transferMessage = transferMessage;
        this.transferQueueId = transferQueueId;
        this.preConversationAudioFileId = preConversationAudioFileId;
        this.autoEscalateOnFrustration = autoEscalateOnFrustration;
        this.frustrationThreshold = frustrationThreshold;
    }

    public InterruptionSensitivity getInterruptionSensitivity() {
        return interruptionSensitivity;
    }

    public boolean isDtmfInputEnabled() {
        return dtmfInputEnabled;
    }

    public VoicemailAction getVoicemailAction() {
        return voicemailAction;
    }

    public String getVoicemailMessage() {
        return voicemailMessage;
    }

    public boolean isMidCallSmsEnabled() {
        return midCallSmsEnabled;
    }

    public String getMidCallSmsTemplate() {
        return midCallSmsTemplate;
    }

    public String getTransferPhoneNumber() {
        return transferPhoneNumber;
    }

    public String getTransferMessage() {
        return transferMessage;
    }

    public Long getTransferQueueId() {
        return transferQueueId;
    }

    public Long getPreConversationAudioFileId() {
        return preConversationAudioFileId;
    }

    public Boolean getAutoEscalateOnFrustration() {
        return autoEscalateOnFrustration;
    }

    public Integer getFrustrationThreshold() {
        return frustrationThreshold;
    }
}
