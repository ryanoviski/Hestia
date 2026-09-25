# Hestia

Sistema desktop de gestão financeira pessoal e familiar para organizar receitas, despesas e compromissos mensais em um ambiente simples, acolhedor e privado.

> O Hestia está em desenvolvimento. Esta versão oferece movimentações, compromissos mensais,
> compras parceladas e anexos locais, mas ainda não deve ser usada como registro financeiro definitivo.

## Objetivo e escopo

O projeto pretende reunir perfis individuais e compartilhados, receitas, despesas, contas recorrentes, compras parceladas, categorias, anexos, relatórios e rotinas de backup e restauração.

A etapa atual oferece:

- janela desktop com painel mensal baseado em dados reais;
- cadastro, listagem, edição e desativação de perfis sem limite fixo de quantidade;
- administração de categorias personalizadas, com edição, pesquisa e ativação;
- cadastro e edição de receitas e despesas;
- conclusão, reabertura e cancelamento de movimentações sem apagar o histórico;
- filtros mensais por tipo, situação, perfil e categoria;
- visão de contas pendentes e vencidas;
- despesas recorrentes mensais, com ocorrências automáticas e idempotentes;
- compras parceladas independentes, com parcelas geradas imediatamente;
- identificação da origem manual, recorrente ou parcelada em cada movimentação;
- anexos PDF, PNG e JPEG em receitas, despesas e perfis;
- consulta e abertura dos anexos diretamente nas movimentações e nos perfis;
- backup completo, restauração protegida e backup automático opcional;
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

Também é possível gerar e executar o JAR autocontido:

```shell
mvn clean package
java --enable-native-access=ALL-UNNAMED -jar target/hestia-0.1.0-SNAPSHOT-executable.jar
```

O JAR inclui as dependências da aplicação, mas ainda exige um JDK 21 ou mais recente instalado. Um
instalador nativo do Windows com runtime próprio ainda não faz parte desta versão.

## Fluxos disponíveis

Antes de cadastrar uma movimentação, crie ao menos um perfil em **Perfis**. Categorias padrão já estão disponíveis; categorias próprias podem ser criadas em **Categorias**, escolhendo o tipo Receita ou Despesa.

Para cadastrar uma receita, abra **Receitas**, selecione **Nova receita** e informe descrição, valor, perfil, categoria e data de referência. Receitas podem permanecer previstas ou ser marcadas como recebidas.

Para cadastrar uma despesa, use **Movimentações** ou **Contas a pagar**. Em **Nova conta**, escolha naturalmente entre **Única**, **Recorrente** ou **Parcelada**; o formulário mostra somente os campos compatíveis. Despesas pendentes com vencimento anterior à data atual aparecem como vencidas. Elas podem ser marcadas como pagas e posteriormente reabertas. O cancelamento exige confirmação e preserva o registro no histórico.

As listas aceitam pesquisa e filtros por mês, tipo, situação, perfil e categoria. Os filtros avançados ficam recolhidos até serem necessários. **Contas a pagar** também oferece o filtro “Somente vencidas”.
Movimentações e contas a pagar também podem ser filtradas por origem: manual, recorrente ou parcelada.

## Recorrências e parcelamentos

Ao selecionar **Recorrente** em **Nova conta**, uma regra mensal define descrição, valor previsto, responsável, categoria e
primeiro vencimento. A aplicação garante ocorrências do mês atual até 12 meses à frente e amplia o
horizonte automaticamente nas inicializações futuras. A geração pode ser repetida com segurança: há
uma única ocorrência por regra e competência. Dias 29, 30 ou 31 são ajustados ao último dia do
mês sem perder o dia original nos meses seguintes.

Editar uma movimentação recorrente altera somente aquela ocorrência e a marca como personalizada.
Editar a regra atualiza apenas ocorrências futuras, pendentes e ainda não personalizadas. Ao desativar
uma regra, o comportamento padrão é apenas interromper novas gerações; opcionalmente, podem ser
canceladas as ocorrências futuras pendentes e não personalizadas.

