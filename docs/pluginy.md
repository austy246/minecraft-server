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

## FPAntiFreeCam (ochrana proti Freecam / X-Ray / ESP)

Freecam je klientský mod, server ho nepozná. Plugin proto hráčům na povrchu neposílá podzemí, takže Freecam, X-Ray ani ESP v hloubce ukáže jen prázdnotu.

- **Verze:** 1.5.2 (`FPAntiFreeCam-1.5.2.jar`, [Modrinth](https://modrinth.com/plugin/fpantifreecam))
- **Vyžaduje:** PacketEvents 2.14.0 (`packetevents-spigot-2.14.0.jar`) – bez něj se plugin nenačte.
- **Config:** `~/minecraft-server/plugins/FPAntiFreeCam/config.yml`
- **Chráněné světy:** jen `world` (Nether a End autor nedoporučuje, dělají vizuální chyby).
- **Chování (upraveno na maximum):** `void-y: 60`, `deep-deactivation-y: 62`, `protection-y: 64` (výchozí bylo 15 / 20 / 64). Když je hráč nad Y 64, jsou skryté bloky na Y 60 a níže (i truhly, spawnery, moby). Pod Y 62 je ochrana vypnutá, podzemí tam kryje Smart Fill (`always-on`). Záloha původního configu: `config.yml.bak`.
- **Detekce modu:** `freecam-detection` je zapnutá, hráče s Freecam modem při připojení kickne. Změny configu: `/fpac reload`.
- Skryje jen podzemí, ne stěny domů na povrchu. Zbavit se Freecamu úplně server technicky nemůže.
- Plugin hlásí novější verzi 1.5.3-c550cc8 (vývojový build), zůstáváme na 1.5.2.

Pro případné další pluginy stejného typu viz Modrinth: [Phantom](https://modrinth.com/plugin/phantom-antixray), [AntiFreecam](https://modrinth.com/plugin/antifreecam-esp). `AntiFreecam-evolved` je Fabric mod (musel by ho mít každý hráč), pro Paper se nehodí.

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
