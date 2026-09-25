# Auditoria pré-release — Hestia

> **Documento histórico.** Esta auditoria registra a situação anterior ao empacotamento nativo.
> Os bloqueadores resolvidos e as evidências finais da versão `1.0.0` estão em
> [release-report.md](release-report.md).

Data: 24 de setembro de 2026  
Revisão-base: `69911f3e7a5c69849694a54ad28590fe5ad88d25` (`main`)  
Versão auditada: `0.1.0-SNAPSHOT`

## 1. Resumo executivo

O código, recursos, banco, migrations, regras financeiras, anexos, backup/restore, PDF,
dependências e empacotamento foram inspecionados. O projeto compila, os 131 testes finais passam e um
JAR autocontido foi iniciado fora do IntelliJ usando dados isolados.

A auditoria corrigiu problemas objetivos: ausência de artefato executável, versão exibida duplicada,
aceitação de schema futuro, preferências de backup frágeis, validação insuficiente da árvore restaurada,
falha de PDF com caracteres fora da fonte, carregamento antecipado de fontes e ausência de inventário de
licenças.

O software ainda **não deve ser declarado Hestia 1.0 pronto para distribuição pública**. É necessário
definir a versão final, produzir/testar um pacote nativo Windows, concluir a varredura automatizada de
vulnerabilidades e fazer uma homologação manual de UI e instalação em ambiente limpo.

## 2. Baseline

| Item | Evidência |
| --- | --- |
| Repositório | `main`, revisão `69911f3e7a5c69849694a54ad28590fe5ad88d25` |
| Estado inicial | árvore de trabalho limpa |
| Versão | `0.1.0-SNAPSHOT` |
| Ambiente | Windows x64; Zulu JDK 27; código alvo Java 21; Maven 3.9.16 |
| Testes antes | 125 executados; 125 aprovados; 0 falhas/erros/ignorados |
| Build antes | `mvn clean package`: `BUILD SUCCESS` |
| Artefato antes | JAR sem `Main-Class`; `java -jar` falhava com `no main manifest attribute` |

A duração exata do baseline não foi preservada no resumo da execução e não é estimada aqui.

## 3. Arquitetura encontrada

- `presentation`: controllers, componentes JavaFX, FXML e CSS;
- `application`: serviços, DTOs e contratos de repositório;
- `domain`: modelos, enums e exceções sem JavaFX ou SQLite;
- `infrastructure`: SQLite, repositories, migrations e filesystem;
- `config`: bootstrap, composição, caminhos e metadados;
- `util`: conversão/formatação monetária compartilhada.

O fluxo predominante é `FXML/controller → service → repository → SQLite`. Não foi encontrado SQL em
controllers nem UI em repositories. `AttachmentService` conhece implementações de filesystem da
infraestrutura, uma imperfeição arquitetural não associada a falha funcional e que não justificou grande
refatoração. Os entrypoints são `HestiaLauncher`, `HestiaApplication` e `ApplicationBootstrap`.

## 4. Problemas encontrados

### HST-AUD-001 — artefato não executava com `java -jar`

- **Área/severidade:** distribuição — ALTO.
- **Reprodução:** executar o JAR original; retornava `no main manifest attribute`.
- **Causa:** ausência de manifesto e de empacotamento das dependências.
- **Correção:** manifesto e artefato adicional pelo Maven Shade Plugin, com services mesclados.
- **Teste:** JAR `-executable.jar` iniciou fora da IDE e criou o banco.
- **Status:** CORRIGIDO.

### HST-AUD-002 — banco de versão futura era aceito

- **Área/severidade:** migrations — ALTO.
- **Reprodução:** registrar schema `999` e inicializar.
- **Causa:** não havia comparação com a maior migration suportada.
- **Correção:** `MigrationRunner` interrompe o startup diante de schema futuro.
- **Teste:** `MigrationCompatibilityTest`.
- **Status:** CORRIGIDO.

