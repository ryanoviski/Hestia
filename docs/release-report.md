# Relatório final de preparação para lançamento — Hestia 1.0.0

**Data:** 25 de setembro de 2026  
**Plataforma:** Windows x64  
**Estado:** candidata tecnicamente distribuível para homologação controlada; publicação ampla condicionada
à validação manual em Windows limpo.

## 1. Versão da release

- versão anterior de desenvolvimento: `0.1.0-SNAPSHOT`;
- versão candidata: `1.0.0`;
- política: `MAJOR.MINOR.PATCH`;
- versão atualizada no POM, no manifesto, em `hestia.properties`, na interface e na documentação;
- nome público da aplicação: **Hestia 1.0.0**.

Os relatórios `project-report.md` e `pre-release-audit.md` preservam a versão antiga porque são registros
históricos. Ambos receberam um aviso apontando para este documento.

## 2. Linha de base

A preparação começou no commit `b3f6691ec66fbd0b22aa76840301fbed1d3a1f6a`, com a árvore de trabalho
limpa. Antes das mudanças, `mvn clean test` aprovou 131 de 131 testes e `mvn clean package` concluiu com
sucesso. Nenhum commit, push, release remota ou publicação foi realizado nesta etapa.

## 3. Alterações realizadas

- promoção da versão para `1.0.0`;
- criação do processo automatizado de empacotamento Windows;
- runtime Java 21 privado e reduzido com `jlink`;
- instalador EXE por usuário com atalhos e desinstalação;
- ícone ICO com múltiplas resoluções;
- atualização de dependências vulneráveis;
- varredura final de segurança;
- script separado para futura assinatura Authenticode;
- documentação de build, instalação, atualização, dados, assinatura e homologação;
- geração de hash SHA-256 e inclusão de licença e avisos de terceiros.
- acesso direto de “Próximos vencimentos” para a visão completa de Contas a pagar.

O artefato reconstruído também incorpora as melhorias financeiras e de UI/UX implementadas após a
preparação inicial. Não houve nova migration nem mudança incompatível no formato de dados.

## 4. Runtime incluído

O instalador contém Microsoft Build of OpenJDK `21.0.12.1` LTS x64. O download oficial é validado pelo
SHA-256 fixado no script:

```text
192441A9D27DA813BADA974BB88B4CF64D37A9589ED37F204374D411CA5CE07F
```

O runtime foi produzido com:

```text
java.base, java.desktop, java.logging, java.management, java.naming,
java.scripting, java.sql, java.xml, jdk.charsets, jdk.crypto.ec,
jdk.jfr, jdk.unsupported, jdk.unsupported.desktop
```

O usuário final não precisa instalar Java, Maven ou IntelliJ.

## 5. Instalador Windows

| Item | Resultado |
| --- | --- |
| Tecnologia | Maven + `jlink` + `jpackage` + WiX 3.14.1 |
| Formato | EXE x64 |
| Arquivo | `release/Hestia-1.0.0-Setup.exe` |
| Tamanho | 97.891.840 bytes |
| Versão do produto | 1.0.0 |
| Fabricante | Ryan |
| Runtime incluso | Sim |
| Escopo | Por usuário, sem exigir administrador |
| Atalhos | Menu Iniciar e Área de Trabalho |
| Diretório padrão | `%LOCALAPPDATA%\Hestia Application` |

O UUID de upgrade é estável para futuras versões. O instalador não contém banco, logs, anexos, backups ou
dados pessoais.

## 6. Diretórios e separação de dados

- programa: `%LOCALAPPDATA%\Hestia Application`;
- banco: `%LOCALAPPDATA%\Hestia\data\hestia.db`;
- anexos: `%LOCALAPPDATA%\Hestia\attachments`;
- backups: `%LOCALAPPDATA%\Hestia\backups`;
- logs: `%LOCALAPPDATA%\Hestia\logs`;
- dados isolados de teste: definidos por `HESTIA_DATA_DIR`.

