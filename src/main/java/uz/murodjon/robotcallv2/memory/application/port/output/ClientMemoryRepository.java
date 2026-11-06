package uz.murodjon.robotcallv2.memory.application.port.output;

import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;

import java.time.Instant;

public interface ClientMemoryRepository {

    /** {@code null} when the company has never talked to this phone. */
    ClientMemory findByCompanyIdAndPhone(long companyId, String phone);

    /**
     * The row for {@code (companyId, phone)}, locked until the surrounding transaction ends — an
     * empty row is created first when there is none, so two writers on a new client queue up on
     * the same row instead of racing to insert it. Must be called inside a transaction.
     */
    ClientMemory lockByCompanyIdAndPhone(long companyId, String phone);

    /** Insert or fully replace the row for {@code (companyId, memory.phone())}. */
    ClientMemory upsert(long companyId, ClientMemory memory);

    /** Removes the row for {@code (companyId, phone)}; whether there was one. */
    boolean deleteByCompanyIdAndPhone(long companyId, String phone);

    /** Deletes up to {@code batchSize} rows, across every company, not written since {@code cutoff}. */
    int deleteNotUpdatedSince(Instant cutoff, int batchSize);
}