### HST-AUD-003 — restauração não validava integralmente a árvore extraída

- **Área/severidade:** backup/filesystem — ALTO.
- **Encontrado:** faltavam validações explícitas de arquivo regular, symlink, manifesto incompleto e
  contenção final, apesar da proteção existente contra ZIP Slip.
- **Correção:** manifesto, tamanho/hash, arquivo regular sem seguir links, normalização/contenção e move
  atômico com fallback.
- **Teste:** nove cenários em `AttachmentBackupIntegrationTest`.
- **Status:** CORRIGIDO.

### HST-AUD-004 — preferências de backup malformadas eram frágeis

- **Área/severidade:** configurações — MÉDIO.
- **Reprodução:** retenção/data inválida no arquivo de preferências.
- **Correção:** defaults seguros, retenção 1–100 e escrita temporária/atômica.
- **Teste:** dois testes em `BackupPreferencesServiceTest`.
- **Status:** CORRIGIDO.

### HST-AUD-005 — PDF falhava com emoji em nomes

- **Área/severidade:** PDF — MÉDIO.
- **Reprodução:** perfil `Ana 🏠`, categoria `Casa 🏡`, exportar relatório.
- **Encontrado:** `IllegalArgumentException` da Helvetica/WinAnsi.
- **Correção:** code points sem cobertura viram `?`; erros são convertidos em mensagem de aplicação.
- **Teste:** regressão cria e lê o PDF.
- **Status:** CORRIGIDO.

### HST-AUD-006 — versão podia divergir entre Maven e interface

- **Área/severidade:** versionamento — MÉDIO.
- **Causa:** textos independentes do POM.
- **Correção:** recurso Maven filtrado e `ApplicationInfo`, usados na janela, Configurações, manifesto e
  backup.
- **Teste:** `ApplicationInfoTest` e inspeção interna do JAR.
- **Status:** CORRIGIDO.

### HST-AUD-007 — fontes de PDF eram inicializadas no startup

- **Área/severidade:** performance — MÉDIO.
- **Encontrado:** construir o serviço podia varrer fontes do Windows antes de qualquer exportação.
- **Correção:** fontes agora são criadas somente pelo writer durante `export`.
- **Teste:** testes de PDF, build e startup do artefato.
- **Status:** CORRIGIDO.

### HST-AUD-008 — pacote sem inventário próprio de licenças

- **Área/severidade:** licenças — MÉDIO.
- **Correção:** `THIRD-PARTY-NOTICES.md` no repositório/JAR e preservação de LICENSE/NOTICE Apache.
- **Teste:** recurso encontrado dentro do JAR.
- **Status:** CORRIGIDO tecnicamente; revisão jurídica ainda recomendada.

## 5. Correções realizadas

- metadados de versão centralizados;
- título da janela, Configurações, manifesto e backups sincronizados;
- proteção contra downgrade de schema;
- preferências de backup tolerantes a corrupção;
- restore endurecido contra conteúdo inesperado e symlinks;
- teste de integridade/foreign keys SQLite;
- PDF tolerante a Unicode fora da fonte;
- carregamento das fontes adiado até a exportação;
- JAR executável autocontido;
- avisos de terceiros no repositório e pacote;
- README atualizado com comando verificado.

Nenhuma migration aplicada nem regra financeira foi alterada.

## 6. Banco e migrations

**Status: PASSOU nos testes automatizados.**

- dados de produção ficam fora do repositório; testes usam diretórios temporários;
- cada conexão configura foreign keys e busy timeout;
- JDBC e streams têm fechamento estruturado;
- migrations `V001`–`V005` são ordenadas e registradas em `schema_history`;
- segunda inicialização não reaplica migrations/seeds;
- schema futuro é recusado;
- `PRAGMA integrity_check` retornou `ok`;
- `PRAGMA foreign_key_check` não retornou violações;
- upgrades anteriores são cobertos pelos testes de migration;
- o JAR criou `hestia.db` com 188.416 bytes e a reabertura manteve esse tamanho.

