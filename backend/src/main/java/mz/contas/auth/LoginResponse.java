package mz.contas.auth;

import mz.contas.domain.Role;

public record LoginResponse(String token, String tipo, long expiraEmSegundos, Role perfil, String nome) {
}