Programa e dados ficam separados. Atualizar ou desinstalar os binários não deve apagar os dados
financeiros.

## 7. Teste de instalação

**Status: PASSOU.** O instalador final foi executado sem privilégios administrativos e retornou código
zero. Foram verificados o diretório do programa, o runtime privado, o executável, o registro da versão
`1.0.0`, o Menu Iniciar e o atalho da Área de Trabalho.

Uma tentativa anterior de pacote para toda a máquina falhou corretamente por falta de privilégios. O
empacotamento foi corrigido para instalação por usuário e a versão final passou.

## 8. Primeira execução

**Status: PASSOU.** A aplicação instalada foi aberta fora da IDE com diretório de dados isolado. O
processo permaneceu ativo, criou o SQLite automaticamente e registrou o log no local esperado. O banco
gerado tinha 188.416 bytes e as migrations/dados iniciais foram aplicados.

## 9. Atualização e reinstalação

**Atualização real de uma versão anterior para 1.0.0: NÃO TESTADA.** Não existia um instalador nativo
anterior confiável para um ensaio A→B.

**Reinstalação da mesma versão: PASSOU.** O Hestia foi instalado, aberto, desinstalado, reinstalado e
aberto novamente usando os mesmos dados isolados. A integridade lógica permaneceu válida:

- `PRAGMA integrity_check`: `ok`;
- violações de chaves estrangeiras: 0;
- migrations registradas: 5;
- grupo padrão: 1;
- categorias iniciais: 17;
- nenhuma duplicação de perfis, movimentações ou demais entidades vazias.

O hash físico do SQLite não é usado como critério de igualdade porque o próprio SQLite pode alterar
páginas e cabeçalhos entre aberturas sem alterar o conteúdo lógico.

## 10. Desinstalação

**Status: PASSOU.** A desinstalação retornou código zero e removeu aplicação, runtime, atalhos e entrada
de desinstalação. O diretório de dados foi preservado, permitindo que a reinstalação recuperasse o estado.

## 11. Revisão visual e usabilidade

**Status: NÃO TESTADA MANUALMENTE NESTA SESSÃO.** O ambiente não forneceu captura ou controle de janelas
nativas JavaFX. Permanecem pendentes a revisão visual em 980×640, 1200×760 e 1920×1080, escalas de DPI,
navegação por teclado e fluxo completo por uma pessoa usuária.

Como cobertura automatizada, passaram 22 smoke tests JavaFX e 27 testes de recursos/FXML. Isso confirma
carregamento técnico, mas não substitui homologação visual.

## 12. Vulnerabilidades

A varredura inicial com OSV-Scanner 2.4.0 encontrou 7 avisos em 3 dependências. Foram atualizados:

- Jackson Databind `2.22.0` → `2.22.1`;
- Logback `1.5.18` → `1.5.34`;
- AssertJ `3.27.3` → `3.27.7`.

A varredura final retornou `No issues found`: 0 críticas, 0 altas, 0 médias, 0 baixas e 0 desconhecidas.
Nenhuma supressão foi adicionada. O resultado detalhado está em `release-security-report.md`.

## 13. Assinatura digital

**Status: PENDENTE — CERTIFICADO NÃO DISPONÍVEL.** Não há certificado Code Signing no repositório de
certificados do usuário. O instalador não foi assinado e pode receber aviso do SmartScreen.

O script `scripts/sign-release.ps1` está preparado para assinatura SHA-256 e timestamp de autoridade
certificadora. Chaves privadas, PFX e senhas não devem ser versionados.

## 14. Integridade do artefato

SHA-256 do instalador final:

```text
0DB932BD8337DFEBEAD61B42A26B8D504F3CA1FE3F029B651C5B262CF9072CED
```

O valor está em `release/SHA256SUMS.txt`. Uma assinatura futura altera o arquivo e exige recalcular e
republicar o hash.

## 15. Licenças e avisos

