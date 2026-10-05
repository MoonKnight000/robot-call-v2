package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentWebhookConfig;
import uz.murodjon.robotcallv2.aiagent.domain.enums.*;

public record AgentAdvancedDto(
        Boolean useRag,
        Boolean preemptiveGeneration,
        Boolean ivrNavigationEnabled,
        Boolean storeCallAudio,
        Boolean zeroPiiRetention,
        Integer conversationRetentionDays,
        RealtimeFactsDelivery realtimeFactsDelivery,
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
        Long transferQueueId,
        InterruptionSensitivity interruptionSensitivity,
        Integer endpointingDelayMs
) {
}
