# Připojení k serveru

## Java Edition (PC/Mac/Linux)

| Adresa | kitchen-bases.gl.joinmc.link |
| :---- | :---- |
| **Port** | 25565 (výchozí) |

## Bedrock Edition

**Bedrock podpora byla odstraněna** (plugin Geyser odstaven). Připojit se lze pouze přes **Java Edition**.
Bedrock klienti (Windows 10/11, Xbox, PlayStation, Switch, mobil) se už nepřipojí.

Postup obnovení viz [Řešení problémů](reseni-problemu.md#obnovení-bedrockgeyser).

## Lokální připojení (ve stejné síti)

- Java: `10.0.0.88:25565`

## Účty a skiny (online-mode=false + SkinsRestorer)

Server běží v režimu `online-mode=false`, takže se připojí jak originální, tak non-premium (cracked) hráči.
V offline režimu se originální skiny nenačtou automaticky — řeší to plugin **SkinsRestorer**.

Každý hráč si skin nastaví příkazem (stačí jednou, pamatuje se):

| Příkaz | Co dělá |
| :---- | :---- |
| `/skin set <jméno>` | Nastaví skin libovolného Mojang účtu (např. `/skin set Notch`) |
| `/skin url <odkaz.png>` | Skin z přímého odkazu na PNG |
| `/skin clear` | Vrátí výchozí skin |
| `/skin update` | Obnoví skin |
