package mz.contas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import mz.contas.account.AccountService;
import mz.contas.account.CreateAccountRequest;
import mz.contas.domain.AccountType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SecurityTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired AccountService accountService;

    @Test
    void semTokenDevolve401() throws Exception {
        mvc.perform(get("/api/contas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void credenciaisErradasDevolvem401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utilizador\":\"admin\",\"palavraPasse\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clienteSoVeAsSuasContas() throws Exception {
        String nuitA = randomNuit();
        String contaA = createAccount(nuitA);
        String contaB = createAccount(randomNuit());
        String token = login(nuitA, "Cliente@123");

        mvc.perform(get("/api/contas/" + contaA).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/contas/" + contaB + "/extracto").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
    }

    @Test
    void clienteNaoPodeCriarNemListarContas() throws Exception {
        String nuit = randomNuit();
        createAccount(nuit);
        String token = login(nuit, "Cliente@123");

        mvc.perform(get("/api/contas").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/contas").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson(randomNuit())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCriaContaEValidaDados() throws Exception {
        String token = login("admin", "Admin@12345");

        mvc.perform(post("/api/contas").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson(randomNuit())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroConta").exists());

        mvc.perform(post("/api/contas").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson("123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
    }

    @Test
    void bloqueiaLoginAposCincoTentativasFalhadas() throws Exception {
        String nuit = randomNuit();
        createAccount(nuit);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"utilizador\":\"" + nuit + "\",\"palavraPasse\":\"errada\"}"))
                    .andExpect(status().isUnauthorized());
        }
        // Mesmo com a palavra-passe certa, fica bloqueado durante a janela configurada.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utilizador\":\"" + nuit + "\",\"palavraPasse\":\"Cliente@123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.codigo").value("DEMASIADAS_TENTATIVAS"));
    }

    @Test
    void clienteDescarregaOSeuExtractoEmPdf() throws Exception {
        String nuit = randomNuit();
        String conta = createAccount(nuit);
        String token = login(nuit, "Cliente@123");

        byte[] pdf = mvc.perform(get("/api/contas/" + conta + "/extracto/pdf").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("extracto-" + conta)))
                .andReturn().getResponse().getContentAsByteArray();

        org.assertj.core.api.Assertions.assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    private String createAccount(String nuit) {
        return accountService.create(new CreateAccountRequest("Titular " + nuit, nuit, null,
                AccountType.ORDEM, new BigDecimal("500.00"), "Cliente@123")).numeroConta();
    }

    private String login(String user, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"utilizador\":\"" + user + "\",\"palavraPasse\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String accountJson(String nuit) {
        return """
                {"nomeCliente":"Teste API","nuit":"%s","tipo":"ORDEM","saldoInicial":1000.00,"palavraPasseCliente":"Cliente@123"}
                """.formatted(nuit);
    }
}
