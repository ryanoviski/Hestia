# Relatório completo do projeto Hestia

> **Documento histórico.** Este relatório registra o estado do projeto em 21 de setembro de 2026,
> quando a versão ainda era `0.1.0-SNAPSHOT`. Para o estado da versão pública `1.0.0`, consulte
> [release-report.md](release-report.md).

**Data do levantamento:** 21 de setembro de 2026  
**Versão do projeto:** `0.1.0-SNAPSHOT`  
**Estado analisado:** código da branch atual, commit-base `5b62575`  
**Finalidade deste documento:** registrar, de forma funcional e técnica, o que o Hestia é hoje, como foi construído, quais decisões orientam o desenvolvimento e o que ainda não está concluído.

## 1. Resumo executivo

O Hestia é um aplicativo desktop de gestão financeira pessoal e familiar. O sistema foi pensado para funcionar localmente, sem depender de serviços bancários, internet, conta de usuário ou sincronização em nuvem. Seu foco é organizar receitas, despesas e compromissos financeiros de uma pessoa, casal ou grupo familiar.

O projeto encontra-se em uma fase funcional intermediária. A fundação arquitetural está pronta e os principais fluxos financeiros já funcionam: perfis, categorias, movimentações, contas a pagar, receitas, despesas recorrentes, compras parceladas, painel mensal, anexos contextuais e backup/restauração. Ainda não se trata de uma versão final de produto: relatórios estão apenas preparados na navegação, algumas áreas precisam de refinamento e não há empacotador/instalador de distribuição.

Situação resumida:

| Área | Estado atual |
|---|---|
| Inicialização desktop JavaFX | Implementada |
| Banco SQLite e migrações | Implementados |
| Perfis | Implementados |
| Categorias | Implementadas, incluindo edição e exclusão segura |
| Receitas e despesas | Implementadas |
| Contas a pagar | Implementadas como visão filtrada de despesas pendentes |
| Recorrências | Implementadas para despesas mensais e integradas ao fluxo de contas |
| Parcelamentos | Implementados no fluxo de contas, sem vínculo com cartão |
| Painel mensal | Implementado com dados reais |
| Anexos | Implementados dentro de perfis e movimentações |
| Backup e restauração | Implementados |
| Relatórios | Implementados com visão mensal, comparações e exportação PDF |
| Calendário | Retirado da navegação; há código interno remanescente |
| Tela central de documentos | Retirada da navegação; anexos continuam contextuais |
| Orçamentos | Não fazem parte da interface atual |
| Contas bancárias e cartões | Excluídos do domínio por decisão de produto |

## 2. Visão do produto

O Hestia foi concebido como uma central doméstica de organização financeira. A proposta não é reproduzir um aplicativo bancário nem controlar saldos de contas. A unidade principal do sistema é o compromisso financeiro: algo que entrou, saiu, deve entrar ou deve ser pago.

Os princípios atuais do produto são:

- **Privacidade local:** banco de dados, anexos, backups e logs ficam no computador do usuário.
- **Organização familiar:** um grupo financeiro pode representar uma pessoa, casal ou família e conter perfis individuais ou compartilhados.
- **Histórico preservado:** registros que já participam do histórico não são removidos de maneira destrutiva; são desativados ou cancelados quando necessário.
- **Valores exatos:** dinheiro não utiliza `double` ou `float`; os cálculos são feitos com `BigDecimal` e os valores são armazenados em centavos inteiros.
- **Interface em português:** textos apresentados ao usuário estão em português do Brasil, enquanto código, tabelas e campos usam nomes em inglês.
- **Evolução modular:** a arquitetura separa interface, casos de uso, domínio e persistência para permitir que novos módulos sejam adicionados sem concentrar regras nos controladores JavaFX.

## 3. Público e modelo de uso

O sistema atende inicialmente um único grupo financeiro local. Na primeira execução, ele cria o grupo padrão **“Minha família”**. Não são criadas pessoas fictícias.

Dentro do grupo, o usuário pode cadastrar perfis de dois tipos:

