package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity.KnowledgeItemEntity;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.mapper.KnowledgeItemEntityMapper;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.repository.KnowledgeItemJpaRepository;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeBaseRepositoryAdapterTest {

    private static final long COMPANY_ID = 1L;

    private KnowledgeItemJpaRepository jpaRepository;
    private KnowledgeItemEntityMapper mapper;
    private CompanyJpaRepository companyJpaRepository;
    private AiAgentJpaRepository aiAgentJpaRepository;
    private KnowledgeBaseRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(KnowledgeItemJpaRepository.class);
        mapper = new KnowledgeItemEntityMapper();
        companyJpaRepository = mock(CompanyJpaRepository.class);
        aiAgentJpaRepository = mock(AiAgentJpaRepository.class);
        adapter = new KnowledgeBaseRepositoryAdapter(jpaRepository, mapper, companyJpaRepository, aiAgentJpaRepository);
    }

    @Test
    void savePersistsTranslationsInAnswersMap() {
        CompanyEntity company = new CompanyEntity();
        company.setId(COMPANY_ID);
        when(companyJpaRepository.getReferenceById(COMPANY_ID)).thenReturn(company);

        KnowledgeItem item = new KnowledgeItem(0L, COMPANY_ID, null, "refund_policy", "billing",
                "Pulni qaytarish", "3 kun ichida", "В течение 3 дней", "Within 3 days",
                "refund,pul", true, null, null);

        ArgumentCaptor<KnowledgeItemEntity> captor = ArgumentCaptor.forClass(KnowledgeItemEntity.class);
        when(jpaRepository.save(captor.capture())).thenAnswer(inv -> {
            KnowledgeItemEntity saved = inv.getArgument(0);
            saved.setId(101L);
            return saved;
        });

        KnowledgeItem result = adapter.save(item);

        assertThat(result.id()).isEqualTo(101L);
        KnowledgeItemEntity entity = captor.getValue();
        assertThat(entity.getAnswers()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "uz", "3 kun ichida",
                "ru", "В течение 3 дней",
                "en", "Within 3 days"
        ));
        assertThat(entity.getAnswerUz()).isEqualTo("3 kun ichida");
        assertThat(entity.getAnswerRu()).isEqualTo("В течение 3 дней");
        assertThat(entity.getAnswerEn()).isEqualTo("Within 3 days");
    }

    @Test
    void findByIdMapsTranslationsBackToDomain() {
        KnowledgeItemEntity entity = new KnowledgeItemEntity();
        entity.setId(101L);
        entity.setCompany(new CompanyEntity());
        entity.setItemKey("delivery");
        entity.setTopic("delivery");
        entity.setTitle("Yetkazib berish");
        entity.setAnswers(Map.of(
                "uz", "1-2 ish kuni",
                "ru", "1-2 рабочих дня"
        ));
        entity.setKeywords("delivery");
        entity.setActive(true);

        when(jpaRepository.findByIdAndCompanyId(101L, COMPANY_ID)).thenReturn(Optional.of(entity));

        Optional<KnowledgeItem> found = adapter.findByIdAndCompanyId(101L, COMPANY_ID);

        assertThat(found).isPresent();
        KnowledgeItem item = found.get();
        assertThat(item.answerUz()).isEqualTo("1-2 ish kuni");
        assertThat(item.answerRu()).isEqualTo("1-2 рабочих дня");
        assertThat(item.answerEn()).isNull();
        assertThat(item.answerForLanguage("uz-UZ")).isEqualTo("1-2 ish kuni");
        assertThat(item.answerForLanguage("ru-RU")).isEqualTo("1-2 рабочих дня");
        assertThat(item.answerForLanguage("en-US")).isEqualTo("1-2 ish kuni"); // fallback to uz
    }
}
