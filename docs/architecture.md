# Arquitetura do Hestia

## Visão geral

O Hestia usa uma arquitetura em camadas pequena, com dependências direcionadas para o domínio. A interface não conhece SQL e a infraestrutura não decide regras de negócio.

```text
presentation → application → domain
                     ↓
              infrastructure
```

- **domain**: modelos, enums e exceções que representam conceitos centrais. Não depende de JavaFX nem SQLite.
- **application**: serviços, validações e contratos usados pelos casos de uso. O limite de cinco perfis ativos vive nesta camada.
- **infrastructure**: conexões SQLite, execução de migrações e implementações de repositório.
- **presentation**: telas FXML e controllers. Controllers coletam entrada, chamam serviços e apresentam resultados.
- **config**: resolve os diretórios locais e compõe as dependências na inicialização.
- **util**: reservado para utilitários realmente compartilhados; não há arquivo artificial nesta etapa.

O fluxo comum é `Controller` → `Service` → contrato de `Repository` → implementação SQLite. Por exemplo, o cadastro financeiro percorre `TransactionsController` → `TransactionService` → `TransactionRepository` → `SqliteTransactionRepository`. A inicialização monta explicitamente essas dependências em `ApplicationBootstrap`, sem estado global mutável e sem controllers acoplados entre si.

## Movimentações e situações

`Transaction` representa receitas (`INCOME`) e despesas (`EXPENSE`). O tipo define o efeito financeiro; o valor é sempre positivo. Cada registro referencia um grupo, perfil e categoria e guarda data de referência, vencimento opcional, conclusão opcional, observações e auditoria.

A situação persistida é uma das seguintes:

- `PENDING`: prevista para receita e pendente para despesa;
- `SETTLED`: recebida para receita e paga para despesa;
- `CANCELLED`: cancelada para ambos os tipos.

“Vencida” não é uma situação persistida. É calculada quando uma despesa está pendente e seu vencimento é anterior à data atual. Serviços recebem `Clock`, permitindo testes determinísticos.

Cancelamentos são alterações de situação, nunca exclusões físicas. Perfis e categorias inativos permanecem associados ao histórico, mas não podem ser escolhidos em novos registros. Categorias padrão são globais e somente leitura; categorias personalizadas pertencem ao grupo atual.

## Persistência e migrações

Cada operação abre sua própria conexão e a fecha com `try-with-resources`. Toda conexão ativa `PRAGMA foreign_keys = ON` e `PRAGMA busy_timeout = 5000`. Não existe conexão global permanente.

As migrações são scripts SQL versionados em `src/main/resources/db/migrations`. `MigrationRunner` aplica versões pendentes em ordem e registra versão, descrição e instante UTC em `schema_history`. Cada migração é transacional; uma falha causa rollback e interrompe a inicialização. Os dados iniciais são inseridos separadamente e de forma idempotente.

O banco usa identificadores inteiros estáveis e chaves estrangeiras explícitas. Os instantes de auditoria são armazenados como texto ISO-8601 em UTC e convertidos para `Instant`. Referência, vencimento e conclusão usam `LocalDate` no Java e ISO-8601 no SQLite. Valores monetários usam `BigDecimal` no Java e centavos em colunas inteiras no SQLite, nunca `double` ou `float`. `MoneyUtils` concentra leitura brasileira, conversão exata e formatação `pt-BR`.

Filtros são representados por `TransactionFilter` e transformados em condições preparadas pelo repositório. Assim, pesquisa e filtros mensais não carregam toda a tabela em memória. O painel usa agregações SQL por mês da `reference_date`, exclui canceladas dos resultados e consulta próximos vencimentos separadamente.

## Localização dos dados

No Windows, a raiz padrão é `%LOCALAPPDATA%\Hestia`, contendo `data`, `attachments`, `backups` e `logs`. A propriedade `hestia.data.dir` ou a variável `HESTIA_DATA_DIR` pode substituir essa raiz. Testes sempre usam diretórios temporários isolados.

## Como adicionar um módulo

1. Modele conceitos e regras independentes de tecnologia em `domain`.
2. Crie um serviço em `application` para o caso de uso e um contrato de repositório somente quando houver persistência.
3. Implemente persistência em `infrastructure`; alterações de esquema entram em uma nova migração, nunca modificando uma versão já distribuída.
4. Crie a tela FXML e um controller pequeno em `presentation`, injetando o serviço durante a composição.
5. Adicione testes unitários da regra e testes de integração com SQLite temporário.

## Limites do domínio

O Hestia organiza compromissos, receitas e despesas pessoais ou familiares, não patrimônios mantidos por instituições financeiras. Por isso, contas bancárias, carteiras, saldos, cartões, faturas, limites, transferências e integração bancária não fazem parte do domínio. Essa decisão reduz complexidade e mantém o produto centrado no planejamento financeiro familiar. Compras parceladas serão modeladas futuramente como compromissos independentes.
