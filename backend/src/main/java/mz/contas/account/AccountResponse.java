package mz.contas.account;

import java.math.BigDecimal;
import java.time.Instant;
import mz.contas.domain.Account;
import mz.contas.domain.AccountType;

public record AccountResponse(String numeroConta, String nomeCliente, String nuit, AccountType tipo,
                              BigDecimal saldo, String moeda, Instant criadaEm) {

    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getAccountNumber(), a.getCustomer().getFullName(), a.getCustomer().getNuit(),
                a.getType(), a.getBalance(), "MZN", a.getCreatedAt());
    }
}
