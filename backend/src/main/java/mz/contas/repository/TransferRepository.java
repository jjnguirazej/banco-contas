package mz.contas.repository;

import java.util.Optional;
import mz.contas.domain.Transfer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    @EntityGraph(attributePaths = {"sourceAccount", "targetAccount"})
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
}
