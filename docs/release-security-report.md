# Security scan — Hestia 1.0.0

Data: 25 de setembro de 2026  
Ferramenta: OSV-Scanner 2.4.0 / osv-scalibr 0.4.5  
Escopo: dependências Maven resolvidas a partir de `pom.xml`

## Resultado inicial

O primeiro scan encontrou 7 vulnerabilidades corrigíveis em 3 pacotes:

| Dependência | Versão | IDs | Severidade OSV | Correção |
| --- | --- | --- | --- | --- |
| `jackson-databind` | 2.22.0 | GHSA-5gvw-p9qm-jgwh, GHSA-5jmj-h7xm-6q6v | 2 médias | 2.22.1 |
| `logback-core` | 1.5.18 | GHSA-25qh-j22f-pwp8, GHSA-jhq6-gfmj-v8fx, GHSA-p47f-322f-whfh, GHSA-qqpg-mvqg-649v | 1 média, 3 baixas | 1.5.34 |
| `assertj-core` (teste) | 3.27.3 | GHSA-rqfh-9r24-8c9r | 1 alta | 3.27.7 |

Todas foram corrigidas por atualização compatível e validadas pela suíte completa. Nenhuma supressão foi
adicionada.

## Resultado final

```text
No issues found
```

| Severidade | Quantidade |
| --- | ---: |
| Critical | 0 |
| High | 0 |
| Medium | 0 |
| Low | 0 |
| Unknown | 0 |

Comando utilizado:

```powershell
osv-scanner scan source --recursive --format markdown .
```

O resultado representa a base OSV consultada na data indicada. Uma release futura deve executar novamente
o scan, pois bancos de vulnerabilidades e a aplicabilidade dos avisos mudam ao longo do tempo.

