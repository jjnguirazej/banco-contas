# Gestão de Contas Bancárias

Módulo de gestão de contas bancárias: criar contas, consultar saldo, transferir entre contas e consultar o extracto, com autenticação JWT e dois perfis (Administrador e Cliente). Inclui uma API REST e uma interface web.

**Backend:** Java 21, Spring Boot 4.1.1, Spring Security, Spring Data JPA/Hibernate, PostgreSQL 16, Flyway, springdoc-openapi (Swagger), JUnit 5 com Testcontainers.

**Frontend:** Angular 20 com TypeScript, componentes standalone, signals e formulários reactivos.

**Infraestrutura:** Docker Compose (PostgreSQL, Redis, API e interface web com nginx).

**Para além dos requisitos:** limite de tentativas de login com Redis, limites de transferência (por operação e diário), extracto em PDF por período e diagramas UML do sistema.

## Arrancar em 1 minuto

Requisito: Docker com Docker Compose.

```bash
docker compose up --build
```

- Interface web: http://localhost:4200
- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html
- Saúde: http://localhost:8080/actuator/health
- Diagramas: `docs/diagramas/Diagramas-Contas-Bancarias.pdf`

O Flyway cria o esquema no arranque. São criados um administrador e dados de demonstração.

| Perfil | Utilizador | Palavra-passe | Contas |
|---|---|---|---|
| Administrador | `admin` | `Admin@12345` | todas |
| Cliente | `100000001` (Jorge Nguiraze) | `Cliente@123` | uma conta à ordem e uma poupança |
| Cliente | `100000002` (Carlos Sitoe) | `Cliente@123` | uma conta à ordem |

O cliente entra com o seu NUIT.

**Dados iniciais.** No arranque, a aplicação cria:

- **O administrador**, só se ainda não existir nenhum. As credenciais vêm de `ADMIN_USERNAME` e `ADMIN_PASSWORD`. Alterar estas variáveis depois do primeiro arranque não muda o administrador que já existe.
- **Os dados de demonstração** (clientes e contas da tabela acima), só com `SEED_DEMO=true` e com a base de dados ainda sem clientes. Arrancar de novo não os duplica. A palavra-passe `Cliente@123` está fixa no código e serve apenas para demonstração.

`SEED_DEMO` vem a `true` por defeito, para facilitar a avaliação. Em produção deve ser `false`, e `ADMIN_PASSWORD` e `JWT_SECRET` devem ser definidos com valores próprios: os valores por defeito do `application.yml` são só para desenvolvimento.

Para voltar ao estado inicial (apaga todos os dados):

    docker compose down -v
    docker compose up --build


### Em modo de desenvolvimento

Requisitos: JDK 21, Maven 3.9+, Node.js 22 e Docker.

```bash
docker compose up -d db redis    # base de dados e Redis

cd backend
mvn spring-boot:run              # API em http://localhost:8080

cd ../frontend
npm install
npm start                        # interface em http://localhost:4200
```

Em desenvolvimento, o `ng serve` encaminha os pedidos `/api` para a API através do `proxy.conf.json`. Em Docker, o nginx faz o mesmo papel.

## Endpoints

| Método | Caminho | Perfil | Descrição |
|---|---|---|---|
| POST | `/api/auth/login` | público | Obter token JWT |
| POST | `/api/contas` | Admin | Criar conta |
| GET | `/api/contas` | Admin | Listar todas as contas (paginado) |
| GET | `/api/contas/minhas` | Cliente | Contas do cliente autenticado |
| GET | `/api/contas/{numero}` | Admin, Cliente (só as suas) | Consultar conta |
| GET | `/api/contas/{numero}/saldo` | Admin, Cliente (só as suas) | Consultar saldo |
| GET | `/api/contas/{numero}/extracto?de=&ate=` | Admin, Cliente (só as suas) | Extracto paginado |
| GET | `/api/contas/{numero}/extracto/pdf?de=&ate=` | Admin, Cliente (só as suas) | Extracto do período em PDF |
| GET | `/api/contas/{numero}/limites` | Admin, Cliente (só as suas) | Limites e disponível hoje |
| POST | `/api/transferencias` | Admin, Cliente (origem tem de ser sua) | Transferir |

