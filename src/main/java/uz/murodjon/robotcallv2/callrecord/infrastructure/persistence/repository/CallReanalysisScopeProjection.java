package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository;

public interface CallReanalysisScopeProjection {
    long getCallCount();
    long getTranscriptChars();
}
