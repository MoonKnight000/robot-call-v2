package uz.murodjon.robotcallv2.contact.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity.ContactCrmLinkEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContactCrmLinkJpaRepository extends JpaRepository<ContactCrmLinkEntity, Long> {

    @Query("SELECT l FROM ContactCrmLinkEntity l WHERE l.companyId = :companyId "
            + "AND l.provider = :provider AND l.externalId = :externalId")
    Optional<ContactCrmLinkEntity> findByExternalId(@Param("companyId") long companyId,
                                                    @Param("provider") String provider,
                                                    @Param("externalId") String externalId);

    @Query("SELECT l FROM ContactCrmLinkEntity l WHERE l.companyId = :companyId AND l.contactId IN :contactIds")
    List<ContactCrmLinkEntity> findByContactIds(@Param("companyId") long companyId,
                                                @Param("contactIds") Collection<Long> contactIds);

    /** Frees the CRM record from another contact, so the unique (company, provider, external id) holds. */
    @Modifying
    @Query("DELETE FROM ContactCrmLinkEntity l WHERE l.companyId = :companyId AND l.provider = :provider "
            + "AND l.externalId = :externalId AND l.contactId <> :contactId")
    void deleteOtherLinks(@Param("companyId") long companyId, @Param("provider") String provider,
                          @Param("externalId") String externalId, @Param("contactId") long contactId);

    @Modifying
    @Query("UPDATE ContactCrmLinkEntity l SET l.pushRequestedAt = :at, l.pushNextAt = :at, l.pushAttempts = 0 "
            + "WHERE l.companyId = :companyId AND l.contactId = :contactId")
    int requestPush(@Param("companyId") long companyId, @Param("contactId") long contactId, @Param("at") Instant at);

    @Query("SELECT l FROM ContactCrmLinkEntity l WHERE l.pushRequestedAt IS NOT NULL AND l.pushNextAt <= :now "
            + "ORDER BY l.pushNextAt")
    List<ContactCrmLinkEntity> findDuePushes(@Param("now") Instant now, Pageable pageable);

    /** Clears the request that was pushed; a newer one (another requestedAt) is left alone. */
    @Modifying
    @Query("UPDATE ContactCrmLinkEntity l SET l.pushRequestedAt = NULL, l.pushNextAt = NULL, l.pushAttempts = 0, "
            + "l.pushError = NULL, l.pushedAt = :at WHERE l.contactId = :contactId AND l.pushRequestedAt = :requestedAt")
    int clearPush(@Param("contactId") long contactId, @Param("requestedAt") Instant requestedAt, @Param("at") Instant at);

    /** Records a failure of the request that was tried; due again at {@code nextAt}. */
    @Modifying
    @Query("UPDATE ContactCrmLinkEntity l SET l.pushAttempts = :attempts, l.pushNextAt = :nextAt, l.pushError = :error "
            + "WHERE l.contactId = :contactId AND l.pushRequestedAt = :requestedAt")
    int retryPush(@Param("contactId") long contactId, @Param("requestedAt") Instant requestedAt,
                  @Param("attempts") int attempts, @Param("nextAt") Instant nextAt, @Param("error") String error);

    /** Gives up the request that was tried, keeping why. */
    @Modifying
    @Query("UPDATE ContactCrmLinkEntity l SET l.pushRequestedAt = NULL, l.pushNextAt = NULL, "
            + "l.pushAttempts = :attempts, l.pushError = :error "
            + "WHERE l.contactId = :contactId AND l.pushRequestedAt = :requestedAt")
    int abandonPush(@Param("contactId") long contactId, @Param("requestedAt") Instant requestedAt,
                    @Param("attempts") int attempts, @Param("error") String error);

    /** A request newer than the one tried: due now, its own attempt count. */
    @Modifying
    @Query("UPDATE ContactCrmLinkEntity l SET l.pushNextAt = :now, l.pushAttempts = 0 "
            + "WHERE l.contactId = :contactId AND l.pushRequestedAt IS NOT NULL AND l.pushRequestedAt <> :requestedAt")
    int dueNewerPush(@Param("contactId") long contactId, @Param("requestedAt") Instant requestedAt,
                     @Param("now") Instant now);
}