### Exemplo

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"utilizador":"admin","palavraPasse":"Admin@12345"}' | jq -r .token)

curl -X POST localhost:8080/api/contas -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"nomeCliente":"Rui Cossa","nuit":"100000003","tipo":"POUPANCA","saldoInicial":2500.00,"palavraPasseCliente":"Cliente@123"}'

curl -X POST localhost:8080/api/transferencias -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" \
  -d '{"contaOrigem":"1000000000001","contaDestino":"1000000000003","valor":1500.00,"descricao":"Pagamento de renda"}'
```

## Colecção Postman

Importe `postman/contas-api.postman_collection.json` no Postman e corra as pastas pela ordem. O login guarda o token automaticamente, e cada pedido tem testes que confirmam o código de resposta esperado, incluindo os casos de erro (400, 401, 403, 409, 422 e 429).

## Arquitectura

```
Browser (Angular) ─► nginx ─► JwtAuthFilter ─► Controller ─► Service ─► Repository ─► PostgreSQL
                     /api      (Spring Security)  (validação)   (regras e      (Spring Data JPA)
                                                                 transacções)
```

No backend, o código está organizado por funcionalidade: `auth`, `account`, `transfer`, com `domain` (entidades), `repository`, `security`, `config` e `common` (erros e paginação). Os controllers só tratam de HTTP e validação; as regras de negócio e as transacções vivem nos services; as invariantes de saldo vivem na entidade `Account`.

No frontend, `core/` tem a autenticação (serviço, interceptor que junta o token, guards por perfil), o acesso à API e os tipos; `features/` tem um componente por ecrã (login, lista de contas, nova conta, as minhas contas, detalhe com extracto, transferência); `shared/` tem a formatação de valores e datas.

## Documentação Swagger e importação no Postman

Com a API a correr, a documentação OpenAPI é gerada automaticamente a partir do código:

- Swagger UI: http://localhost:8080/swagger-ui.html
- Especificação OpenAPI (JSON): http://localhost:8080/v3/api-docs

### Testar no Swagger UI

1. Abra http://localhost:8080/swagger-ui.html.
2. Em **Autenticação → POST /api/auth/login**, clique em **Try it out**, preencha o corpo e clique em **Execute**:
   ```json
   { "utilizador": "admin", "palavraPasse": "Admin@12345" }


## Importar a API no Postman a partir do Swagger

A API publica a sua especificação OpenAPI, gerada automaticamente a partir do código. O Postman consegue criar uma colecção a partir dela, com todos os endpoints e exemplos de JSON.

**1. Arrancar a API** (com Docker ou em modo de desenvolvimento) e confirmar que a especificação responde:

http://localhost:8080/v3/api-docs

**2. Importar no Postman**

1. No Postman, clicar em **Import** (canto superior esquerdo).
2. Colar o endereço `http://localhost:8080/v3/api-docs` na caixa de texto.
3. Escolher **Postman Collection** e clicar em **Import**.

É criada a colecção *Teste Tecnico - API de Gestão de Contas Bancárias*, com uma pasta por grupo: Autenticação, Contas e Transferências.

Sem acesso à API no momento da importação, pode guardar primeiro a especificação num ficheiro e importar o ficheiro (**Import → files**):

```bash
curl -s http://localhost:8080/v3/api-docs -o openapi.json
```

**3. Configurar o endereço**

Na colecção importada, abrir o separador **Variables** e confirmar que `baseUrl` é `http://localhost:8080`.

**4. Obter o token**

Abrir **Autenticação → POST /api/auth/login**, colocar no **Body** e clicar em **Send**:

```json
{ "utilizador": "admin", "palavraPasse": "Admin@12345" }
```

Copiar o valor de `token` da resposta.

**5. Usar o token em todos os pedidos**

1. Clicar no nome da colecção e abrir o separador **Authorization**.
2. Em **Auth Type**, escolher **Bearer Token**.
3. Colar o token no campo **Token** e guardar (**Ctrl+S**).

Todos os pedidos da colecção herdam a autenticação. O token expira ao fim de 1 hora: quando os pedidos começarem a devolver 401, repetir o passo 4. Para testar como cliente, repetir o login com `100000001` / `Cliente@123` e trocar o token.

**Colecção gerada ou colecção do repositório?**