O Hestia permanece sob MIT. `LICENSE` e `THIRD-PARTY-NOTICES.md` acompanham os artefatos. Os avisos foram
atualizados para refletir as versões finais das bibliotecas. A revisão é técnica e não constitui parecer
jurídico.

## 16. Testes automatizados finais

`mvn clean test`:

- total: 146;
- aprovados: 146;
- falhas: 0;
- erros: 0;
- ignorados: 0;
- resultado: `BUILD SUCCESS`;
- duração registrada na reconstrução: 16,256 s.

## 17. Build final

O build final foi executado pelo script de release, que chama `mvn clean package`. Resultado:

- 146 testes aprovados;
- `BUILD SUCCESS`;
- duração Maven registrada: 16,256 s;
- JAR executável usado como entrada: `hestia-1.0.0-executable.jar`;
- instalador e hash gerados com sucesso.

## 18. Execução fora da IDE

**Status: PASSOU.** Foram iniciados tanto o `app-image` gerado pelo `jpackage` quanto a aplicação instalada
pelo EXE. Em ambos os casos o processo permaneceu ativo e usou o runtime incluído, sem depender do JDK
global ou do IntelliJ.

## 19. Windows limpo

**Status: NÃO TESTADO.** Instalação, abertura e desinstalação passaram na máquina de desenvolvimento, mas
uma máquina virtual ou computador Windows 10/11 x64 realmente limpo ainda deve validar ausência de Java,
SmartScreen, antivírus, DPI, permissões e atalhos. O roteiro completo está em `windows-release.md`.

## 20. Arquivos alterados ou criados

- `.gitignore`;
- `pom.xml`;
- `README.md`;
- `THIRD-PARTY-NOTICES.md`;
- `src/test/java/io/github/ryanoviski/hestia/config/ApplicationInfoTest.java`;
- `scripts/build-release.ps1`;
- `scripts/sign-release.ps1`;
- `docs/windows-release.md`;
- `docs/release-security-report.md`;
- `docs/release-report.md`;
- avisos históricos em `docs/project-report.md` e `docs/pre-release-audit.md`.

## 21. Artefatos gerados

- `release/Hestia-1.0.0-Setup.exe`;
- `release/SHA256SUMS.txt`;
- `release/LICENSE`;
- `release/THIRD-PARTY-NOTICES.md`;
- `release/SECURITY-SCAN.md`;
- relatórios brutos locais `release/osv-scan.json` e `release/osv-scan.md`;
- `release/Hestia/`, imagem descompactada para testes.

O diretório `release/` é ignorado pelo Git para impedir versionamento acidental de binários grandes.

## 22. Bloqueadores restantes para publicação ampla

1. homologação do instalador e da interface em Windows 10/11 x64 limpo;
2. teste real de atualização A→B quando existir uma versão nativa anterior.

O primeiro item é bloqueador da publicação ampla. O segundo é bloqueador para prometer atualização
automática segura, mas não impede uma primeira versão quando documentado.

## 23. Pendências não bloqueadoras

- adquirir certificado e assinar o instalador;
- construir reputação do executável no SmartScreen;
- repetir a varredura de vulnerabilidades imediatamente antes da publicação;
- fazer revisão jurídica independente das licenças, se exigida;
- automatizar testes do instalador em CI/VM Windows;
- ensaiar carga volumosa, DPI elevado, leitor de tela e navegação completa por teclado.

## 24. Limitações e decisão recomendada

O pacote está pronto para **homologação controlada** e testes com usuários conhecidos. Build, testes,
segurança automatizada, instalação, primeira abertura, persistência, reinstalação e desinstalação passaram.

Ainda não é recomendável anunciar a candidata como lançamento público amplo até concluir o checklist em
um Windows limpo e registrar a inspeção visual. A ausência de assinatura não invalida tecnicamente o EXE,
mas deve ser informada porque pode causar alertas de confiança. Após esses dois aceites manuais, e uma nova
confirmação do SHA-256, a versão pode ser disponibilizada como Hestia 1.0.0.
