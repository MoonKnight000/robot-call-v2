package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentWebhookConfig;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.aiagent.domain.enums.InterruptionSensitivity;
import uz.murodjon.robotcallv2.aiagent.domain.enums.NoiseCancellationMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction;

public record AgentAdvancedDto(
        Boolean useRag,
        Boolean preemptiveGeneration,
        Boolean ivrNavigationEnabled,
        Boolean storeCallAudio,
        Boolean zeroPiiRetention,
        Integer conversationRetentionDays,
        AgentWebhookConfig initiationWebhook,
        AgentWebhookConfig postCallWebhook,
        AmbientSound ambientSound,
        Double ambientSoundVolume,
        Double ambientSoundFadeInSeconds,
        AmbientSound thinkingSound,
        Double thinkingSoundVolume,
        Boolean noiseCancellationEnabled,
        NoiseCancellationMode noiseCancellationMode,
        Boolean emotionAdaptiveVoice,
        Boolean dtmfInputEnabled,
        VoicemailAction voicemailAction,
        String voicemailMessage,
        Boolean midCallSmsEnabled,
        String midCallSmsTemplate,
        String transferPhoneNumber,
        String transferMessage,
        InterruptionSensitivity interruptionSensitivity,
        Integer endpointingDelayMs
) {
}
