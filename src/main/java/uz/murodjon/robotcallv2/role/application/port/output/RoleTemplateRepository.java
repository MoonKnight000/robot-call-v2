package uz.murodjon.robotcallv2.role.application.port.output;

import uz.murodjon.robotcallv2.role.domain.entity.RoleTemplate;

import java.util.List;

public interface RoleTemplateRepository {

    List<RoleTemplate> findAll();

    RoleTemplate find(long id);

    boolean existsByName(String name, Long excludeId);

    long count();

    /** Stores code, name, description, assignability and permissions; id and createdAt come back filled. */
    RoleTemplate create(RoleTemplate template);

    /** Name, description, assignability and permissions; the code never changes. Null if the id is gone. */
    RoleTemplate update(RoleTemplate template);

    void delete(long id);
}
