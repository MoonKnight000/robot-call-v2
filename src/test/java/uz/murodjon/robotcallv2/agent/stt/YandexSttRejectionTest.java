package uz.murodjon.robotcallv2.agent.stt;

import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Which Yandex stream errors are a refusal of the stream's own settings, not a lost connection. */
class YandexSttRejectionTest {

    @Test
    void anUnknownModelOrABadKeyIsARefusalAndADroppedConnectionIsNot() {
        assertThat(YandexSttProvider.isRejection(Status.INVALID_ARGUMENT
                .withDescription("Unknown model gemini-3.5-transcribe-live").asRuntimeException())).isTrue();
        assertThat(YandexSttProvider.isRejection(Status.UNAUTHENTICATED.asRuntimeException())).isTrue();
        assertThat(YandexSttProvider.isRejection(Status.UNAVAILABLE.asRuntimeException())).isFalse();
        assertThat(YandexSttProvider.isRejection(Status.DEADLINE_EXCEEDED.asRuntimeException())).isFalse();
        assertThat(YandexSttProvider.isRejection(new RuntimeException("socket closed"))).isFalse();
    }
}
