package mz.contas.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Formato estável de paginação, independentemente da implementação interna do Spring Data. */
public record PageResponse<T>(List<T> conteudo, int pagina, int tamanho, long totalElementos, int totalPaginas) {
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
