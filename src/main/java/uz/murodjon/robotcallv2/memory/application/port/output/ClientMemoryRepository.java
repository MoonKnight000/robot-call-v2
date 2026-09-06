package uz.murodjon.robotcallv2.memory.application.port.output;

import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;

public interface ClientMemoryRepository {

    /** {@code null} when the company has never talked to this phone. */
    ClientMemory findByCompanyIdAndPhone(long companyId, String phone);

    /** Insert or fully replace the row for {@code (companyId, memory.phone())}. */
    ClientMemory upsert(long companyId, ClientMemory memory);
}