A colecção gerada mostra sempre os endpoints actuais da API, mas é preciso preencher os dados e o token à mão. A colecção do repositório (`postman/contas-api.postman_collection.json`) é a recomendada para avaliar: guarda o token automaticamente após o login e tem testes que confirmam a resposta esperada em cada caso, incluindo os erros.


### Ecrãs

| Ecrã | Perfil | O que faz |
|---|---|---|
| Entrar | todos | Login; redirecciona conforme o perfil |
| Contas | Admin | Lista paginada de todas as contas |
| Nova conta | Admin | Formulário com validação imediata e erros da API por campo |
| As minhas contas | Cliente | Contas do cliente e saldo total |
| Conta | Admin, Cliente | Saldo e extracto em formato de livro-razão, com filtro por datas |
| Transferir | Admin, Cliente | Três passos: dados, confirmação e comprovativo |

### Modelo de dados

```
customers (1) ──< accounts (1) ──< movements >── (0..1) transfers
    │                                               │
    └──< app_users                     source/target ┘
```

- `customers`: titular, com NUIT único. Um titular pode ter várias contas.
- `app_users`: credenciais e perfil; um utilizador Cliente está ligado a um titular.
- `accounts`: número único, tipo (ORDEM/POUPANCA) e saldo actual.
- `transfers`: cada transferência, com referência UUID e chave de idempotência opcional.
- `movements`: livro-razão imutável; cada linha guarda o saldo resultante.

## Decisões técnicas

**Atomicidade.** `TransferService.transfer` corre numa única transacção (`@Transactional`). Débito, crédito, registo da transferência e os dois movimentos são confirmados juntos, ou nada fica gravado. Há testes que provam o rollback por saldo insuficiente e por conta de destino inexistente.

**Concorrência.** Uma transacção não basta: duas transferências simultâneas da mesma conta poderiam ler o mesmo saldo. As contas são lidas com bloqueio pessimista (`SELECT ... FOR UPDATE`), sempre pela mesma ordem (número de conta crescente), o que evita deadlocks quando A→B e B→A acontecem ao mesmo tempo. Um teste lança 10 transferências de 200 em paralelo sobre uma conta com 1000 e confirma que exactamente 5 passam e o saldo termina em zero.

**Dinheiro.** `BigDecimal` no código e `NUMERIC(19,2)` na base de dados. Nunca `double`. Os pedidos aceitam no máximo duas casas decimais.

**Regras em duas camadas.** As regras são validadas na aplicação (mensagens claras) e garantidas na base de dados com `UNIQUE` e `CHECK` (número de conta único, saldo ≥ 0, valores > 0, origem ≠ destino). Se um bug escapar à aplicação, a base de dados recusa.

**Extracto como livro-razão.** O saldo actual está na conta, mas cada movimento guarda o saldo resultante e nunca é alterado. Cada transferência gera um débito e um crédito ligados pela mesma referência, o que permite auditoria e reconciliação.

**Idempotência.** O cabeçalho opcional `Idempotency-Key` garante que um pedido repetido (por exemplo, depois de um timeout de rede) devolve a transferência original sem debitar outra vez. A mesma chave com dados diferentes devolve 409.

**Segurança.** JWT assinado (HS512) com expiração de 1 hora; palavras-passe com BCrypt; API sem estado. A autorização por recurso (`AccessGuard`) usa a identidade do token, nunca um valor enviado pelo cliente: um cliente que altere o número da conta no URL recebe 403. O login devolve a mesma mensagem para utilizador inexistente e palavra-passe errada.

**Erros.** Todas as respostas de erro seguem o RFC 7807 (`application/problem+json`) com um `codigo` estável, por exemplo `SALDO_INSUFICIENTE`, `LIMITE_POR_OPERACAO` e `LIMITE_DIARIO_EXCEDIDO` (422), `CONTA_NAO_ENCONTRADA` (404), `ACESSO_NEGADO` (403), `DADOS_INVALIDOS` (400), `CONTA_DUPLICADA` (409) ou `DEMASIADAS_TENTATIVAS` (429). Stack traces nunca chegam ao cliente.

