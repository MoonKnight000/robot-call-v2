package uz.murodjon.robotcallv2.contact.infrastructure.persistence.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity.ContactEntity;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.mapper.ContactEntityMapper;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.repository.ContactJpaRepository;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContactRepositoryAdapterTest {

    private ContactJpaRepository contactJpaRepository;
    private CompanyJpaRepository companyJpaRepository;
    private ContactEntityMapper mapper;
    private ContactRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        contactJpaRepository = mock(ContactJpaRepository.class);
        companyJpaRepository = mock(CompanyJpaRepository.class);
        mapper = new ContactEntityMapper();
        adapter = new ContactRepositoryAdapter(contactJpaRepository, companyJpaRepository, mapper);
    }

    @Test
    void createConvertsCommaSeparatedTagsToSet() {
        CompanyEntity company = new CompanyEntity();
        company.setId(1L);
        when(companyJpaRepository.getReferenceById(1L)).thenReturn(company);

        Contact input = Contact.of("Ali Valiyev", "+998901234567", "Toshkent", "vip, qarzdor", "Eslatma");

        ArgumentCaptor<ContactEntity> captor = ArgumentCaptor.forClass(ContactEntity.class);
        when(contactJpaRepository.save(captor.capture())).thenAnswer(inv -> {
            ContactEntity saved = inv.getArgument(0);
            saved.setId(101L);
            return saved;
        });

        long id = adapter.create(1L, input);

        assertThat(id).isEqualTo(101L);
        ContactEntity savedEntity = captor.getValue();
        assertThat(savedEntity.getTags()).containsExactlyInAnyOrder("vip", "qarzdor");
        assertThat(savedEntity.getTagsString()).contains("vip");
        assertThat(savedEntity.getTagsString()).contains("qarzdor");
    }

    @Test
    void findMapsTagSetBackToCommaSeparatedString() {
        ContactEntity entity = new ContactEntity();
        entity.setId(101L);
        entity.setName("Ali Valiyev");
        entity.setPhone("+998901234567");
        entity.setAddress("Toshkent");
        entity.setTags(Set.of("vip", "qarzdor"));

        when(contactJpaRepository.findByIdAndCompanyId(101L, 1L)).thenReturn(Optional.of(entity));

        Contact contact = adapter.find(1L, 101L);

        assertThat(contact).isNotNull();
        assertThat(contact.name()).isEqualTo("Ali Valiyev");
        assertThat(contact.tags()).contains("vip");
        assertThat(contact.tags()).contains("qarzdor");
    }

    @Test
    void updateUpdatesTagsCorrectly() {
        ContactEntity entity = new ContactEntity();
        entity.setId(101L);
        entity.setName("Ali");
        entity.setTags(Set.of("old_tag"));

        when(contactJpaRepository.findByIdAndCompanyId(101L, 1L)).thenReturn(Optional.of(entity));

        Contact update = Contact.of("Ali", "+998901234567", "Toshkent", "new_tag, vip", null);
        adapter.update(1L, 101L, update);

        verify(contactJpaRepository).save(entity);
        assertThat(entity.getTags()).containsExactlyInAnyOrder("new_tag", "vip");
    }
}
