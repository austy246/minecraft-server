# Minecraft Server na Raspberry Pi 5

> Aktualizováno: červen 2026 (v3, nahrazuje v2).
> Hlavní změny oproti v2: verze serveru je **26.1.2** (ne 1.21.11), **Bedrock/Geyser byl odstraněn**,
> přidány pluginy **SkinsRestorer** a **TreeTimber (Timber)**.

## Základní informace

| Položka | Hodnota |
| :---- | :---- |
| Zařízení | Raspberry Pi 5 (8GB RAM) |
| OS | Debian 13 (trixie) 64-bit na Raspberry Pi |
| Minecraft verze | 26.1.2 (Paper) |
| Server software | Paper + SimpleShops + Simple Voice Chat + SkinsRestorer + TreeTimber |
| Vzdálený přístup | Playit.gg |

> Pozn. k verzování: Minecraft přešel na nové verzování (rok-based, `26.1.x`). Dřívější údaj `1.21.11` byl chybný.

## Dokumentace

| Dokument | Obsah |
| :---- | :---- |
| [Připojení a účty](docs/pripojeni.md) | Adresy, Java/Bedrock, lokální připojení, skiny (`online-mode=false`) |
| [Správa serveru](docs/sprava.md) | Struktura souborů, příkazy, konzole, autostart (crontab) |
| [Zálohy](docs/zalohy.md) | Automatické zálohy, reset světa, obnovení |
| [Playit.gg tunely](docs/playit.md) | Tunely pro Java a Voice Chat |
| [Pluginy](docs/pluginy.md) | SkinsRestorer, TreeTimber, SimpleShops, Simple Voice Chat |
| [Výkon](docs/vykon.md) | Doporučený počet hráčů |
| [Řešení problémů](docs/reseni-problemu.md) | Server/Playit/skiny nefungují, obnovení Geyseru |

## Užitečné odkazy

- **Paper MC:** https://papermc.io
- **Playit.gg:** https://playit.gg
- **SkinsRestorer:** https://modrinth.com/plugin/skinsrestorer
- **TreeTimber:** https://modrinth.com/plugin/treetimber
- **Simple Voice Chat:** https://modrinth.com/plugin/simple-voice-chat
- **Raspberry Pi:** https://www.raspberrypi.com/documentation/