- **Pessoa (`PERSON`):** representa uma pessoa específica.
- **Compartilhado (`SHARED`):** representa movimentações do casal, da família ou de uso comum.

Não há autenticação, contas de acesso, permissões individuais ou isolamento por senha. Todos os dados disponíveis no computador pertencem ao mesmo ambiente local do Hestia.

## 4. Experiência e navegação atual

A janela principal possui uma barra lateral fixa, cabeçalho e área central de conteúdo. A identidade visual usa verde-petróleo, superfícies claras, tons neutros e pequenos acentos quentes. A tipografia principal é Segoe UI, com Arial como alternativa.

A janela abre com 1200 × 760 pixels e respeita o mínimo de 980 × 640 pixels. Formulários, filtros e diálogos compartilham o mesmo CSS e componentes reutilizáveis para manter consistência.

A navegação visível contém:

1. Painel;
2. Movimentações;
3. Contas a pagar;
4. Receitas;
5. Relatórios;
6. Perfis;
7. Categorias;
8. Configurações.

Recorrências e Parcelamentos deixaram de ocupar destinos principais: são criados no fluxo **Nova conta** e gerenciados de forma contextual em Contas a pagar. Calendário, Documentos e Orçamentos não aparecem no menu atual. Anexos de PDF e imagem permanecem disponíveis no contexto de perfis e movimentações, evitando uma seção de documentos desconectada do dado financeiro ao qual o arquivo pertence.

## 5. Funcionalidades detalhadas

### 5.1 Painel

O Painel é a tela inicial e resume o mês selecionado. Os números vêm das movimentações gravadas no banco; o sistema não inventa dados para preencher estados vazios.

São exibidos:

- receitas recebidas;
- receitas ainda previstas;
- despesas pagas;
- despesas pendentes;
- despesas vencidas;
- resultado realizado;
- resultado projetado;
- próximos vencimentos;
- distribuição das despesas por categoria.

As fórmulas adotadas são:

- **Resultado realizado:** receitas recebidas menos despesas pagas.
- **Resultado projetado:** receitas não canceladas menos despesas não canceladas.
- **Vencida:** despesa pendente com vencimento anterior ao dia atual.

Movimentações canceladas não entram nos resultados. Recorrências e parcelamentos são contabilizados pelas movimentações geradas, e não novamente pela regra ou plano de origem, evitando dupla contagem.

### 5.2 Movimentações

Movimentação é o registro financeiro central. Pode ser uma receita ou uma despesa e possui:

- descrição;
- valor positivo;
- perfil;
- categoria compatível com o tipo;
- data de referência;
- vencimento opcional;
- situação;
- data de conclusão quando recebida ou paga;
- observações opcionais;
- origem manual, recorrente ou parcelada.

A tela permite:

- criar e editar movimentações;
- consultar detalhes;
- marcar como recebida ou paga;
- reabrir uma movimentação concluída;
- cancelar uma movimentação;
- consultar a origem de registros gerados;
- adicionar e gerenciar anexos;
- pesquisar por descrição;
- filtrar por mês, tipo, situação, perfil, categoria e origem;
- exibir somente despesas vencidas;
- limpar os filtros.

Regras relevantes:

- descrição é obrigatória e limitada a 150 caracteres;
- observações são limitadas a 1.000 caracteres;
- o perfil e a categoria precisam pertencer ao contexto atual;
- categoria de receita não pode ser usada em despesa e vice-versa;
- novos registros não podem selecionar perfil ou categoria inativos;
- registros concluídos exigem data de conclusão;
- registros pendentes ou cancelados não mantêm data de conclusão;
- o tipo de uma movimentação gerada não pode ser alterado;
- o valor de uma parcela gerada não pode ser alterado individualmente nesta versão.

### 5.3 Contas a pagar

Contas a pagar não é uma entidade duplicada. É uma visão especializada da mesma base de movimentações, fixada em despesas e orientada a compromissos pendentes/vencidos. Essa decisão evita divergência entre uma “conta” e sua respectiva despesa.