Nenhum banco real do usuário foi aberto ou modificado.

## 7. Integridade financeira

**Status: PASSOU na cobertura existente.**

- dinheiro usa `BigDecimal` no domínio e centavos inteiros no SQLite;
- não há `double`/`float` para dinheiro;
- 12 testes cobrem parsing, centavos, milhares, inválidos e arredondamento;
- valores positivos são exigidos nos fluxos aplicáveis;
- parcelas distribuem centavos deterministicamente e preservam o total; limite 1–120;
- recorrências cobrem fim de mês, fevereiro, idempotência e ocorrência personalizada;
- realizado = receitas recebidas − despesas pagas;
- projetado = receitas não canceladas − despesas não canceladas;
- cancelamentos preservam histórico.

Os fluxos integrados de compromissos/movimentações somam 27 testes. Não houve ensaio manual de cada valor
monetário solicitado em todos os campos visuais; as conversões centrais foram automatizadas.

## 8. Backup e restore

**Status: PASSOU automatizado; falhas físicas de disco não foram simuladas.**

- snapshot SQLite, anexos e manifesto versionado;
- tamanho e SHA-256 por arquivo;
- backup protegido usa AES pelo Zip4j e não persiste senha;
- restore valida formato, paths, manifesto, hashes, banco e schema antes da troca;
- backup de segurança é criado antes da substituição;
- ZIP Slip, arquivo alterado/ausente, senha errada, schema futuro e symlink são rejeitados;
- move atômico é usado quando disponível;
- backup automático é opt-in, diário e com retenção limitada.

Falta um ensaio manual de interrupção durante a troca em filesystems diferentes.

## 9. UI/UX

**Status: PASSOU em smoke tests; inspeção visual manual completa NÃO TESTADA.**

Foram carregadas 22 combinações de telas/estados JavaFX e feitas 26 verificações de recursos. FXML, CSS,
imagens e controllers são encontrados. Painel, movimentações, receitas, contas, recorrências,
parcelamentos, perfis, categorias, relatórios e configurações possuem fluxos reais e estados vazios.

Não havia controle/captura da janela nativa nesta sessão. Alinhamento pixel a pixel, teclado, leitor de
tela, DPI 125/150/200% e múltiplas resoluções precisam de homologação manual. FXML/controllers de
calendário/documentos continuam no classpath por compatibilidade, embora não sejam navegação principal.

## 10. Segurança e privacidade

**Status: PARCIAL.**

- não foram encontrados segredos, telemetria ou endpoints externos;
- armazenamento é local e SQL variável usa parâmetros;
- anexos validam assinatura, extensão, tamanho e SHA-256;
- nomes físicos são aleatórios e paths ficam confinados;
- restore valida conteúdo antes de substituir dados;
- stack traces não são apresentados na UI;
- banco e backups sem senha não são criptografados por padrão;
- não há autenticação ou separação por usuário dentro do aplicativo.

O OWASP Dependency-Check, sem chave NVD, anunciou 397.448 registros e foi interrompido em 3%. Portanto,
não se afirma que uma varredura automatizada completa passou. A revisão manual não encontrou
vulnerabilidade crítica conhecida e aplicável; PDFBox 3.0.8 está acima da faixa 3.0.0–3.0.6 citada para
CVE-2026-23907. O gate deve rodar em CI com cache/chave NVD antes da release.

## 11. Dependências

| Dependência | Versão | Finalidade |
| --- | --- | --- |
| OpenJFX Controls/FXML/Swing | 21.0.8 | UI/FXML/imagens |
| SQLite JDBC | 3.50.3.0 | persistência |
| SLF4J / Logback | 2.0.17 / 1.5.18 | logging |
| Apache PDFBox | 3.0.8 | PDF |
| Zip4j | 2.11.6 | backup/criptografia |
| Jackson | 2.22.0 | manifesto JSON/datas |
| JUnit / AssertJ | 5.13.4 / 3.27.3 | testes |

