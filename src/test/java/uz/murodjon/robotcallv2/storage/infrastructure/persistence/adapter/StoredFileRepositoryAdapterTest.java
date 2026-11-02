package uz.murodjon.robotcallv2.storage.infrastructure.persistence.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.mapper.StoredFileEntityMapper;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.repository.StoredFileJpaRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StoredFileRepositoryAdapterTest {

    private StoredFileJpaRepository jpaRepository;
    private StoredFileEntityMapper mapper;
    private CompanyJpaRepository companyJpaRepository;
    private StoredFileRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(StoredFileJpaRepository.class);
        mapper = new StoredFileEntityMapper();
        companyJpaRepository = mock(CompanyJpaRepository.class);
        adapter = new StoredFileRepositoryAdapter(jpaRepository, mapper, companyJpaRepository);
    }

    @Test
    void createWithPositiveCompanySetsCompanyReference() {
        CompanyEntity company = new CompanyEntity();
        company.setId(10L);
        when(companyJpaRepository.getReferenceById(10L)).thenReturn(company);

        StoredFileEntity saved = new StoredFileEntity();
        saved.setId(101L);
        when(jpaRepository.save(any(StoredFileEntity.class))).thenReturn(saved);

        long id = adapter.create(10L, FileCategory.IMAGE, "logo.png", "com-10/image/logo.png", "files", "image/png", 500L);

        assertThat(id).isEqualTo(101L);
        ArgumentCaptor<StoredFileEntity> captor = ArgumentCaptor.forClass(StoredFileEntity.class);
        verify(jpaRepository).save(captor.capture());
        StoredFileEntity entity = captor.getValue();
        assertThat(entity.getCompany()).isSameAs(company);
        assertThat(entity.getCompanyId()).isEqualTo(10L);
        assertThat(entity.getOriginalName()).isEqualTo("logo.png");
    }

    @Test
    void createWithZeroCompanyLeavesCompanyNullForPlatformAssets() {
        StoredFileEntity saved = new StoredFileEntity();
        saved.setId(202L);
        when(jpaRepository.save(any(StoredFileEntity.class))).thenReturn(saved);

        long id = adapter.create(0L, FileCategory.IMAGE, "icon.png", "platform/image/icon.png", "files", "image/png", 300L);

        assertThat(id).isEqualTo(202L);
        verifyNoInteractions(companyJpaRepository);
        ArgumentCaptor<StoredFileEntity> captor = ArgumentCaptor.forClass(StoredFileEntity.class);
        verify(jpaRepository).save(captor.capture());
        StoredFileEntity entity = captor.getValue();
        assertThat(entity.getCompany()).isNull();
        assertThat(entity.getCompanyId()).isEqualTo(0L);
    }

    @Test
    void findMapsPlatformFileWithNullCompanyToZeroCompanyId() {
        StoredFileEntity entity = new StoredFileEntity();
        entity.setId(303L);
        entity.setCompany(null);
        entity.setCategory(FileCategory.IMAGE);
        entity.setOriginalName("icon.png");
        entity.setPath("platform/image/icon.png");
        entity.setBucket("files");
        entity.setFormat("image/png");
        entity.setSizeBytes(300L);

        when(jpaRepository.findById(303L)).thenReturn(Optional.of(entity));

        StoredFile found = adapter.find(303L);
        assertThat(found).isNotNull();
        assertThat(found.id()).isEqualTo(303L);
        assertThat(found.companyId()).isEqualTo(0L);
    }
}