O usuário utiliza os mesmos recursos de consulta, edição, pagamento, cancelamento, filtros e anexos disponíveis nas movimentações, dentro do contexto de despesas.

### 5.4 Receitas

Receitas também são uma visão especializada das movimentações, fixada no tipo receita. A situação é apresentada em linguagem adequada: prevista, recebida ou cancelada.

Essa tela reaproveita o mesmo fluxo de cadastro e consulta, mantendo uma única regra de negócio para os registros financeiros.

### 5.5 Perfis

A gestão de perfis está funcional e conectada ao SQLite. O usuário pode:

- listar perfis ativos e inativos;
- cadastrar perfil;
- editar nome, tipo e cor;
- desativar e reativar;
- excluir quando não há dependências;
- gerenciar anexos associados ao perfil.

Regras:

- nome é obrigatório, normalizado e limitado a 80 caracteres;
- nomes são únicos no grupo, sem diferença entre maiúsculas e minúsculas;
- tipo é obrigatório;
- cor é opcional e validada no formato hexadecimal;
- não há limite fixo para a quantidade de perfis ativos no grupo;
- o tipo de perfil em uso não pode ser alterado;
- perfil referenciado por movimentações, compromissos ou anexos não pode ser excluído fisicamente e deve ser desativado;
- perfil sem referências pode ser excluído após confirmação.

### 5.6 Categorias

As categorias classificam receitas e despesas. Há categorias padrão, compartilhadas como base pelo sistema, e categorias personalizadas do grupo financeiro.

O usuário pode:

- pesquisar pelo nome;
- filtrar por tipo;
- mostrar ou ocultar categorias inativas;
- criar categoria personalizada;
- editar nome e cor;
- alterar o tipo de categoria personalizada ainda não utilizada;
- desativar e reativar;
- excluir com segurança.

O comportamento de categorias padrão é deliberadamente diferente:

- o registro global original não é alterado;
- nome, cor e visibilidade são personalizados somente para o grupo atual por meio de `category_preferences`;
- seu tipo, receita ou despesa, não pode ser alterado;
- a ação de exclusão equivale a ocultá-la/desativá-la para o grupo;
- ela pode ser reativada posteriormente.

Para categorias personalizadas:

- nome é obrigatório e limitado a 80 caracteres;
- a combinação de nome e tipo deve ser única;
- cor é opcional e validada;
- categoria já utilizada não pode ter seu tipo alterado;
- categoria já utilizada não pode ser excluída fisicamente; deve ser desativada;
- categoria sem referências pode ser excluída após confirmação.

Categorias padrão iniciais de receita:

- Salário;
- Renda extra;
- Reembolso;
- Outros.

Categorias padrão iniciais de despesa:

- Moradia;
- Alimentação;
- Supermercado;
- Saúde;
- Farmácia;
- Transporte;
- Educação;
- Lazer;
- Assinaturas;
- Compras;
- Impostos;
- Dívidas;
- Outros.

### 5.7 Despesas recorrentes

Recorrências representam compromissos mensais repetidos, como aluguel, condomínio ou assinatura. Nesta versão, somente despesas podem ser recorrentes.

Cada regra contém descrição, valor, perfil, categoria de despesa, primeiro vencimento, término opcional, observações e situação ativa/inativa.

O sistema:

- cria e edita regras mensais;
- gera ocorrências como movimentações reais;
- gera, na inicialização, as ocorrências necessárias até doze meses à frente;
- impede duplicidade por regra e mês;
- preserva pagamentos e alterações individuais;
- permite desativar uma regra;
- oferece a opção de cancelar ocorrências futuras pendentes que ainda não foram personalizadas.

Vencimentos nos dias 29, 30 ou 31 são ajustados para o último dia de meses menores. O ajuste não provoca deriva: uma regra do dia 31 pode cair em 28 de fevereiro e voltar ao dia 31 em março.

Ao editar individualmente uma ocorrência, ela é marcada como personalizada. Alterações futuras na regra não sobrescrevem essa ocorrência. Essa separação preserva ajustes reais, como uma conta de energia cujo valor de determinado mês foi diferente.