`dependency:analyze` apresentou falsos positivos esperados para classificadores JavaFX e componentes de
runtime/reflexão. O Shade ainda avisa sobre `module-info` e recursos repetidos, comportamento esperado no
uber-JAR em classpath, mas uma razão adicional para validar uma imagem nativa definitiva.

## 12. Licenças

Hestia usa MIT. `THIRD-PARTY-NOTICES.md` relaciona versões e licenças; o JAR preserva avisos Apache.
Há componentes sob GPLv2 com Classpath Exception, Apache 2.0, MIT, EPL e LGPL. Esta é revisão técnica,
não parecer jurídico; a redistribuição do OpenJFX e do futuro runtime deve ser revisada antes da publicação.

## 13. Performance

- não foi confirmado N+1 crítico;
- não há conexão SQLite global aberta;
- JDBC/streams são fechados;
- fontes PDF não são mais carregadas no startup;
- não houve benchmark com milhares de registros nem profiling prolongado.

No sandbox, JavaFX tentou gravar cache em `C:\.openjfx`, sem permissão, atrasando a primeira janela em
aproximadamente nove segundos. Isso decorre do `user.home` incomum do ambiente; deve ser medido novamente
em conta Windows comum e no pacote nativo.

## 14. Testes

**Antes:** 125 total; 125 aprovados; 0 falhas; 0 erros; 0 ignorados.  
**Depois:** 131 total; 131 aprovados; 0 falhas; 0 erros; 0 ignorados.

Comando final `mvn clean test`: `BUILD SUCCESS`, 11,163 s. Foram acrescentadas regressões para versão,
schema futuro, preferências de backup, integridade SQLite e Unicode no PDF.

## 15. Build

`mvn clean package`: `BUILD SUCCESS`, 131 testes aprovados, 14,527 s.

- `hestia-0.1.0-SNAPSHOT.jar`: JAR fino;
- `hestia-0.1.0-SNAPSHOT-executable.jar`: autocontido, 32.881.166 bytes.

O executável contém `Main-Class`, título/versão, `hestia.properties`, provider SLF4J e avisos de terceiros.

## 16. Distribuição

Comando testado fora da IDE:

```shell
java --enable-native-access=ALL-UNNAMED -jar target/hestia-0.1.0-SNAPSHOT-executable.jar
```

Com `HESTIA_DATA_DIR` isolado, o processo permaneceu ativo e criou diretórios e SQLite. A segunda abertura
permaneceu ativa e manteve o banco em 188.416 bytes. Ainda não existem `jpackage`, instalador, runtime
próprio, assinatura, atualização ou desinstalação. O JAR exige JDK 21+, adequado para validação técnica,
mas insuficiente como experiência de primeira release pública Windows.

## 17. Arquivos alterados

**Criados:** `ApplicationInfo.java`, `hestia.properties`, três classes de teste novas,
`THIRD-PARTY-NOTICES.md` e este relatório.

**Modificados:** `pom.xml`, `README.md`, `HestiaApplication.java`, `SettingsController.java`,
`settings-view.fxml`, `BackupPreferencesService.java`, `BackupService.java`, `ReportPdfService.java`,
`MigrationRunner.java`, `ReportServiceTest.java` e `DatabaseInitializerTest.java`.

Não houve remoções, alterações de migrations, commit ou push.

## 18. Bloqueadores de lançamento restantes

### BLK-001 — versão snapshot

- **Severidade:** BLOQUEADOR de processo.
- **Evidência:** POM/UI/manifesto mostram `0.1.0-SNAPSHOT`.
- **Impacto:** não identifica a versão pública solicitada.
- **Correção:** após aprovação, definir versão final sem `SNAPSHOT` e reconstruir.
- **Status:** PENDENTE.

