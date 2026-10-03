package mz.contas.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import mz.contas.domain.Movement;
import mz.contas.domain.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovementRepository extends JpaRepository<Movement, Long> {

    @EntityGraph(attributePaths = {"transfer", "transfer.sourceAccount", "transfer.targetAccount"})
    @Query(value = """
            select m from Movement m
            where m.account.id = :accountId and m.createdAt >= :from and m.createdAt < :to
            order by m.createdAt desc, m.id desc
            """,
           countQuery = """
            select count(m) from Movement m
            where m.account.id = :accountId and m.createdAt >= :from and m.createdAt < :to
            """)
    Page<Movement> findStatement(@Param("accountId") Long accountId,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to,
                                 Pageable pageable);

    long countByAccountId(Long accountId);

    /** Movimentos de um período em ordem cronológica, para o extracto em PDF. */
    @EntityGraph(attributePaths = {"transfer", "transfer.sourceAccount", "transfer.targetAccount"})
    @Query("""
            select m from Movement m
            where m.account.id = :accountId and m.createdAt >= :from and m.createdAt < :to
            order by m.createdAt asc, m.id asc
            """)
    List<Movement> findForExport(@Param("accountId") Long accountId,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to,
                                 Pageable pageable);

    /** Último movimento antes de uma data: dá o saldo no início do período. */
    Optional<Movement> findFirstByAccountIdAndCreatedAtBeforeOrderByCreatedAtDescIdDesc(Long accountId, Instant before);

    /** Soma dos movimentos de um tipo desde uma data (usado no limite diário). */
    @Query("""
            select coalesce(sum(m.amount), 0) from Movement m
            where m.account.id = :accountId and m.type = :type and m.createdAt >= :since
            """)
    BigDecimal sumAmountSince(@Param("accountId") Long accountId,
                              @Param("type") MovementType type,
                              @Param("since") Instant since);
}
