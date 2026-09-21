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

Operações compostas usam `FinancialCommitmentRepository` como fronteira transacional. Os serviços
calculam e validam as regras; o repositório persiste regra, vínculos e movimentações na mesma conexão,
fazendo rollback integral em caso de falha.

## Movimentações e situações

`Transaction` representa receitas (`INCOME`) e despesas (`EXPENSE`). O tipo define o efeito financeiro; o valor é sempre positivo. Cada registro referencia um grupo, perfil e categoria e guarda data de referência, vencimento opcional, conclusão opcional, observações e auditoria.

A situação persistida é uma das seguintes:

- `PENDING`: prevista para receita e pendente para despesa;
- `SETTLED`: recebida para receita e paga para despesa;
- `CANCELLED`: cancelada para ambos os tipos.

“Vencida” não é uma situação persistida. É calculada quando uma despesa está pendente e seu vencimento é anterior à data atual. Serviços recebem `Clock`, permitindo testes determinísticos.

Cancelamentos são alterações de situação, nunca exclusões físicas. Perfis e categorias inativos permanecem associados ao histórico, mas não podem ser escolhidos em novos registros. Categorias padrão permanecem globais no banco, mas `category_preferences` permite personalizar nome, cor e visibilidade por grupo sem alterar a linha original; categorias personalizadas pertencem ao grupo atual.

## Persistência e migrações

Cada operação abre sua própria conexão e a fecha com `try-with-resources`. Toda conexão ativa `PRAGMA foreign_keys = ON` e `PRAGMA busy_timeout = 5000`. Não existe conexão global permanente.

As migrações são scripts SQL versionados em `src/main/resources/db/migrations`. `MigrationRunner` aplica versões pendentes em ordem e registra versão, descrição e instante UTC em `schema_history`. Cada migração é transacional; uma falha causa rollback e interrompe a inicialização. Os dados iniciais são inseridos separadamente e de forma idempotente.

O banco usa identificadores inteiros estáveis e chaves estrangeiras explícitas. Os instantes de auditoria são armazenados como texto ISO-8601 em UTC e convertidos para `Instant`. Referência, vencimento e conclusão usam `LocalDate` no Java e ISO-8601 no SQLite. Valores monetários usam `BigDecimal` no Java e centavos em colunas inteiras no SQLite, nunca `double` ou `float`. `MoneyUtils` concentra leitura brasileira, conversão exata e formatação `pt-BR`.

Filtros são representados por `TransactionFilter` e transformados em condições preparadas pelo repositório. Assim, pesquisa e filtros mensais não carregam toda a tabela em memória. O painel usa agregações SQL por mês da `reference_date`, exclui canceladas dos resultados e consulta próximos vencimentos separadamente.

## Recorrências e parcelamentos

`recurring_expenses` guarda a regra mensal e `recurring_expense_occurrences` vincula cada competência
a exatamente uma linha de `transactions`. A restrição única `(recurring_expense_id, reference_month)`
protege contra inicializações, cliques ou navegações concorrentes. A aplicação gera do mês corrente até
12 meses adiante e estende esse horizonte nas inicializações futuras. Uma ocorrência editada
é marcada como `customized`; atualizações da regra alcançam somente ocorrências futuras, pendentes e não
personalizadas. Regras são desativadas, nunca removidas.

`installment_plans` preserva o valor contratado e a quantidade original. `installments` vincula cada
número de parcela a uma movimentação de despesa, com unicidade por plano e número. O total em centavos
é dividido por inteiros e o resto é atribuído, em ordem, às primeiras parcelas. A situação do plano é
derivada das situações das parcelas: em andamento, concluído, parcialmente cancelado ou cancelado.
Cancelar restantes altera apenas movimentações pendentes atuais ou futuras e preserva as pagas.

Ambas as origens criam `transactions` do tipo `EXPENSE`. Regra e plano nunca entram diretamente em
agregações financeiras: esta única fonte de verdade evita dupla contagem. As consultas usam `LEFT JOIN`
com os vínculos para apresentar `Manual`, `Recorrente` ou `Parcela N de M`.

Vencimentos mensais são sempre calculados a partir do dia do primeiro vencimento e do `YearMonth` de
destino. Se o dia não existir, usa-se o último dia daquele mês; o ajuste de fevereiro não se propaga.

Na apresentação, recorrências e parcelamentos não são módulos principais. O fluxo **Nova conta** escolhe
entre conta única, recorrente e parcelada e delega a operação ao serviço correspondente. O gerenciamento
das regras e planos continua contextual em Contas a pagar, sem misturar essa composição visual com a
regra financeira.

