# Řešení problémů

## Server neběží

1. Zkontroluj: `screen -r minecraft`
2. Spusť ručně: `~/minecraft-server/start.sh`

## Playit nefunguje

1. Zkontroluj: `screen -r playit`
2. Spusť ručně: `screen -S playit -d -m /usr/local/bin/playit`

## Skin se nezobrazuje

1. Hráč musí skin nastavit přes `/skin set <jméno>` (offline režim nenačítá skiny automaticky).
2. Ověř, že SkinsRestorer běží: v logu hledej `Enabling SkinsRestorer`.

## Obnovení Bedrock/Geyser

Pokud bys ho chtěl zpět:

1. Vrať jar a složku: `mv ~/minecraft-disabled-geyser/Geyser-Spigot.jar ~/minecraft-disabled-geyser/Geyser-Spigot ~/minecraft-server/plugins/`
2. Nastav `online-mode=false` (už je) a v Geyser configu `auth-type: offline`.
3. Odkomentuj healthcheck řádek v `crontab -e`.
4. V Playit dashboardu znovu povol (enable) Bedrock tunel.
5. Restartuj server: `~/minecraft-server/restart.sh`.