**Limite de tentativas de login (Redis).** Cada falha incrementa um contador no Redis (`login:falhas:<utilizador>`) que expira ao fim de 15 minutos. Depois de 5 falhas, as tentativas seguintes são recusadas com 429 e o cabeçalho `Retry-After`, mesmo com a palavra-passe certa, até o prazo terminar.
 A verificação acontece antes de validar a palavra-passe. Um login com sucesso apaga o contador. O Redis foi escolhido porque o contador é temporário, muito escrito, expira sozinho e é partilhado por várias instâncias da API.

**Limites de transferência.** Por operação (100 000,00 MZN) e diário por conta de origem (250 000,00 MZN), configuráveis por variáveis de ambiente. O limite diário é calculado a partir do livro-razão, dentro da transacção e com a conta bloqueada: é sempre exacto, não pode ser ultrapassado por transferências simultâneas, e um rollback desfaz também o consumo do limite.

**Extracto em PDF.** Gerado com OpenPDF a partir do mesmo filtro de datas do ecrã, com saldo inicial do período, totais de débitos e créditos, saldo final e os movimentos em ordem cronológica. Períodos com mais de 5 000 movimentos são recusados, para proteger o servidor.

**Interface.** A transferência tem um passo de confirmação antes de enviar, como num banco real. A chave de idempotência é gerada quando o utilizador revê a operação, por isso repetir o "Confirmar" depois de uma falha de rede não debita duas vezes. Os guards de perfil no Angular só servem a navegação; a protecção real está na API.

**Migrações.** O esquema é criado e alterado só pelo Flyway: `V1__esquema_inicial.sql` e `V2__indice_limite_diario.sql` (índice para a soma diária). O plugin Maven permite consultar e aplicar sem arrancar a aplicação: `mvn flyway:info`, `mvn flyway:migrate`, `mvn flyway:validate`. Uma migração aplicada nunca é editada; qualquer alteração entra numa nova versão.

**Datas.** Guardadas em UTC (`TIMESTAMPTZ`); os filtros do extracto interpretam as datas no fuso de Maputo.

## Estrutura

```
banco-contas/
├── docker-compose.yml
├── README.md
├── postman/contas-api.postman_collection.json
├── docs/
│   ├
│   └── diagramas/       diagramas UML 
            
├── backend/
│   ├── pom.xml, Dockerfile
│   └── src/
│       ├── main/java/mz/contas/
│       │   ├── auth/         login e emissão do token
│       │   ├── account/      contas, saldo e extracto
│       │   ├── transfer/     transferências
│       │   ├── domain/       entidades JPA
│       │   ├── repository/   Spring Data JPA
│       │   ├── security/     JWT, filtro, autorização por recurso
│       │   ├── config/       segurança, Swagger, dados iniciais
│       │   └── common/       erros e paginação
│       ├── main/resources/   application.yml, db/migration (Flyway)
│       └── test/java/        testes de integração
└── frontend/
    ├── Dockerfile, nginx.conf, proxy.conf.json
    └── src/app/
        ├── core/             auth, interceptor, guards, api, modelos, erros
        ├── features/         um componente por ecrã
        └── shared/           formatação de valores e datas
```

## Testes

Os testes de integração correm contra um PostgreSQL real em contentor (Testcontainers), porque bloqueios e constraints têm de se comportar como em produção. Requer Docker.

```bash
cd backend
mvn test
```

Cobrem: transferência com sucesso, rollback por saldo insuficiente, rollback por conta inexistente, idempotência, cliente a tentar usar conta alheia, transferências concorrentes, limite por operação, limite diário, bloqueio do login após 5 falhas, extracto em PDF, 401 sem token, 403 por perfil e validação de dados. O Redis dos testes também corre num contentor.

## Diagramas

Em `docs/diagramas/` estão 14 diagramas UML feitos no draw.io (casos de uso, componentes, implantação, modelo de dados, classes, sequências de login, transferência, concorrência, PDF e migrações, actividade e estados), um por separador do ficheiro `Diagramas-Contas-Bancarias.drawio`. O ficheiro `Diagramas-Contas-Bancarias.pdf` reúne todos.

## Melhorias possíveis em produção

Refresh tokens e revogação de tokens (também no Redis), limite de tentativas também por endereço IP, limites diferentes por perfil de cliente, maker-checker para operações do administrador, auditoria de consultas, observabilidade (métricas e tracing com correlation ID) e reconciliação periódica entre o saldo das contas e a soma dos movimentos.
