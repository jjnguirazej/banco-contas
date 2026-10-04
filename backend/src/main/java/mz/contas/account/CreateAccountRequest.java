package mz.contas.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import mz.contas.domain.AccountType;

public record CreateAccountRequest(
        @NotBlank(message = "O nome do cliente é obrigatório")
        @Size(max = 150, message = "O nome do cliente tem no máximo 150 caracteres")
        String nomeCliente,

        @NotBlank(message = "O NUIT é obrigatório")
        @Pattern(regexp = "\\d{9}", message = "O NUIT deve ter exactamente 9 dígitos")
        String nuit,

        @Schema(description = "Opcional. Se omitido, é gerado automaticamente.", example = "1000000000001")
        @Pattern(regexp = "\\d{8,20}", message = "O número da conta deve ter entre 8 e 20 dígitos")
        String numeroConta,

        @NotNull(message = "O tipo de conta é obrigatório (ORDEM ou POUPANCA)")
        AccountType tipo,

        @NotNull(message = "O saldo inicial é obrigatório")
        @PositiveOrZero(message = "O saldo inicial não pode ser negativo")
        @Digits(integer = 17, fraction = 2, message = "O saldo inicial aceita no máximo 2 casas decimais")
        BigDecimal saldoInicial,

        @Schema(description = "Obrigatória quando o NUIT ainda não existe: cria o acesso do cliente (utilizador = NUIT).")
        @Size(min = 8, max = 72, message = "A palavra-passe do cliente deve ter entre 8 e 72 caracteres")
        String palavraPasseCliente) {
}
