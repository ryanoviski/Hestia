# Hestia

Sistema desktop de gestão financeira pessoal e familiar para organizar receitas, despesas e compromissos mensais em um ambiente simples, acolhedor e privado.

> O Hestia está em desenvolvimento. Esta versão oferece o primeiro fluxo financeiro completo, mas ainda não deve ser usada como registro financeiro definitivo.

## Objetivo e escopo

O projeto pretende reunir perfis individuais e compartilhados, receitas, despesas, contas recorrentes, compras parceladas, categorias, orçamentos, anexos, calendário financeiro, relatórios e rotinas de backup e restauração.

A etapa atual oferece:

- janela desktop com painel mensal baseado em dados reais;
- cadastro, listagem e desativação de até cinco perfis ativos;
- administração de categorias personalizadas, com edição, pesquisa e ativação;
- cadastro e edição de receitas e despesas;
- conclusão, reabertura e cancelamento de movimentações sem apagar o histórico;
- filtros mensais por tipo, situação, perfil e categoria;
- visão de contas pendentes e vencidas;
- banco SQLite criado automaticamente, com migrações e dados iniciais idempotentes;
- categorias padrão de receita e despesa;
- logs técnicos e tratamento amigável de falhas críticas;
- testes automatizados da infraestrutura e das regras iniciais.

Contas bancárias, carteiras, saldo bancário, cartões de crédito, faturas, limites, transferências e integrações bancárias estão explicitamente fora do domínio do Hestia. Parcelamentos serão compromissos independentes, sem vínculo com cartões.

## Tecnologias

- Java 21 LTS (o código também foi verificado com um JDK mais recente usando `--release 21`);
- JavaFX 21 e FXML;
- Maven;
- SQLite e SQLite JDBC;
- SLF4J com Logback;
- JUnit 5 e AssertJ.

## Pré-requisitos

- JDK 21 ou mais recente;
- Maven 3.9 ou mais recente disponível no `PATH`.

## Executar

Na raiz do projeto:

```shell
mvn javafx:run
```

Na primeira abertura, o Hestia cria automaticamente seus diretórios, banco e categorias iniciais.

## Fluxos disponíveis

Antes de cadastrar uma movimentação, crie ao menos um perfil em **Perfis**. Categorias padrão já estão disponíveis; categorias próprias podem ser criadas em **Categorias**, escolhendo o tipo Receita ou Despesa.

Para cadastrar uma receita, abra **Receitas**, selecione **Nova movimentação** e informe descrição, valor, perfil, categoria e data de referência. Receitas podem permanecer previstas ou ser marcadas como recebidas.

Para cadastrar uma despesa, use **Movimentações** ou **Contas a pagar**. Despesas pendentes com vencimento anterior à data atual aparecem como vencidas. Elas podem ser marcadas como pagas e posteriormente reabertas. O cancelamento exige confirmação e preserva o registro no histórico.

As listas aceitam pesquisa e filtros por mês, tipo, situação, perfil e categoria. **Contas a pagar** também oferece o filtro “Somente vencidas”.

No painel:

- **resultado realizado** = receitas recebidas − despesas pagas;
- **resultado projetado** = receitas não canceladas − despesas não canceladas.

Ambos usam o mês da data de referência. Uma despesa “vencida” continua armazenada como pendente; a condição é calculada diariamente a partir do vencimento.

## Testar e compilar

Os comandos abaixo foram verificados nesta etapa:

```shell
mvn clean test
mvn clean package
```

## Dados do usuário

No Windows, por padrão, os arquivos ficam em `%LOCALAPPDATA%\Hestia`:

```text
Hestia/
├── data/hestia.db
├── attachments/
├── backups/
└── logs/
```

O diretório pode ser substituído pela propriedade de sistema `hestia.data.dir` ou pela variável de ambiente `HESTIA_DATA_DIR`. A propriedade tem precedência. Isso permite isolar ambientes de desenvolvimento e testes sem tocar nos dados reais.

Exemplo no PowerShell:

```powershell
$env:HESTIA_DATA_DIR = "$PWD\.hestia"
mvn javafx:run
```

## Estrutura resumida

```text
src/main/java/io/github/ryanoviski/hestia/
├── application/       # serviços, filtros, validação e contratos de persistência
├── config/            # composição e caminhos da aplicação
├── domain/            # modelos, enums e exceções centrais
├── infrastructure/    # SQLite, migrações e repositórios
└── presentation/      # controllers JavaFX

src/main/resources/
├── db/migrations/     # scripts SQL versionados
├── fxml/              # estrutura das telas
└── styles/            # identidade visual
```

Consulte [docs/architecture.md](docs/architecture.md) para as decisões e convenções arquiteturais.

## Limitações atuais e próxima etapa

Ainda não há contas recorrentes, compras parceladas, anexos, calendário, orçamentos, relatórios completos, backup, autenticação, sincronização ou importação. A próxima etapa planejada é implementar despesas recorrentes e compras parceladas como compromissos independentes, seguida pelo calendário financeiro.

## Licença

Distribuído sob a licença MIT. Consulte [LICENSE](LICENSE).