Ao selecionar **Parcelada**, informe o valor total e de 1 a 120 parcelas. A interface apresenta uma prévia calculada pelas mesmas regras usadas na criação. Todas são criadas como despesas
reais e independentes de cartões ou contas. Centavos indivisíveis são distribuídos de forma determinística
nas primeiras parcelas: R$ 100,00 em três resulta em R$ 33,34, R$ 33,33 e R$ 33,33. A ação
**Cancelar parcelas restantes** preserva parcelas pagas e todo o histórico.

Recorrências e planos não são somados diretamente. Painel e contas a pagar usam somente
as movimentações geradas, evitando dupla contagem.

Recorrências e parcelamentos não ocupam opções principais da barra lateral. O gerenciamento específico
continua disponível de forma contextual em **Contas a pagar** e na origem de cada movimentação gerada.

No painel:

- **resultado realizado** = receitas recebidas − despesas pagas;
- **resultado projetado** = receitas não canceladas − despesas não canceladas.

Ambos usam o mês da data de referência. Uma despesa “vencida” continua armazenada como pendente; a condição é calculada diariamente a partir do vencimento.

## Relatórios

**Relatórios** apresenta o resumo do mês, situação de receitas e despesas, distribuição de gastos por
categoria e perfil e comparação com o mês anterior. O período pode ser exportado como um PDF estruturado,
com data de geração e os mesmos cálculos usados pelo painel. A exportação não é uma captura de tela.

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
├── cache/
├── temp/
└── logs/
```

O diretório pode ser substituído pela propriedade de sistema `hestia.data.dir` ou pela variável de ambiente `HESTIA_DATA_DIR`. A propriedade tem precedência. Isso permite isolar ambientes de desenvolvimento e testes sem tocar nos dados reais.

## Anexos e privacidade

Movimentações aceitam vários comprovantes, holerites, cobranças ou outros documentos. Perfis também
podem receber documentos gerais. Os formatos permitidos são PDF, PNG, JPG e JPEG, com limite padrão
de 20 MB por arquivo, configurável por `hestia.attachment.max.bytes`. O Hestia verifica a assinatura
real, o tamanho e o SHA-256; uma extensão permitida com conteúdo incompatível é recusada.

Os arquivos ficam em `attachments/household-{id}/AAAA/MM`, usando nomes físicos aleatórios. O nome
original existe apenas nos metadados e na interface. Os anexos são administrados diretamente na
movimentação ou no perfil relacionado, onde podem ser adicionados, consultados, abertos e removidos.
A remoção não apaga a movimentação nem o perfil. Nenhum arquivo é enviado para serviços externos.

## Backup e restauração

Em **Configurações**, o backup manual gera um arquivo `.hestia-backup` com snapshot íntegro do SQLite,
anexos, manifesto versionado, tamanhos e hashes. Logs, cache, temporários e backups anteriores ficam de
fora. O arquivo pode ser desprotegido ou usar senha com criptografia autenticada AES-256; a senha nunca
é armazenada.

Antes da restauração, formato, caminhos, manifesto, hashes, banco e versão de esquema são validados em
uma área temporária. O Hestia cria um backup de segurança do estado atual antes de substituir banco e
anexos. Backups com esquema futuro, senha incorreta, ZIP Slip, arquivos ausentes ou conteúdo alterado
são rejeitados sem modificar os dados locais.

O backup automático é opcional, nunca é ativado sem consentimento e executa no máximo uma vez ao dia.
Ele não usa senha nesta versão e mantém dez arquivos por padrão, removendo somente backups automáticos
reconhecidos pelo próprio Hestia.

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

Ainda não há OCR, importação automática de documentos, autenticação,
sincronização, compartilhamento ou notificações do sistema. As recorrências desta versão são exclusivamente
mensais e de despesas; parcelamentos também são somente de despesas. Relatórios possuem um primeiro
recorte mensal, mas ainda não oferecem séries históricas anuais ou formatos adicionais de exportação.

## Licença

Distribuído sob a licença MIT. Consulte [LICENSE](LICENSE). As bibliotecas incluídas e suas licenças
estão relacionadas em [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
