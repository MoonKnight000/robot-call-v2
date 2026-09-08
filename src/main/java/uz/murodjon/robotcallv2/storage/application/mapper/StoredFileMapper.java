package uz.murodjon.robotcallv2.storage.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;

@Component
public class StoredFileMapper {

    public StoredFile entityToDomain(StoredFileEntity entity) {
        if (entity == null) {
            return null;
        }
        return new StoredFile(
                entity.getId(),
                entity.getCompanyId(),
                entity.getCategory(),
                entity.getOriginalName(),
                entity.getPath(),
                entity.getBucket(),
                entity.getFormat(),
                entity.getSizeBytes(),
                entity.getCreatedAt()
        );
    }

    public StoredFileEntity domainToEntity(StoredFile domain, CompanyEntity company) {
        if (domain == null) {
            return null;
        }
        StoredFileEntity entity = new StoredFileEntity();
        entity.setId(domain.id());
        entity.setCompany(company);
        entity.setCategory(domain.category());
        entity.setOriginalName(domain.originalName());
        entity.setPath(domain.path());
        entity.setBucket(domain.bucket());
        entity.setFormat(domain.format());
        entity.setSizeBytes(domain.sizeBytes());
        entity.setCreatedAt(domain.createdAt());
        return entity;
    }
}
