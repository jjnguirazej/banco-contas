package mz.contas.transfer;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank(message = "A conta de origem é obrigatória") String contaOrigem,
        @NotBlank(message = "A conta de destino é obrigatória") String contaDestino,

        @NotNull(message = "O valor é obrigatório")
        @Positive(message = "O valor tem de ser positivo")
        @Digits(integer = 17, fraction = 2, message = "O valor aceita no máximo 2 casas decimais")
        BigDecimal valor,

        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 200, message = "A descrição tem no máximo 200 caracteres")
        String descricao) {
}
