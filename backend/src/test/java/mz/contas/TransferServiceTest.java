package mz.contas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import mz.contas.account.AccountService;
import mz.contas.account.CreateAccountRequest;
import mz.contas.common.BusinessRuleException;
import mz.contas.common.NotFoundException;
import mz.contas.domain.AccountType;
import mz.contas.domain.Role;
import mz.contas.repository.AccountRepository;
import mz.contas.repository.CustomerRepository;
import mz.contas.repository.MovementRepository;
import mz.contas.security.UserPrincipal;
import mz.contas.transfer.TransferRequest;
import mz.contas.transfer.TransferResult;
import mz.contas.transfer.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

class TransferServiceTest extends IntegrationTestBase {

    private static final UserPrincipal ADMIN = new UserPrincipal(1L, "admin", Role.ADMIN, null);

    @Autowired AccountService accountService;
    @Autowired TransferService transferService;
    @Autowired AccountRepository accounts;
    @Autowired CustomerRepository customers;
    @Autowired MovementRepository movements;

    @Test
    void transferenciaActualizaSaldosERegistaDoisMovimentos() {
        String a = newAccount("1000.00");
        String b = newAccount("0");

        transferService.transfer(new TransferRequest(a, b, new BigDecimal("250.00"), "Renda"), null, ADMIN);

        assertBalance(a, "750.00");
        assertBalance(b, "250.00");
        assertThat(movements.countByAccountId(id(a))).isEqualTo(2); // saldo inicial + débito
        assertThat(movements.countByAccountId(id(b))).isEqualTo(1); // crédito
    }

    @Test
    void saldoInsuficienteNaoAlteraNada() {
        String a = newAccount("100.00");
        String b = newAccount("0");

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(a, b, new BigDecimal("500.00"), "Tentativa"), null, ADMIN))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Saldo insuficiente");

        assertBalance(a, "100.00");
        assertBalance(b, "0.00");
        assertThat(movements.countByAccountId(id(b))).isZero();
    }

    @Test
    void contaDestinoInexistenteFazRollback() {
        String a = newAccount("100.00");

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(a, "9999999999999", new BigDecimal("10.00"), "Erro"), null, ADMIN))
                .isInstanceOf(NotFoundException.class);

        assertBalance(a, "100.00");
        assertThat(movements.countByAccountId(id(a))).isEqualTo(1);
    }

    @Test
    void pedidoRepetidoComMesmaChaveNaoDebitaDuasVezes() {
        String a = newAccount("1000.00");
        String b = newAccount("0");
        String key = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(a, b, new BigDecimal("300.00"), "Pagamento");

        TransferResult first = transferService.transfer(request, key, ADMIN);
        TransferResult second = transferService.transfer(request, key, ADMIN);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.transfer().referencia()).isEqualTo(first.transfer().referencia());
        assertBalance(a, "700.00");
    }

    @Test
    void clienteNaoPodeTransferirDeContaAlheia() {
        String a = newAccount("1000.00");
        String nuitB = randomNuit();
        String b = newAccount("0", nuitB);
        UserPrincipal clienteB = new UserPrincipal(99L, nuitB, Role.CLIENT, customers.findByNuit(nuitB).orElseThrow().getId());

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(a, b, new BigDecimal("100.00"), "Fraude"), null, clienteB))
                .isInstanceOf(AccessDeniedException.class);

        assertBalance(a, "1000.00");
    }

    @Test
    void valorAcimaDoLimitePorOperacaoERecusado() {
        String a = newAccount("300000.00");
        String b = newAccount("0");

        // Limite por operação padrão: 100 000,00
        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(a, b, new BigDecimal("100000.01"), "Acima do limite"), null, ADMIN))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("LIMITE_POR_OPERACAO");

        assertBalance(a, "300000.00");
    }

    @Test
    void limiteDiarioSomaTodasAsTransferenciasDoDia() {
        String a = newAccount("400000.00");
        String b = newAccount("0");

        // Limite diário padrão: 250 000,00 — 100 000 + 100 000 + 50 000 esgotam o dia.
        transferService.transfer(new TransferRequest(a, b, new BigDecimal("100000.00"), "1.ª"), null, ADMIN);
        transferService.transfer(new TransferRequest(a, b, new BigDecimal("100000.00"), "2.ª"), null, ADMIN);
        transferService.transfer(new TransferRequest(a, b, new BigDecimal("50000.00"), "3.ª"), null, ADMIN);

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(a, b, new BigDecimal("0.01"), "4.ª"), null, ADMIN))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("LIMITE_DIARIO_EXCEDIDO");

        assertBalance(a, "150000.00");
    }

    @Test
    void transferenciasConcorrentesNuncaDeixamSaldoNegativo() throws Exception {
        String a = newAccount("1000.00");
        String b = newAccount("0");
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    transferService.transfer(new TransferRequest(a, b, new BigDecimal("200.00"), "Concorrente"), null, ADMIN);
                    ok.incrementAndGet();
                } catch (BusinessRuleException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(ok.get()).isEqualTo(5);
        assertThat(rejected.get()).isEqualTo(5);
        assertBalance(a, "0.00");
        assertBalance(b, "1000.00");
    }

    private String newAccount(String balance) {
        return newAccount(balance, randomNuit());
    }

    private String newAccount(String balance, String nuit) {
        return accountService.create(new CreateAccountRequest("Titular " + nuit, nuit, null,
                AccountType.ORDEM, new BigDecimal(balance), "Teste@1234")).numeroConta();
    }

    private Long id(String number) {
        return accounts.findByAccountNumber(number).orElseThrow().getId();
    }

    private void assertBalance(String number, String expected) {
        assertThat(accounts.findByAccountNumber(number).orElseThrow().getBalance())
                .isEqualByComparingTo(expected);
    }
}
