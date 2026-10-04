package mz.contas.security;

import mz.contas.domain.Role;

/** Identidade do utilizador autenticado, reconstruída a partir do token JWT em cada pedido. */
public record UserPrincipal(Long userId, String username, Role role, Long customerId) {
    public boolean isAdmin() { return role == Role.ADMIN; }
}
