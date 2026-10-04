package mz.contas.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "O utilizador é obrigatório") String utilizador,
        @NotBlank(message = "A palavra-passe é obrigatória") String palavraPasse) {
}
