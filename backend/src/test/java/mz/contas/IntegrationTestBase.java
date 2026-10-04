package mz.contas;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Os testes correm contra um PostgreSQL real (Testcontainers), porque o comportamento
 * de bloqueios e de constraints tem de ser igual ao de produção. O Redis também corre
 * num contentor, para testar o bloqueio de login. Requer Docker.
 */
@SpringBootTest(properties = "app.seed.demo=false")
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    protected static String randomNuit() {
        return String.format("%09d", ThreadLocalRandom.current().nextInt(1, 1_000_000_000));
    }
}