### 5.8 Compras parceladas

Parcelamentos são compromissos financeiros independentes e não possuem relação com cartões, faturas ou limites.

O usuário informa:

- descrição;
- valor total;
- quantidade de parcelas;
- primeiro vencimento;
- perfil;
- categoria de despesa;
- observações opcionais.

O sistema cria o plano e todas as parcelas em uma única transação de banco. Cada parcela também é uma movimentação de despesa, o que permite que apareça no painel e nas consultas comuns.

Regras:

- são permitidas de 1 a 120 parcelas;
- a soma das parcelas sempre é exatamente igual ao valor total;
- eventuais centavos restantes são distribuídos deterministicamente nas primeiras parcelas;
- datas mensais mantêm o dia-base quando possível e usam o último dia em meses menores;
- uma parcela pode ser paga e reaberta pelos fluxos de movimentação;
- o usuário pode cancelar somente as parcelas restantes;
- parcelas já pagas e histórico anterior são preservados;
- o plano calcula os estados em andamento, concluído, parcialmente cancelado ou cancelado.

Exemplo da distribuição exata: R$ 100,00 em três parcelas gera R$ 33,34, R$ 33,33 e R$ 33,33.

### 5.9 Anexos contextuais

O Hestia permite anexar arquivos a um perfil ou a uma movimentação. Não existe atualmente uma opção “Documentos” na barra lateral; o acesso ocorre a partir do registro relacionado.

Formatos aceitos:

- PDF;
- PNG;
- JPEG/JPG.

Tipos de documento disponíveis:

- comprovante;
- holerite;
- conta ou documento de cobrança;
- outro.

Proteções implementadas:

- validação do conteúdo real, e não apenas da extensão;
- tamanho máximo padrão de 20 MB por arquivo;
- limite configurável pela propriedade `hestia.attachment.max.bytes`;
- cálculo e armazenamento de SHA-256;
- nome físico gerado por UUID, sem reutilizar o nome original;
- bloqueio de caminhos absolutos ou que escapem da pasta de anexos;
- vínculo obrigatório com exatamente um perfil ou uma movimentação;
- verificação de arquivo ausente, modificado ou inválido;
- remoção coordenada entre arquivo e registro de banco.

Na interface contextual atual é possível adicionar, abrir no aplicativo padrão do sistema e remover anexos. O projeto também contém infraestrutura para pré-visualização e diagnóstico, mas a antiga tela central de Documentos não faz parte da navegação entregue ao usuário.

### 5.10 Configurações, backup e restauração

A tela Configurações concentra proteção e portabilidade dos dados.

Backup manual:

- cria um arquivo com extensão `.hestia-backup`;
- inclui uma cópia consistente do SQLite e todos os anexos;
- inclui manifesto com versão, esquema, tamanhos e hashes;
- pode usar senha com criptografia AES-256;
- valida o arquivo produzido antes de concluir.

Restauração:

- valida extensão, formato ZIP, manifesto, hashes e integridade do SQLite;
- rejeita caminhos inseguros e arquivos excessivamente grandes;
- rejeita esquema mais novo e incompatível;
- cria automaticamente um backup de segurança antes de substituir os dados;
- restaura o estado anterior se a aplicação do backup falhar.

Backup automático:

- é opcional;
- ocorre no máximo uma vez por dia, ao iniciar o Hestia;
- usa um diretório escolhido pelo usuário;
- não utiliza senha;
- mantém uma quantidade configurável de cópias, com padrão de dez;
- registra data e resultado da última tentativa.

Logs e arquivos temporários não fazem parte do backup.

### 5.11 Relatórios

Relatórios oferece uma visão mensal baseada nas mesmas movimentações e fórmulas do Painel. Exibe totais de receitas e despesas, resultado projetado, valores recebidos/previstos, pagos/pendentes/vencidos, distribuição de despesas por categoria e perfil e comparação com o mês anterior.

