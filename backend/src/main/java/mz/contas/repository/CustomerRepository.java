package mz.contas.repository;

import java.util.Optional;
import mz.contas.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByNuit(String nuit);
}
