package mz.contas.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import mz.contas.common.BadRequestException;
import mz.contas.common.BusinessRuleException;
import mz.contas.common.ConflictException;
import mz.contas.common.NotFoundException;
import mz.contas.common.PageResponse;
import mz.contas.domain.Account;
import mz.contas.domain.AppUser;
import mz.contas.domain.Customer;
import mz.contas.domain.Movement;
import mz.contas.domain.MovementType;
import mz.contas.repository.AccountRepository;
import mz.contas.repository.AppUserRepository;
import mz.contas.repository.CustomerRepository;
import mz.contas.repository.MovementRepository;
import mz.contas.security.AccessGuard;
import mz.contas.security.UserPrincipal;
import mz.contas.transfer.TransferLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final long ACCOUNT_NUMBER_BASE = 1_000_000_000_000L;

    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final AppUserRepository users;
    private final MovementRepository movements;
    private final PasswordEncoder passwordEncoder;
    private final AccessGuard accessGuard;
    private final TransferLimits limits;
    private final StatementPdfService pdfService;
    private final ZoneId zone;
    private final int pdfMaxMovements;

    public AccountService(AccountRepository accounts, CustomerRepository customers, AppUserRepository users,
                          MovementRepository movements, PasswordEncoder passwordEncoder, AccessGuard accessGuard,
                          TransferLimits limits, StatementPdfService pdfService,
                          @Value("${app.timezone}") String timezone,
                          @Value("${app.statement.pdf-max-movements}") int pdfMaxMovements) {
        this.accounts = accounts;
        this.customers = customers;
        this.users = users;
        this.movements = movements;
        this.passwordEncoder = passwordEncoder;
        this.accessGuard = accessGuard;
        this.limits = limits;
        this.pdfService = pdfService;
        this.zone = ZoneId.of(timezone);
        this.pdfMaxMovements = pdfMaxMovements;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        Customer customer = customers.findByNuit(request.nuit())
                .map(existing -> requireSameName(existing, request.nomeCliente()))
                .orElseGet(() -> createCustomerWithAccess(request));

        String number = hasText(request.numeroConta()) ? request.numeroConta() : generateAccountNumber();
        if (accounts.existsByAccountNumber(number)) {
            throw new ConflictException("CONTA_DUPLICADA", "Já existe uma conta com o número " + number + ".");
        }

        Account account = accounts.save(new Account(number, customer, request.tipo()));

        BigDecimal initial = request.saldoInicial();
        if (initial.signum() > 0) {
            account.credit(initial);
            movements.save(Movement.initialBalance(account, initial));
        }
        log.info("Conta {} criada para o titular {}", number, customer.getId());
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> list(int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by("createdAt").descending());
        return PageResponse.of(accounts.findAll(pageable), AccountResponse::from);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> myAccounts(UserPrincipal principal) {
        return accounts.findByCustomerIdOrderByCreatedAtAsc(principal.customerId()).stream()
                .map(AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(String number, UserPrincipal principal) {
        return AccountResponse.from(loadAuthorized(number, principal));
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(String number, UserPrincipal principal) {
        Account account = loadAuthorized(number, principal);
        return new BalanceResponse(account.getAccountNumber(), account.getBalance(), "MZN", Instant.now());
    }

    @Transactional(readOnly = true)
    public StatementResponse statement(String number, LocalDate from, LocalDate to, int page, int size,
                                       UserPrincipal principal) {
        validatePeriod(from, to);
        Account account = loadAuthorized(number, principal);
        Instant start = startOf(from);
        Instant end = endOf(to);

        var result = movements.findStatement(account.getId(), start, end,
                PageRequest.of(Math.max(page, 0), clampSize(size)));
        return new StatementResponse(account.getAccountNumber(), account.getCustomer().getFullName(),
                account.getBalance(), "MZN", from, to, PageResponse.of(result, MovementResponse::from));
    }

    /** Extracto de um período em PDF, com saldo inicial, totais e saldo final. */
    @Transactional(readOnly = true)
    public StatementPdf statementPdf(String number, LocalDate from, LocalDate to, UserPrincipal principal) {
        validatePeriod(from, to);
        Account account = loadAuthorized(number, principal);
        Instant start = startOf(from);
        Instant end = endOf(to);

        // Pede mais um do que o máximo: se vier, o período é demasiado longo para um documento.
        List<Movement> list = movements.findForExport(account.getId(), start, end,
                PageRequest.of(0, pdfMaxMovements + 1));
        if (list.size() > pdfMaxMovements) {
            throw new BusinessRuleException("PERIODO_DEMASIADO_LONGO",
                    "O período tem mais de " + pdfMaxMovements + " movimentos. Escolha um intervalo de datas menor.");
        }
        BigDecimal opening = movements
                .findFirstByAccountIdAndCreatedAtBeforeOrderByCreatedAtDescIdDesc(account.getId(), start)
                .map(Movement::getBalanceAfter)
                .orElse(BigDecimal.ZERO.setScale(2));

        byte[] pdf = pdfService.render(account, from, to, opening, list);
        String fileName = "extracto-" + account.getAccountNumber()
                + "-" + (from == null ? "inicio" : from) + "-a-" + (to == null ? LocalDate.now(zone) : to) + ".pdf";
        return new StatementPdf(pdf, fileName);
    }

    /** Limites de transferência da conta e quanto ainda pode sair hoje. */
    @Transactional(readOnly = true)
    public LimitsResponse limits(String number, UserPrincipal principal) {
        Account account = loadAuthorized(number, principal);
        BigDecimal used = movements.sumAmountSince(account.getId(), MovementType.TRANSFERENCIA_DEBITO,
                limits.startOfToday());
        return new LimitsResponse(account.getAccountNumber(), limits.perOperation(), limits.daily(),
                used, limits.available(used), "MZN");
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("PERIODO_INVALIDO", "A data inicial não pode ser posterior à data final.");
        }
    }

    private Instant startOf(LocalDate from) {
        return from == null ? Instant.EPOCH : from.atStartOfDay(zone).toInstant();
    }

    private Instant endOf(LocalDate to) {
        return (to == null ? LocalDate.now(zone) : to).plusDays(1).atStartOfDay(zone).toInstant();
    }

    private Account loadAuthorized(String number, UserPrincipal principal) {
        Account account = accounts.findByAccountNumber(number)
                .orElseThrow(() -> new NotFoundException("CONTA_NAO_ENCONTRADA", "Conta " + number + " não encontrada."));
        accessGuard.assertCanAccess(account, principal);
        return account;
    }

    private Customer requireSameName(Customer existing, String name) {
        if (!existing.getFullName().trim().equalsIgnoreCase(name.trim())) {
            throw new ConflictException("NUIT_COM_OUTRO_TITULAR",
                    "O NUIT indicado já pertence a um titular com outro nome.");
        }
        return existing;
    }

    private Customer createCustomerWithAccess(CreateAccountRequest request) {
        if (!hasText(request.palavraPasseCliente())) {
            throw new BusinessRuleException("PALAVRA_PASSE_OBRIGATORIA",
                    "Para um novo titular, indique a palavra-passe de acesso do cliente.");
        }
        if (users.existsByUsername(request.nuit())) {
            throw new ConflictException("UTILIZADOR_DUPLICADO", "Já existe um utilizador com este NUIT.");
        }
        Customer customer = customers.save(new Customer(request.nomeCliente().trim(), request.nuit()));
        users.save(AppUser.client(request.nuit(), passwordEncoder.encode(request.palavraPasseCliente()), customer));
        return customer;
    }

    private String generateAccountNumber() {
        return String.valueOf(ACCOUNT_NUMBER_BASE + accounts.nextAccountSequence());
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}