O relatório pode ser exportado em PDF estruturado por meio do PDFBox. O arquivo contém identificação do Hestia, período, data de geração, resumo financeiro, situações, comparações e detalhamentos; não é uma captura de tela. O módulo é somente leitura e não exigiu nova tabela ou migração.

## 6. Funcionalidades removidas da navegação ou não iniciadas

### Calendário

O menu Calendário foi removido por decisão de experiência do produto. Ainda existem `CalendarService`, `CalendarController`, testes e FXML no código. Eles conseguem agrupar movimentações por data e aplicar filtros, mas não há rota na interface principal para acessar o módulo.

Esse código deve ser tratado como infraestrutura remanescente, e não como funcionalidade entregue. Em uma etapa futura será necessário decidir entre removê-lo definitivamente ou reaproveitá-lo dentro de uma experiência mais útil, por exemplo na área de vencimentos.

### Documentos

A antiga tela central de Documentos também foi retirada da navegação. Seus arquivos de controller/FXML e serviços de visualização continuam no projeto. A função útil foi mantida como **Anexos** dentro de perfis e movimentações.

### Orçamentos

Orçamentos não fazem parte da navegação e não possuem modelo, tabela ou serviço implementado no estado atual.

## 7. Arquitetura do software

O projeto segue uma arquitetura em camadas com composição explícita de dependências.

```text
presentation
    controllers
    components

application
    services
    dto
    repositories (contratos)

domain
    models
    enums
    exceptions

infrastructure
    database
    migrations
    repositories (SQLite)
    filesystem

config
util
```

### Presentation

Contém telas FXML, controladores JavaFX e componentes visuais reutilizáveis. É responsável por capturar ações, montar diálogos, apresentar dados e transformar erros em mensagens compreensíveis. Não deve conter SQL nem regras financeiras centrais.

Componentes importantes:

- `MonthYearPicker`: escolha consistente de mês e ano;
- `ColorPalette`: seleção de cores de perfis e categorias;
- `ComboBoxSupport`: padronização de listas e rótulos;
- `ThemeManager`: aplicação do CSS em cenas, telas e diálogos.

### Application

Contém os casos de uso. Serviços validam entradas, aplicam limites, coordenam repositórios e protegem o histórico. Exemplos: `ProfileService`, `CategoryService`, `TransactionService`, `RecurringExpenseService`, `InstallmentPlanService`, `AttachmentService` e `BackupService`.

Os contratos dos repositórios ficam nesta camada, permitindo que a aplicação conheça as operações necessárias sem depender de SQL.

### Domain

Contém modelos, enums e exceções que expressam o vocabulário financeiro. Não depende de JavaFX nem do driver SQLite.

Principais modelos:

- `Profile`;
- `Category`;
- `Transaction`;
- `RecurringExpense`;
- `InstallmentPlan`;
- `Installment`;
- `Attachment`.

### Infrastructure

Contém os detalhes técnicos: abertura de conexões, migrações, SQL, repositórios SQLite, validação de arquivos, armazenamento seguro e backup.

Não existe uma conexão SQLite global permanentemente aberta. Cada operação abre sua conexão e fecha recursos com `try-with-resources`.

### Config e composição

`ApplicationBootstrap` cria os diretórios, inicializa o banco, instancia repositórios e serviços e devolve um `ApplicationContext`. A composição é explícita; não há contêiner de injeção de dependência nem singleton global mutável.

## 8. Fluxo de uma operação

O fluxo típico é:

```text
FXML / componente visual
        ↓
Controller JavaFX
        ↓
Serviço de aplicação
        ↓
Contrato de repositório
        ↓
Implementação SQLite
        ↓
Banco local
```

Exemplo de cadastro de categoria:

1. o controller lê nome, tipo e cor;
2. `CategoryService` normaliza e valida os dados;
3. o serviço verifica duplicidade e contexto do grupo;
4. o repositório executa a persistência;
5. o controller atualiza a lista ou mostra uma mensagem amigável;
6. detalhes técnicos são enviados ao log, sem stack trace na interface.

## 9. Inicialização da aplicação

O ponto de entrada é `HestiaLauncher`, separado de `HestiaApplication` para facilitar execução pelo Maven, IntelliJ e futuro empacotamento.

