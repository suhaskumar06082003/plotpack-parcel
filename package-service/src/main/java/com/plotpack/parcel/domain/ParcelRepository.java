package com.plotpack.parcel.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ParcelRepository extends JpaRepository<Parcel, Long> {

    Optional<Parcel> findByIdempotencyKey(String idempotencyKey);

    List<Parcel> findByTokenOrderByBinCodeAscIdAsc(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Parcel p WHERE p.token = :token AND p.status = com.plotpack.parcel.domain.Parcel.Status.ALLOCATED ORDER BY p.id")
    List<Parcel> lockWaitingByToken(String token);

    Optional<Parcel> findFirstByCustomerIdAndStatus(Long customerId, Parcel.Status status);

    boolean existsByTokenAndStatus(String token, Parcel.Status status);

    List<Parcel> findByStatusOrderByIdAsc(Parcel.Status status);

    long countByStatus(Parcel.Status status);

    long countByStatusAndDeliveredAtAfter(Parcel.Status status, Instant after);
}
