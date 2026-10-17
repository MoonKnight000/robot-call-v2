package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FloorLearningTest {

    private LearnedFloorRuleJpaRepository repository;
    private ObjectProvider<StringRedisTemplate> redisProvider;
    private FloorLearning floorLearning;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(LearnedFloorRuleJpaRepository.class);
        redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        floorLearning = new FloorLearning(repository, redisProvider);
    }

    @Test
    void unseenPhraseReturnsFalse() {
        assertThat(floorLearning.isLearnedTakingTheFloor("tushundim")).isFalse();
    }

    @Test
    void promotesPhraseAfterFiveOccurrences() {
        when(repository.findByPhrase("boldi boldi")).thenReturn(Optional.empty());

        for (int i = 1; i <= 5; i++) {
            floorLearning.recordModelDecision("bo'ldi, bo'ldi", "Bot line", true);
        }

        ArgumentCaptor<LearnedFloorRuleEntity> captor = ArgumentCaptor.forClass(LearnedFloorRuleEntity.class);
        verify(repository, atLeast(5)).save(captor.capture());

        LearnedFloorRuleEntity lastSaved = captor.getValue();
        assertThat(lastSaved.getStatus()).isEqualTo("APPROVED");
        assertThat(lastSaved.getDecision()).isEqualTo("STOP");
        assertThat(floorLearning.isLearnedTakingTheFloor("bo'ldi bo'ldi")).isTrue();
    }

    @Test
    void vetoPreventsPhraseFromTakingFloor() {
        floorLearning.recordModelDecision("aha", "Bot line", false);

        ArgumentCaptor<LearnedFloorRuleEntity> captor = ArgumentCaptor.forClass(LearnedFloorRuleEntity.class);
        verify(repository).save(captor.capture());

        LearnedFloorRuleEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("BLOCKED");
        assertThat(saved.getDecision()).isEqualTo("CONTINUE");
        assertThat(floorLearning.isLearnedTakingTheFloor("aha")).isFalse();
    }

    @Test
    void preloadsApprovedRulesFromDatabaseAtStartup() {
        LearnedFloorRuleEntity entity = new LearnedFloorRuleEntity(
                "toxtab turing", "STOP", 6, "APPROVED", Instant.now(), Instant.now()
        );
        when(repository.findAll()).thenReturn(List.of(entity));

        floorLearning.init();

        assertThat(floorLearning.isLearnedTakingTheFloor("to'xtab turing")).isTrue();
    }
}
