package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.agent.dialog.DialogTechnicalSnapshot;

public interface CallTechnicalRepository {
    boolean save(long callId, String channelName, String trunk, String amdResult, String sttProvider,
                 String ttsProvider, String ttsVoice, String llmModel, DialogTechnicalSnapshot technical);
}
