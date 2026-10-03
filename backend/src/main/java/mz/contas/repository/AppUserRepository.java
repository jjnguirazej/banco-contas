package mz.contas.repository;

import java.util.Optional;
import mz.contas.domain.AppUser;
import mz.contas.domain.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @EntityGraph(attributePaths = "customer")
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRole(Role role);
}

