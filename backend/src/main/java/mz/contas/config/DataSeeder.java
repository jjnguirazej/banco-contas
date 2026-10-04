package mz.contas.config;

import java.math.BigDecimal;
import mz.contas.account.AccountService;
import mz.contas.account.CreateAccountRequest;
import mz.contas.domain.AccountType;
import mz.contas.domain.AppUser;
import mz.contas.domain.Role;
import mz.contas.repository.AppUserRepository;
import mz.contas.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Administrador: criado só se ainda não existir nenhum. As credenciais vêm de
 * ADMIN_USERNAME e ADMIN_PASSWORD, e não ficam nas migrações. O valor por defeito
 * em application.yml serve apenas para desenvolvimento: em produção, definir sempre ADMIN_PASSWORD.
 *
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppUserRepository users;
    private final CustomerRepository customers;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final boolean seedDemo;

    public DataSeeder(AppUserRepository users, CustomerRepository customers, AccountService accountService,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.admin.username}") String adminUsername,
                      @Value("${app.admin.password}") String adminPassword,
                      @Value("${app.seed.demo}") boolean seedDemo) {
        this.users = users;
        this.customers = customers;
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.seedDemo = seedDemo;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Verifica por perfil e não por username: se o administrador mudar de nome,
        // não é criado um segundo administrador no arranque seguinte.
        if (!users.existsByRole(Role.ADMIN)) {
            users.save(AppUser.admin(adminUsername, passwordEncoder.encode(adminPassword)));
            log.info("Administrador '{}' criado", adminUsername);
        }

        // Só com a base de dados vazia, para não duplicar dados a cada arranque.
        // As contas são criadas pelo AccountService para seguir as mesmas regras da API
        // (validação do NUIT, movimento de saldo inicial e criação do acesso do cliente).
        // Palavra-passe de demonstração (Cliente@123)
        
        if (seedDemo && customers.count() == 0) {
            accountService.create(new CreateAccountRequest("Jorge Nguiraze", "100000001", null,
                    AccountType.ORDEM, new BigDecimal("50000.00"), "Cliente@123"));
            accountService.create(new CreateAccountRequest("Jorge Nguiraze", "100000001", null,
                    AccountType.POUPANCA, new BigDecimal("120000.00"), null));
            accountService.create(new CreateAccountRequest("Carlos Sitoe", "100000002", null,
                    AccountType.ORDEM, new BigDecimal("15000.00"), "Cliente@123"));
            accountService.create(new CreateAccountRequest("Nesia Dalila", "100000003", null,
                    AccountType.ORDEM, new BigDecimal("150000.00"), "Cliente@123"));
            accountService.create(new CreateAccountRequest("Nesia Dalila", "100000003", null,
                    AccountType.POUPANCA, new BigDecimal("13000.00"), "Cliente@123"));
            accountService.create(new CreateAccountRequest("Lurdes Jaime", "100000005", null,
                    AccountType.POUPANCA, new BigDecimal("33000.00"), "Cliente@123"));
            accountService.create(new CreateAccountRequest("Lurdes Jaime", "100000005", null,
                    AccountType.ORDEM, new BigDecimal("43000.00"), "Cliente@123"));

            
            log.info("Dados de demonstração criados");
        }
    }
}
