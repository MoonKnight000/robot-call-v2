package uz.murodjon.robotcallv2.company.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.application.dto.CompanyFilter;
import uz.murodjon.robotcallv2.company.application.mapper.CompanyMapper;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyRepository;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class CompanyRepositoryAdapter implements CompanyRepository {

    private final CompanyJpaRepository jpa;
    private final CompanyMapper mapper;

    public CompanyRepositoryAdapter(CompanyJpaRepository jpa, CompanyMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public long create(String name) {
        CompanyEntity entity = new CompanyEntity();
        entity.setName(name);
        entity.setStatus(CompanyStatus.ACTIVE);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    @Override
    public Company find(long id) {
        return jpa.findById(id).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public void update(long id, String name, String address) {
        jpa.findById(id).ifPresent(entity -> {
            entity.setName(name);
            entity.setAddress(address);
            jpa.save(entity);
        });
    }

    @Override
    public void updateLogoFileId(long id, Long logoFileId) {
        jpa.findById(id).ifPresent(entity -> {
            entity.setLogoFileId(logoFileId);
            jpa.save(entity);
        });
    }

    @Override
    public void updateStatus(long id, CompanyStatus status) {
        jpa.findById(id).ifPresent(entity -> {
            entity.setStatus(status);
            jpa.save(entity);
        });
    }

    @Override
    public List<Company> findAll() {
        return jpa.findAll().stream().map(mapper::entityToDomain).toList();
    }

    @Override
    public List<Company> findAll(CompanyFilter filter) {
        return jpa.search(likePattern(filter.search()), filter.pageable()).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public long count(CompanyFilter filter) {
        return jpa.countSearch(likePattern(filter.search()));
    }

    private static String likePattern(String search) {
        return search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
    }
}
