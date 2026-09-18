# Hestia

Sistema desktop de gestão financeira pessoal e familiar para organizar receitas, despesas e compromissos mensais em um ambiente simples, acolhedor e privado.

> O Hestia está em desenvolvimento. Esta versão estabelece a arquitetura, o armazenamento local e a interface inicial; ainda não deve ser usada como registro financeiro definitivo.

## Objetivo e escopo

O projeto pretende reunir perfis individuais e compartilhados, receitas, despesas, contas recorrentes, compras parceladas, categorias, orçamentos, anexos, calendário financeiro, relatórios e rotinas de backup e restauração.

A etapa atual oferece:

- janela desktop com painel, navegação e estados vazios;
- cadastro, listagem e desativação de até cinco perfis ativos;
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
├── application/       # serviços, validação e contratos de persistência
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

## Licença

Distribuído sob a licença MIT. Consulte [LICENSE](LICENSE).