Sequência de inicialização:

1. resolve o diretório de dados;
2. cria pastas necessárias;
3. define a pasta de logs;
4. inicia o JavaFX;
5. abre e migra o banco;
6. insere somente os dados iniciais ausentes;
7. monta repositórios e serviços;
8. garante recorrências até doze meses à frente;
9. agenda a tentativa de backup automático, se habilitado e devido;
10. carrega `main-view.fxml` e o CSS;
11. abre o Painel.

Se houver falha crítica, o detalhe é registrado em log e o usuário recebe uma mensagem simples para consultar os logs e tentar novamente.

## 10. Persistência e banco de dados

O SQLite é usado como banco embarcado. Cada nova conexão executa:

```sql
PRAGMA foreign_keys = ON;
PRAGMA busy_timeout = 5000;
```

Isso ativa a integridade referencial e permite aguardar até cinco segundos quando o arquivo estiver temporariamente bloqueado.

### Tabelas atuais

| Tabela | Responsabilidade |
|---|---|
| `schema_history` | versões de migração já aplicadas |
| `households` | grupo financeiro local |
| `profiles` | pessoas e perfis compartilhados |
| `categories` | categorias padrão e personalizadas |
| `category_preferences` | nome, cor e visibilidade de categoria padrão por grupo |
| `transactions` | receitas e despesas manuais ou geradas |
| `recurring_expenses` | regras mensais de despesas recorrentes |
| `recurring_expense_occurrences` | ligação entre regra, mês e movimentação gerada |
| `installment_plans` | plano de compra parcelada |
| `installments` | parcela e movimentação correspondente |
| `attachments` | metadados, vínculo e integridade dos arquivos anexados |

Chaves estrangeiras usam, em geral, `RESTRICT` para impedir que a exclusão apague histórico financeiro por cascata. Índices apoiam as consultas por grupo, mês, tipo, situação, perfil, categoria e vencimento.

### Migrações

O mecanismo próprio de migração é pequeno, transacional e idempotente. As versões são aplicadas em ordem e registradas em `schema_history`.

| Versão | Descrição |
|---|---|
| V001 | grupo, perfis e categorias |
| V002 | movimentações |
| V003 | recorrências e parcelamentos |
| V004 | anexos |
| V005 | preferências de categorias padrão por grupo |

Uma versão aplicada não é executada novamente. Falhas provocam rollback da migração e interrompem a inicialização.

### Datas, horários e dinheiro

- datas financeiras sem horário usam `LocalDate` e texto ISO `AAAA-MM-DD`;
- referência mensal usa `YearMonth`/`AAAA-MM`;
- auditoria usa `Instant` e ISO-8601 em UTC;
- serviços dependentes do “hoje” recebem `Clock`, facilitando testes determinísticos;
- valores entram como `BigDecimal` e são armazenados como `INTEGER` em centavos;
- valores precisam ser positivos e exatos em duas casas decimais.

## 11. Dados locais e privacidade

No Windows, o diretório padrão é:

```text
%LOCALAPPDATA%\Hestia\
├── data\
│   └── hestia.db
├── attachments\
├── backups\
├── cache\
├── temp\
└── logs\
```

O diretório pode ser substituído por:

1. propriedade de sistema `hestia.data.dir`;
2. variável de ambiente `HESTIA_DATA_DIR`.

A propriedade tem prioridade sobre a variável. Essa possibilidade é usada nos testes para garantir que dados reais nunca sejam acessados.

O aplicativo não envia informações para serviços externos. A proteção física do diretório depende das permissões e da segurança da conta do Windows.

## 12. Tratamento de erros e observabilidade

O projeto usa SLF4J com Logback. Logs técnicos são gravados na pasta `logs`, enquanto a interface apresenta mensagens curtas em português.

As validações previsíveis utilizam `ValidationException` e exceções específicas, como o limite de perfis ativos. Falhas de infraestrutura são encapsuladas como erro de banco ou convertidas em mensagem de operação não concluída.

