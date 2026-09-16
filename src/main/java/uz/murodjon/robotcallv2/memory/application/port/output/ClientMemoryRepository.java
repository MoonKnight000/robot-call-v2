package uz.murodjon.robotcallv2.memory.application.port.output;

import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;

import java.time.Instant;

public interface ClientMemoryRepository {

    /** {@code null} when the company has never talked to this phone. */
    ClientMemory findByCompanyIdAndPhone(long companyId, String phone);

    /** Insert or fully replace the row for {@code (companyId, memory.phone())}. */
    ClientMemory upsert(long companyId, ClientMemory memory);

    /** Deletes up to {@code batchSize} rows, across every company, not written since {@code cutoff}. */
    int deleteNotUpdatedSince(Instant cutoff, int batchSize);
}
