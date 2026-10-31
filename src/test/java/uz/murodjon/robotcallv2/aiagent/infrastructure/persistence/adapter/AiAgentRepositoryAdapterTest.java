package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentAmbience;
import uz.murodjon.robotcallv2.aiagent.domain.entity.PronunciationRule;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.aiagent.domain.enums.HandbookRule;
import uz.murodjon.robotcallv2.aiagent.domain.enums.NoiseCancellationMode;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentAmbienceEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.PronunciationRuleEmbeddable;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.mapper.AiAgentEntityMapper;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.repository.ScenarioJpaRepository;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.repository.TtsVoiceJpaRepository;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAgentRepositoryAdapterTest {

    @Mock
    private AiAgentJpaRepository jpaRepository;

    @Mock
    private CompanyJpaRepository companyJpaRepository;

    @Mock
    private ScenarioJpaRepository scenarioJpaRepository;

    @Mock
    private TtsVoiceJpaRepository ttsVoiceJpaRepository;

    private AiAgentEntityMapper mapper;
    private AiAgentRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        mapper = new AiAgentEntityMapper(new ObjectMapper());
        adapter = new AiAgentRepositoryAdapter(
                jpaRepository,
                mapper,
                companyJpaRepository,
                scenarioJpaRepository,
                ttsVoiceJpaRepository
        );
    }

    @Test
    void create_mapsHandbookPronunciationAndAmbienceToRelationalStructures() {
        long companyId = 1L;
        CompanyEntity company = new CompanyEntity();
        company.setId(companyId);
        when(companyJpaRepository.getReferenceById(companyId)).thenReturn(company);

        AiAgent agent = AiAgent.builder()
                .name("Support Assistant")
                .companyId(companyId)
                .handbook(EnumSet.of(HandbookRule.HIGH_EMPATHY, HandbookRule.CONFIRM_DETAILS))
                .pronunciationRules(List.of(
                        new PronunciationRule("MChJ", "mas'uliyati cheklangan jamiyat", false, "uz-UZ"),
                        new PronunciationRule("CRM", "si-ar-em", true, "*")
                ))
                .ambience(new AiAgentAmbience(
                        AmbientSound.CALL_CENTER,
                        0.8,
                        2.0,
                        AmbientSound.KEYBOARD_TYPING,
                        0.5,
                        true,
                        NoiseCancellationMode.VOICE_ISOLATION
                ))
                .build();

        AiAgentEntity saved = new AiAgentEntity();
        saved.setId(10L);
        when(jpaRepository.save(any(AiAgentEntity.class))).thenReturn(saved);

        long createdId = adapter.create(agent);

        assertThat(createdId).isEqualTo(10L);

        ArgumentCaptor<AiAgentEntity> captor = ArgumentCaptor.forClass(AiAgentEntity.class);
        verify(jpaRepository).save(captor.capture());
        AiAgentEntity entity = captor.getValue();

        // 1. Handbook rules relational collection
        assertThat(entity.getHandbook())
                .containsExactlyInAnyOrder(HandbookRule.HIGH_EMPATHY, HandbookRule.CONFIRM_DETAILS);

        // 2. Pronunciation rules relational collection
        assertThat(entity.getPronunciationRules()).hasSize(2);
        assertThat(entity.getPronunciationRules().get(0).getWord()).isEqualTo("MChJ");
        assertThat(entity.getPronunciationRules().get(0).getReplacement()).isEqualTo("mas'uliyati cheklangan jamiyat");
        assertThat(entity.getPronunciationRules().get(1).getWord()).isEqualTo("CRM");
        assertThat(entity.getPronunciationRules().get(1).isCaseSensitive()).isTrue();

        // 3. Ambience secondary table mapping
        AiAgentAmbienceEntity ambience = entity.getAmbience();
        assertThat(ambience.getAmbientSound()).isEqualTo(AmbientSound.CALL_CENTER);
        assertThat(ambience.getAmbientSoundVolume()).isEqualTo(0.8);
        assertThat(ambience.getAmbientSoundFadeInSeconds()).isEqualTo(2.0);
        assertThat(ambience.getThinkingSound()).isEqualTo(AmbientSound.KEYBOARD_TYPING);
        assertThat(ambience.getThinkingSoundVolume()).isEqualTo(0.5);
        assertThat(ambience.isNoiseCancellationEnabled()).isTrue();
        assertThat(ambience.getNoiseCancellationMode()).isEqualTo(NoiseCancellationMode.VOICE_ISOLATION);
    }

    @Test
    void find_mapsRelationalHandbookAndPronunciationBackToDomain() {
        long companyId = 1L;
        long agentId = 10L;

        CompanyEntity company = new CompanyEntity();
        company.setId(companyId);

        AiAgentEntity entity = new AiAgentEntity();
        entity.setId(agentId);
        entity.setCompany(company);
        entity.setName("Billing Bot");
        entity.setHandbook(EnumSet.of(HandbookRule.KNOWLEDGE_ONLY, HandbookRule.PROTECT_INSTRUCTIONS));
        entity.setPronunciationRules(List.of(
                new PronunciationRuleEmbeddable("UZS", "so'm", false, "uz-UZ")
        ));
        entity.setAmbience(new AiAgentAmbienceEntity(
                AmbientSound.OFFICE,
                1.0,
                1.5,
                AmbientSound.OFF,
                1.0,
                true,
                NoiseCancellationMode.BACKGROUND_NOISE_SUPPRESSION
        ));

        when(jpaRepository.findByIdAndCompanyId(agentId, companyId)).thenReturn(Optional.of(entity));

        AiAgent agent = adapter.find(companyId, agentId);

        assertThat(agent).isNotNull();
        assertThat(agent.name()).isEqualTo("Billing Bot");
        assertThat(agent.handbook())
                .containsExactlyInAnyOrder(HandbookRule.KNOWLEDGE_ONLY, HandbookRule.PROTECT_INSTRUCTIONS);
        assertThat(agent.pronunciationRules()).hasSize(1);
        assertThat(agent.pronunciationRules().get(0).word()).isEqualTo("UZS");
        assertThat(agent.pronunciationRules().get(0).replacement()).isEqualTo("so'm");
        assertThat(agent.ambience().background()).isEqualTo(AmbientSound.OFFICE);
    }
}
