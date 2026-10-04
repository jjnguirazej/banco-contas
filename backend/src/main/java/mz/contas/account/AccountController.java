package mz.contas.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import mz.contas.common.PageResponse;
import mz.contas.security.UserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contas")
@Tag(name = "Contas")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Criar conta (Administrador)")
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse created = accountService.create(request);
        return ResponseEntity.created(URI.create("/api/contas/" + created.numeroConta())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar todas as contas (Administrador)")
    public PageResponse<AccountResponse> list(@RequestParam(defaultValue = "0") int pagina,
                                              @RequestParam(defaultValue = "20") int tamanho) {
        return accountService.list(pagina, tamanho);
    }

    @GetMapping("/minhas")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Listar as contas do cliente autenticado (Cliente)")
    public List<AccountResponse> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return accountService.myAccounts(principal);
    }

    @GetMapping("/{numero}")
    @Operation(summary = "Consultar conta", description = "O cliente só pode consultar as suas contas.")
    public AccountResponse get(@PathVariable String numero, @AuthenticationPrincipal UserPrincipal principal) {
        return accountService.get(numero, principal);
    }

    @GetMapping("/{numero}/saldo")
    @Operation(summary = "Consultar saldo")
    public BalanceResponse balance(@PathVariable String numero, @AuthenticationPrincipal UserPrincipal principal) {
        return accountService.balance(numero, principal);
    }

    @GetMapping(value = "/{numero}/extracto/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descarregar o extracto em PDF",
            description = "Mesmo filtro de datas do extracto. Inclui saldo inicial, totais e saldo final do período.")
    public ResponseEntity<byte[]> statementPdf(@PathVariable String numero,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        StatementPdf pdf = accountService.statementPdf(numero, de, ate, principal);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .body(pdf.content());
    }

    @GetMapping("/{numero}/limites")
    @Operation(summary = "Consultar limites de transferência e o disponível hoje")
    public LimitsResponse limits(@PathVariable String numero, @AuthenticationPrincipal UserPrincipal principal) {
        return accountService.limits(numero, principal);
    }

    @GetMapping("/{numero}/extracto")
    @Operation(summary = "Consultar extracto de movimentos",
            description = "Filtro opcional por período (datas no fuso de Moçambique). Ordenado do mais recente para o mais antigo.")
    public StatementResponse statement(@PathVariable String numero,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                       @RequestParam(defaultValue = "0") int pagina,
                                       @RequestParam(defaultValue = "20") int tamanho,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return accountService.statement(numero, de, ate, pagina, tamanho, principal);
    }
}