### BLK-002 — ausência de pacote nativo Windows

- **Severidade:** BLOQUEADOR para distribuição pública amigável.
- **Evidência:** somente JAR; exige JDK e comando.
- **Impacto:** instalação, atalhos, runtime e desinstalação não validados.
- **Correção:** criar/testar app-image ou instalador x64 Java 21, ícone e licença.
- **Status:** PENDENTE.

### BLK-003 — scan automatizado de vulnerabilidades inconclusivo

- **Severidade:** BLOQUEADOR do checklist, não vulnerabilidade confirmada.
- **Evidência:** Dependency-Check interrompido em 3% sem chave NVD.
- **Correção:** executar em CI com chave, cache e relatório arquivado no mesmo commit.
- **Status:** PENDENTE.

### BLK-004 — aceite manual de UI e fluxo ponta a ponta

- **Severidade:** BLOQUEADOR de homologação.
- **Evidência:** smoke tests passaram; janela nativa não pôde ser automatizada/capturada.
- **Correção:** roteiro Windows limpo cobrindo criação, persistência, PDF, anexos, backup e restore,
  resoluções e DPIs.
- **Status:** PENDENTE.

## 19. Pendências não bloqueadoras

- benchmark/profiling com milhares de movimentações;
- fonte Unicode incorporada no PDF em vez de `?` para glifos raros;
- falha de espaço/disco e interrupção em diferentes filesystems;
- modularização/jlink após estabilizar o pacote nativo;
- limpeza de FXML legado após confirmar referências;
- testes de teclado/acessibilidade;
- documentação de atualização/desinstalação após existir instalador;
- avaliar assinatura Authenticode/SmartScreen.

## 20. Limitações da auditoria

- sem inspeção visual manual completa da janela nativa;
- sem instalador, upgrade, desinstalação ou VM Windows limpa;
- sem varredura NVD integral;
- sem parecer jurídico;
- sem simulação de todas as falhas de disco/permissão;
- sem carga massiva ou teste de longa duração;
- nenhum dado real foi utilizado.

## Matriz de release

| Área | Status | Problema/risco | Testado? |
| --- | --- | --- | --- |
| Build | PASSOU | warnings esperados do uber-JAR | SIM |
| Startup | PASSOU | cache JavaFX no sandbox | SIM |
| Database/Migrations | PASSOU | schema futuro corrigido | SIM |
| Transactions/Bills/Income | PASSOU | nenhum conhecido | SIM |
| Recurrences/Installments | PASSOU | nenhum conhecido | SIM |
| Dashboard | PASSOU | aceite visual pendente | PARCIAL |
| Profiles/Categories | PASSOU | nenhum conhecido | SIM |
| Reports/PDF | PASSOU | glifo raro vira `?` | SIM |
| Attachments | PASSOU | nenhum conhecido | SIM |
| Backup/Restore | PASSOU | interrupção física não ensaiada | SIM |
| Settings | PASSOU | versão/preferências corrigidas | SIM |
| UI/UX | PENDENTE | homologação manual | PARCIAL |
| JavaFX | PASSOU | warning de unnamed module | SIM |
| Security | PENDENTE | scan NVD incompleto | PARCIAL |
| Dependencies/Licenses | PASSOU | revisão jurídica recomendada | SIM |
| Packaging | PENDENTE | sem pacote nativo | PARCIAL |
| Performance | PARCIAL | sem carga volumosa | PARCIAL |
| Documentation | PARCIAL | instalação/update dependem do pacote | SIM |

## Conclusão

O projeto possui agora um artefato funcional fora da IDE. Não foram encontradas perda conhecida de dados,
corrupção conhecida, cálculo financeiro incorreto, migration destrutiva, restore destrutivo confirmado ou
falha de build. Os quatro bloqueadores de processo acima precisam ser fechados antes de chamar o artefato
de **Hestia 1.0 público**.