Stack traces não são mostrados diretamente ao usuário.

## 13. Tecnologias e versões

| Tecnologia | Uso | Versão atual |
|---|---|---|
| Java | linguagem e runtime | 21 LTS |
| JavaFX Controls/FXML/Swing | interface desktop e integração de imagem | 21.0.8 |
| Maven | build e dependências | compatível com o ambiente |
| SQLite JDBC | banco embarcado | 3.50.3.0 |
| SLF4J | fachada de logging | definida no `pom.xml` |
| Logback | implementação de logging | 1.5.18 |
| Apache PDFBox | leitura e renderização de PDF | 3.0.8 |
| Zip4j | arquivos e criptografia de backup | 2.11.6 |
| Jackson | manifesto JSON de backup | 2.22.0 |
| JUnit Jupiter | testes | 5.13.4 |
| AssertJ | asserções legíveis | 3.27.3 |

Não há Spring nem outro framework de aplicação de grande porte.

## 14. Estratégia de testes

Os testes usam diretórios temporários e bancos isolados. Não acessam `%LOCALAPPDATA%\Hestia`.

A suíte cobre, entre outros pontos:

- criação inicial do banco;
- execução e não repetição de migrações;
- atualização de banco de versões anteriores;
- dados iniciais sem duplicação;
- chaves estrangeiras e restrições;
- criação, edição, desativação, reativação e exclusão segura de perfis e categorias;
- criação de mais de cinco perfis ativos sem limitação artificial;
- preferências de categoria padrão por grupo;
- ciclo de vida de receitas e despesas;
- detecção de vencimento;
- cálculos do painel;
- geração idempotente de recorrências;
- preservação de ocorrências personalizadas;
- distribuição exata de parcelas;
- transação integral na criação de plano parcelado;
- validação e armazenamento seguro de PDF/imagem;
- integridade, consulta e ciclo de vida de anexos;
- backup, criptografia, restauração e rejeição de arquivo malformado;
- carregamento de FXML e CSS;
- comportamento do layout em resoluções desktop.

Após a revisão de UI/UX e a implementação de Relatórios, foram executados **112 testes em 17 suítes**, sem falhas, erros ou testes ignorados. A seção 18 registra o resultado completo.

## 15. Como executar

Pré-requisito: JDK 21 e Maven configurados.

Na raiz do projeto:

```shell
mvn clean test
mvn javafx:run
```

Para gerar o pacote de build:

```shell
mvn clean package
```

No IntelliJ IDEA, o projeto deve ser importado pelo `pom.xml` como projeto Maven. Para execução direta, use `io.github.ryanoviski.hestia.HestiaLauncher`, e não `HestiaApplication`. A configuração Maven `javafx:run` também é uma opção adequada.

## 16. Estrutura principal de arquivos

| Caminho | Papel |
|---|---|
| `pom.xml` | dependências, compilação, testes e execução JavaFX |
| `src/main/java/.../HestiaLauncher.java` | ponto de entrada |
| `src/main/java/.../HestiaApplication.java` | ciclo de vida JavaFX |
| `src/main/java/.../config/ApplicationBootstrap.java` | composição e inicialização |
| `src/main/java/.../application/services/` | casos de uso e validações |
| `src/main/java/.../domain/` | modelos e vocabulário do domínio |
| `src/main/java/.../infrastructure/` | SQLite, repositórios, migrações e arquivos |
| `src/main/java/.../presentation/` | controladores e componentes JavaFX |
| `src/main/resources/fxml/` | estrutura das telas |
| `src/main/resources/styles/main.css` | identidade visual centralizada |
| `src/main/resources/db/migrations/` | versões do esquema SQLite |
| `src/test/` | testes unitários e de integração |
| `README.md` | apresentação e instruções rápidas |
| `docs/architecture.md` | visão resumida da arquitetura |
| `docs/project-report.md` | este retrato completo do produto |

No estado atual, existem 85 arquivos Java de produção, 17 classes de teste, 12 arquivos FXML e 5 migrações SQL.

## 17. Funcionalidades explicitamente fora do escopo

