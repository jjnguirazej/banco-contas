package mz.contas.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import mz.contas.domain.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @EntityGraph(attributePaths = "customer")
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Lê a conta e bloqueia a linha (SELECT ... FOR UPDATE) até ao fim da transacção.
     * Outra transferência sobre a mesma conta espera, o que impede saldos negativos em concorrência.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber = :number")
    Optional<Account> findByAccountNumberForUpdate(@Param("number") String number);

    boolean existsByAccountNumber(String accountNumber);

    @EntityGraph(attributePaths = "customer")
    List<Account> findByCustomerIdOrderByCreatedAtAsc(Long customerId);

    @Override
    @EntityGraph(attributePaths = "customer")
    Page<Account> findAll(Pageable pageable);

    @Query(value = "select nextval('account_number_seq')", nativeQuery = true)
    long nextAccountSequence();
}

