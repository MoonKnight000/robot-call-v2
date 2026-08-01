package uz.murodjon.uysotvoice.callrecord.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.callrecord.entity.CallTechnical;

/**
 * Spring Data repository for {@link CallTechnical}. Written exactly once, from {@code
 * CallFinalizer} at teardown (not re-run by the summary outbox like {@code call_result}),
 * so a plain {@code save()} is enough — no upsert/conflict handling needed.
 */
@Repository
public interface CallTechnicalJpaRepository extends JpaRepository<CallTechnical, Long> {
}
