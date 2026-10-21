# Pluginy

## SkinsRestorer (vlastní skiny v offline režimu)

Plugin umožňující hráčům nastavit si skin i na serveru s `online-mode=false`.

- **Verze:** 15.12.5
- **Soubor:** `~/minecraft-server/plugins/SkinsRestorer.jar`
- **Config:** `~/minecraft-server/plugins/SkinsRestorer/`
- Použití viz [Připojení a účty](pripojeni.md#účty-a-skiny-online-modefalse--skinsrestorer) (`/skin set`, `/skin url`, ...).

## TreeTimber (kácení celého stromu)

Plugin, který skácí celý strom po rozbití kmene sekerou.

- **Verze:** 1.8.3 (jar: `timber-1.8.3.jar`)
- **Config:** `~/minecraft-server/plugins/Timber/config.yml`
- V configu lze nastavit chování (sneak, opotřebení sekery, povolené druhy dřeva, max. počet bloků).

## SimpleShops (vlastní plugin)

Custom plugin (verze 1.0.0) pro výměnu itemů mezi hráči bez peněz. Inspirovaný Forge modem Simple Shops.

### Crafting

Vyrob **Shop Block** v craftění (barel + 4 smaragdy):

```
. E .
E B E   → 1x Shop Block
. E .
```

(E = Emerald, B = Barrel)

Obyčejné barely fungují normálně — jen Shop Block se chová jako obchod.

### Použití

**Majitel (kdo Shop Block postavil):**

| Akce | Co se stane |
| :---- | :---- |
| Pravý klik s itemem + sneak | Nastav CENU |
| Pravý klik s itemem | Vlož ZBOŽÍ do skladu |
| Levý klik | Vyber zisky |
| Sneak + levý klik | Zruš shop (vrátí vše + Shop Block) |

**Kupující:**

| Akce | Co se stane |
| :---- | :---- |
| Pravý klik s cenou v ruce | Nákup |
| Levý klik | Info o shopu |

### Příkazy

```
/ss        - nápověda
/ss give   - dej si Shop Block (jen admin)
```

### Zdrojový kód a build

- **Zdrojový kód:** `~/simpleshops/`
- **Build:** `cd ~/simpleshops && ./build.sh`
- **Instalace:** `cp target/SimpleShops.jar ~/minecraft-server/plugins/`

## MaceLimiter (vlastní plugin)

Custom plugin (verze 1.0.0) upravující Mace (palici).

- **Bez enchantů:** enchanty existují dál, ale nelze je dát na Mace (kovadlina, enchant stůl). Stávající enchanty se z Mace odstraní (při připojení, otevření inventáře/truhly, sebrání itemu a každé 2 s v inventáři a enderchestu).
- **Nabíjení 60 s:** po každém zásahu Mace má hráč 60 s cooldown (v ručce se zobrazí overlay), během kterého Mace nezraňuje.
- **Zdrojový kód:** `maceplugin/` v tomto repozitáři.
- **Build:** `cd maceplugin && mvn package` (verze Paper API je v `pom.xml` jako `paper.api.version`, nastav podle serveru)
- **Instalace:** `cp target/MaceLimiter.jar ~/minecraft-server/plugins/` a restart serveru.

## Simple Voice Chat

Proximity voice chat pro Java hráče (plugin verze 2.6.17, `voicechat-bukkit-2.6.17.jar`). Hráči slyší ostatní podle vzdálenosti ve hře.

### Připojení

| Položka | Hodnota |
| :---- | :---- |
| Voice Chat adresa | bay-inquiry.gl.at.ply.gg |
| Port | 46128 (UDP) |
| Lokální port | 24454 |

### Požadavky pro hráče (Java Edition)

1. **Nainstalovat Fabric Loader:** https://fabricmc.net/use/installer/
2. **Stáhnout mod:** https://modrinth.com/mod/simple-voice-chat
3. **Vložit .jar soubor do složky mods:**
   - Windows: `%appdata%\.minecraft\mods\`
   - Mac: `~/Library/Application Support/minecraft/mods/`
4. **Spustit Minecraft s Fabric profilem**

### Konfigurace serveru

**Soubor:** `~/minecraft-server/plugins/voicechat/voicechat-server.properties`

**Důležitá nastavení:**

- `voice_host=bay-inquiry.gl.at.ply.gg:46128`
- `port=24454`

### Ovládání (pro hráče)

| Klávesa | Funkce |
| :---- | :---- |
| V | Otevře voice chat menu |
| Caps Lock | Push-to-talk (výchozí) |

## Poznámky ke stavu na serveru

- Ve `plugins/` zůstala složka `TradeShop/` (config a data z května), ale plugin (jar) nainstalovaný není — je to pozůstatek.
- Složky `bStats/`, `spark/` a `update/` generují Paper/pluginy.
