package mz.contas.transfer;

import java.math.BigDecimal;
import java.util.Optional;
import mz.contas.common.BadRequestException;
import mz.contas.common.BusinessRuleException;
import mz.contas.common.ConflictException;
import mz.contas.common.NotFoundException;
import mz.contas.domain.Account;
import mz.contas.domain.Movement;
import mz.contas.domain.MovementType;
import mz.contas.domain.Transfer;
import mz.contas.repository.AccountRepository;
import mz.contas.repository.MovementRepository;
import mz.contas.repository.TransferRepository;
import mz.contas.security.AccessGuard;
import mz.contas.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final int MAX_IDEMPOTENCY_KEY = 100;

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final MovementRepository movements;
    private final AccessGuard accessGuard;
    private final TransferLimits limits;

    public TransferService(AccountRepository accounts, TransferRepository transfers,
                           MovementRepository movements, AccessGuard accessGuard, TransferLimits limits) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.movements = movements;
        this.accessGuard = accessGuard;
        this.limits = limits;
    }

    /**
     * Executa a transferência numa única transacção. Qualquer excepção (saldo insuficiente,
     * conta inexistente, falha da base de dados) provoca rollback de tudo: saldos e movimentos.
     */
    @Transactional
    public TransferResult transfer(TransferRequest request, String idempotencyKey, UserPrincipal principal) {
        String key = normalizeKey(idempotencyKey);

        // 1. Idempotência: o mesmo pedido repetido (ex.: após um timeout) não volta a debitar.
        if (key != null) {
            Optional<Transfer> previous = transfers.findByIdempotencyKey(key);
            if (previous.isPresent()) {
                Transfer t = previous.get();
                if (!isSameRequest(t, request, principal)) {
                    throw new ConflictException("CHAVE_IDEMPOTENCIA_REUTILIZADA",
                            "A chave de idempotência já foi usada noutra transferência.");
                }
                return new TransferResult(TransferResponse.from(t), false);
            }
        }

        if (request.contaOrigem().equals(request.contaDestino())) {
            throw new BusinessRuleException("MESMA_CONTA", "A conta de origem e a de destino têm de ser diferentes.");
        }
        // Limite por operação: não depende da base de dados, verifica-se antes de bloquear contas.
        limits.checkPerOperation(request.valor());

        // 2. Bloquear as duas contas sempre pela mesma ordem, para evitar deadlocks
        //    quando A->B e B->A acontecem ao mesmo tempo.
        boolean sourceFirst = request.contaOrigem().compareTo(request.contaDestino()) < 0;
        Account first = lock(sourceFirst ? request.contaOrigem() : request.contaDestino());
        Account second = lock(sourceFirst ? request.contaDestino() : request.contaOrigem());
        Account source = sourceFirst ? first : second;
        Account target = sourceFirst ? second : first;

        // 3. O cliente só pode transferir a partir de uma conta sua.
        accessGuard.assertCanAccess(source, principal);

        // 3b. Limite diário: soma do que já saiu hoje desta conta. Como a conta está bloqueada,
        //     duas transferências simultâneas não conseguem ultrapassar o limite juntas.
        BigDecimal usedToday = movements.sumAmountSince(source.getId(), MovementType.TRANSFERENCIA_DEBITO,
                limits.startOfToday());
        limits.checkDaily(usedToday, request.valor());

        // 4. Regras de negócio no domínio: valor positivo e saldo nunca negativo.
        source.debit(request.valor());
        target.credit(request.valor());

        // 5. Registo da operação e dos dois movimentos do livro-razão.
        Transfer transfer = transfers.save(new Transfer(key, source, target, request.valor(),
                request.descricao().trim(), principal.username()));
        movements.save(Movement.transferDebit(transfer));
        movements.save(Movement.transferCredit(transfer));

        log.info("Transferência {} concluída por {}", transfer.getReference(), principal.username());
        return new TransferResult(TransferResponse.from(transfer), true);
    }

    private Account lock(String number) {
        return accounts.findByAccountNumberForUpdate(number)
                .orElseThrow(() -> new NotFoundException("CONTA_NAO_ENCONTRADA", "Conta " + number + " não encontrada."));
    }

    private static boolean isSameRequest(Transfer t, TransferRequest r, UserPrincipal principal) {
        return t.getCreatedBy().equals(principal.username())
                && t.getSourceAccount().getAccountNumber().equals(r.contaOrigem())
                && t.getTargetAccount().getAccountNumber().equals(r.contaDestino())
                && t.getAmount().compareTo(r.valor()) == 0;
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY) {
            throw new BadRequestException("CHAVE_IDEMPOTENCIA_INVALIDA",
                    "A chave de idempotência tem no máximo " + MAX_IDEMPOTENCY_KEY + " caracteres.");
        }
        return trimmed;
    }
}