## Relatórios

`ReportService` combina as agregações mensais já fornecidas por `TransactionRepository.summarize` com
as movimentações do período para agrupar despesas por perfil. O DTO `MonthlyReport` contém o período
atual e o anterior, permitindo calcular comparações fora do controller. `ReportPdfService` recebe esse
DTO e produz um documento PDF estruturado com PDFBox; ele não conhece JavaFX e pode ser testado sem UI.
Relatórios são somente leitura e não exigem tabela ou migração adicional.

## Anexos e integridade

`attachments` guarda somente metadados e uma `storage_key` relativa. Exatamente um alvo é obrigatório:
movimentação ou perfil. PDFs e imagens nunca são BLOBs. O armazenamento físico usa
`attachments/household-{id}/AAAA/MM/{UUID}.{extensão}`, sem incorporar o nome fornecido pelo usuário.
Todo caminho é resolvido, normalizado e confirmado dentro da raiz; caminhos absolutos, `..`, links
simbólicos inesperados e sobrescritas são rejeitados.

`FileValidationService` compara extensão com as assinaturas PDF, PNG e JPEG, impõe o limite configurado,
mede o arquivo e calcula SHA-256. A importação copia primeiro para `temp/`, revalida a cópia e só então
move para o destino. Se a persistência falhar, o arquivo é compensado. A remoção faz o movimento inverso
para quarentena antes de excluir os metadados. O diagnóstico compara registros, arquivos, hashes e
temporários sem remover órfãos automaticamente.

Os anexos são acessados no contexto da movimentação ou do perfil relacionado. A interface nunca revela
a chave física e solicita confirmação antes de remover um arquivo, preservando o registro financeiro.

## Backup e restauração

O backup `.hestia-backup` é um contêiner ZIP com `manifest.json`, snapshot `database/hestia.db` e os
anexos válidos. `VACUUM INTO` produz o snapshot consistente e `PRAGMA integrity_check` o valida antes da
compactação. O manifesto registra versão do formato, aplicação e esquema, instante, grupo e, para cada
arquivo, caminho relativo, tamanho e SHA-256. Logs, cache, temporários, preferências com caminhos externos
e backups anteriores não entram no pacote.

Zip4j oferece proteção opcional AES-256; nenhuma senha é persistida ou registrada. Backups automáticos
são explicitamente habilitados pelo usuário, não usam senha nesta versão, executam no máximo uma vez ao
dia e aplicam retenção apenas a nomes gerados pelo Hestia.

A restauração valida extensão, estrutura ZIP, quantidade e tamanhos, protege contra ZIP Slip, confere
manifesto, hashes, integridade do SQLite e compatibilidade do esquema antes de tocar nos dados ativos.
Ela extrai em área temporária e cria primeiro um backup de segurança. Banco e anexos anteriores são
mantidos em uma segunda área de reversão durante a substituição; qualquer falha restaura ambos. Bancos
mais antigos são aceitos e passam pelas migrações na inicialização seguinte; esquemas futuros são rejeitados.

## Localização dos dados

No Windows, a raiz padrão é `%LOCALAPPDATA%\Hestia`, contendo `data`, `attachments`, `backups`, `cache`, `temp` e `logs`. A propriedade `hestia.data.dir` ou a variável `HESTIA_DATA_DIR` pode substituir essa raiz. Testes sempre usam diretórios temporários isolados.

## Como adicionar um módulo

1. Modele conceitos e regras independentes de tecnologia em `domain`.
2. Crie um serviço em `application` para o caso de uso e um contrato de repositório somente quando houver persistência.
3. Implemente persistência em `infrastructure`; alterações de esquema entram em uma nova migração, nunca modificando uma versão já distribuída.
4. Crie a tela FXML e um controller pequeno em `presentation`, injetando o serviço durante a composição.
5. Adicione testes unitários da regra e testes de integração com SQLite temporário. Operações que criem
   mais de um registro devem possuir uma fronteira transacional explícita e restrições únicas no banco.

## Limites do domínio

O Hestia organiza compromissos, receitas e despesas pessoais ou familiares, não patrimônios mantidos por instituições financeiras. Por isso, contas bancárias, carteiras, saldos, cartões, faturas, limites, transferências e integração bancária não fazem parte do domínio. Essa decisão reduz complexidade e mantém o produto centrado no planejamento financeiro familiar. Compras parceladas são compromissos independentes e não contêm instituição, bandeira, número ou qualquer outro conceito de cartão.