O Hestia não deve possuir:

- cadastro de contas bancárias;
- carteiras financeiras;
- controle de saldo bancário;
- cartões de crédito;
- faturas de cartão;
- limites ou fechamento de fatura;
- transferências entre contas;
- integração bancária;
- qualquer dependência de cartão para compras parceladas.

Essa exclusão é arquitetural e de produto. O sistema acompanha compromissos domésticos, não a estrutura dos produtos bancários usados para pagá-los. Isso reduz complexidade e evita que o domínio dependa de reconciliação de saldo, ciclos de fatura e transferência.

## 18. Estado de qualidade verificado

O levantamento encontrou o repositório sem alterações pendentes antes da criação deste documento. Em 21 de setembro de 2026 foi executado `mvn clean package`, que recompilou a aplicação e os testes, copiou os 18 recursos e gerou `target/hestia-0.1.0-SNAPSHOT.jar` com **BUILD SUCCESS**.

Resultado da execução:

- 112 testes executados;
- 0 falhas;
- 0 erros;
- 0 testes ignorados;
- 17 suítes de teste;
- recursos FXML e CSS cobertos por testes de carregamento;
- dados de teste isolados em diretórios temporários.

O teste JavaFX iniciou o toolkit e carregou as telas em ambiente automatizado. A janela nativa não foi mantida aberta como parte desta verificação documental; a validação cobriu compilação, recursos e construção das views sem erros.

## 19. Limitações e pendências atuais

- Não há instalador ou imagem nativa do Windows; a execução atual depende de JDK/Maven ou da IDE.
- Não há autenticação, múltiplos usuários ou controle de permissões.
- Não há sincronização em nuvem nem compartilhamento entre computadores.
- Não há OCR, leitura automática de comprovantes ou importação financeira.
- Recorrências e parcelamentos tratam apenas despesas.
- Relatórios cobrem inicialmente um mês e a comparação direta com o mês anterior; ainda não há série histórica anual.
- Anexos são abertos pelo aplicativo padrão do Windows no fluxo contextual; a infraestrutura interna de pré-visualização não está exposta por uma rota principal.
- Há código remanescente das telas Calendário e Documentos que não pertence à navegação atual e deverá ser removido ou reintegrado conscientemente.
- README e documentação arquitetural resumida precisam permanecer sincronizados com a evolução de categorias e navegação; este relatório passa a ser a referência detalhada do estado atual.
- A interface gráfica não possui uma suíte extensa de testes end-to-end; a cobertura visual atual se concentra no carregamento de FXML/CSS, componentes e comportamento estrutural do layout.

## 20. Direção recomendada para a próxima etapa

A próxima etapa mais coerente é consolidar a distribuição do aplicativo para Windows e ampliar gradualmente a análise histórica, sem alterar o foco do domínio.

Escopo objetivo recomendado:

1. preparação de um pacote executável para Windows;
2. comparação de vários meses e visão anual;
3. refinamento da exportação PDF com paginação para grandes volumes;
4. remoção ou decisão definitiva sobre o código não navegável de Calendário e Documentos;
5. testes end-to-end adicionais para interações completas com diálogos.

Antes de criar novos módulos, também é recomendável uma rodada curta de uso real com dados de teste para validar os nomes, ações e fluxo entre Movimentações, Contas a pagar e Receitas.

## 21. Conclusão

O Hestia possui hoje uma base sólida, local e testável. A arquitetura separa responsabilidades, o banco evolui por migrações, os valores financeiros são tratados de forma exata e os principais registros preservam histórico. Perfis e categorias possuem ciclo de manutenção completo; movimentações alimentam um painel real; recorrências e parcelas geram compromissos sem duplicar totais; anexos e backups recebem proteções de integridade.

O sistema ainda não é um produto final distribuível, mas já é uma aplicação financeira funcional em desenvolvimento. O foco agora deve ser consolidar a experiência existente, implementar relatórios úteis e preparar a entrega para Windows, mantendo a decisão central de não introduzir contas bancárias, cartões ou faturas no domínio.
