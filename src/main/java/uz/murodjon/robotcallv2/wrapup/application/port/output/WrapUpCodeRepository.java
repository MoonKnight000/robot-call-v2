package uz.murodjon.robotcallv2.wrapup.application.port.output;

import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCode;
import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCodeFilter;

import java.util.List;

public interface WrapUpCodeRepository {

    WrapUpCode create(long companyId, WrapUpCode code);

    WrapUpCode update(long companyId, long id, WrapUpCode code);

    void delete(long companyId, long id);

    WrapUpCode find(long companyId, long id);

    List<WrapUpCode> findAll(long companyId, WrapUpCodeFilter filter);

    long count(long companyId, WrapUpCodeFilter filter);
}
