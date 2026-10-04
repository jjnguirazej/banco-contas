package mz.contas.security;

import mz.contas.domain.Account;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Regra de autorização por recurso: o administrador acede a qualquer conta;
 * o cliente apenas às contas do seu próprio titular. A identidade vem sempre do token,
 * nunca de um valor enviado pelo cliente no pedido.
 */
@Component
public class AccessGuard {

    public void assertCanAccess(Account account, UserPrincipal principal) {
        if (principal.isAdmin()) {
            return;
        }
        Long owner = account.getCustomer().getId();
        if (principal.customerId() == null || !principal.customerId().equals(owner)) {
            throw new AccessDeniedException("Conta não pertence ao utilizador autenticado");
        }
    }
}
