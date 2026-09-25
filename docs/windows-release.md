# Release Windows — Hestia

## Estratégia

O Hestia usa Maven para compilar um JAR autocontido, `jlink` para produzir um runtime Microsoft OpenJDK
21 LTS reduzido e `jpackage` para criar o instalador Windows. O formato escolhido é EXE porque oferece um
fluxo familiar ao usuário e suporta Menu Iniciar, atalho, escolha de diretório e desinstalação pelo Windows.
O `jpackage` usa WiX 3.14.1 portátil durante o build.

O instalador contém Java, JavaFX, SQLite JDBC, PDFBox, Zip4j, Jackson e Logback. O usuário não precisa
instalar Java, Maven ou IntelliJ. A instalação é feita por usuário e não exige privilégios administrativos.

## Build reproduzível

Pré-requisitos de desenvolvimento:

- Windows x64;
- PowerShell;
- Maven 3.9+;
- acesso HTTPS para o primeiro download das ferramentas.

Execute:

```powershell
.\scripts\build-release.ps1 -PackageType exe
```

O script:

1. exige versão `MAJOR.MINOR.PATCH`, sem `SNAPSHOT`;
2. executa `mvn clean package`;
3. baixa OpenJDK 21.0.12.1 e WiX 3.14.1 de fontes oficiais;
4. valida os downloads por SHA-256 fixado;
5. cria um ICO com 16, 32, 48, 64, 128 e 256 pixels;
6. monta o runtime com `jlink`;
7. gera o instalador com `jpackage`;
8. copia licença, avisos de terceiros e relatório de segurança;
9. produz `SHA256SUMS.txt`.

Downloads ficam em `.release-tools/`; artefatos finais ficam em `release/`. Ambos são ignorados pelo Git.

## Runtime

Módulos incluídos:

```text
java.base, java.desktop, java.logging, java.management, java.naming,
java.scripting, java.sql, java.xml, jdk.charsets, jdk.crypto.ec,
jdk.jfr, jdk.unsupported, jdk.unsupported.desktop
```

## Dados do usuário

Programa e dados são independentes:

- aplicação: por padrão, `%LOCALAPPDATA%\Hestia Application`;
- dados: `%LOCALAPPDATA%\Hestia`;
- banco: `%LOCALAPPDATA%\Hestia\data\hestia.db`;
- anexos: `%LOCALAPPDATA%\Hestia\attachments`;
- backups: `%LOCALAPPDATA%\Hestia\backups`;
- logs: `%LOCALAPPDATA%\Hestia\logs`;
- preferências: `%LOCALAPPDATA%\Hestia\data`.

Atualizar ou desinstalar o programa não deve remover dados financeiros. O usuário deve apagar a pasta de
dados manualmente somente se desejar remover definitivamente banco, anexos, backups e configurações.
Backups salvos em outro diretório nunca pertencem ao instalador.

## Atualização

O UUID de upgrade do instalador é estável. Uma nova versão deve incrementar o POM, usar o mesmo UUID e
ser instalada sobre a anterior. O instalador atualiza os binários; na primeira abertura, o Hestia executa
as migrations. Não coloque lógica de banco no instalador.

Antes de publicar uma atualização:

1. copie uma base de teste da versão anterior;
2. instale a versão anterior e crie perfis, categorias, movimentos, recorrências, parcelas e anexos;
3. instale a nova versão;
4. abra e valide migrations e integridade;
5. confirme todos os dados, PDF, backup e restore.

## Assinatura digital

Assinatura real está pendente até existir um certificado Code Signing válido no repositório de
certificados do Windows. Nunca versione PFX, chave privada ou senha.

Quando o certificado e a URL de timestamp fornecida pela autoridade certificadora estiverem disponíveis:

```powershell
.\scripts\sign-release.ps1 `
  -Artifact .\release\Hestia-1.0.0-Setup.exe `
  -CertificateThumbprint "THUMBPRINT_REAL" `
  -TimestampUrl "URL_FORNECIDA_PELA_AUTORIDADE"
```

O script assina com SHA-256, verifica pelo mecanismo Authenticode e só então recalcula o SHA-256. Assinar
ajuda na identificação/reputação, mas não garante a ausência de alertas do SmartScreen.

## Checklist em Windows limpo

- [ ] Windows 10/11 x64 atualizado;
- [ ] Java global não instalado;
- [ ] Hestia não instalado;
- [ ] conferir o SHA-256 publicado;
- [ ] registrar alertas do Windows/SmartScreen;
- [ ] executar o instalador;
- [ ] verificar versão e ícones;
- [ ] abrir pelo Menu Iniciar e pelo atalho;
- [ ] confirmar que o uso diário não exige administrador;
- [ ] criar perfil e categoria;
- [ ] criar receita, despesa e conta;
- [ ] criar parcelamento e recorrência;
- [ ] testar nomes longos, acentos e muitos registros;
- [ ] fechar/reabrir e conferir persistência;
- [ ] gerar relatório/PDF;
- [ ] adicionar/abrir/remover anexo;
- [ ] criar e restaurar backup;
- [ ] instalar versão superior e conferir dados/migrations;
- [ ] desinstalar em Aplicativos instalados;
- [ ] confirmar remoção de programa, atalhos e runtime;
- [ ] confirmar preservação de `%LOCALAPPDATA%\Hestia`;
- [ ] reinstalar e confirmar reaparecimento dos dados.

O teste em uma máquina/VM Windows realmente limpa deve ser registrado antes da publicação ampla.

