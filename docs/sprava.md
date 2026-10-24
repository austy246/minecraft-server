# Správa serveru

## Struktura souborů

```
/home/austy/
├── minecraft-server/
│   ├── paper.jar
│   ├── start.sh
│   ├── restart.sh
│   ├── backup.sh
│   ├── healthcheck.sh               # nepoužívá se (viz crontab)
│   ├── paper-old.jar                # předchozí verze Paperu
│   ├── plugins/
│   │   ├── FPAntiFreeCam-1.5.2.jar
│   │   ├── packetevents-spigot-2.14.0.jar
│   │   ├── SimpleShops.jar
│   │   ├── Teams.jar
│   │   ├── SkinsRestorer.jar
│   │   ├── timber-1.8.3.jar
│   │   ├── voicechat-bukkit-2.6.17.jar
│   │   └── voicechat/
│   │       └── voicechat-server.properties
│   ├── world/
│   ├── world_nether/
│   └── world_the_end/
├── minecraft-backups/
├── minecraft-archive/
├── minecraft-disabled-geyser/   # odstavený Geyser plugin (záloha, lze vrátit)
├── playit/                      # Playit agent (binárka v /opt/playit/agent)
└── simpleshops/                 # zdrojový kód SimpleShops pluginu
```

## Důležité příkazy

### Správa serveru

| Akce | Příkaz |
| :---- | :---- |
| Spustit server | `~/minecraft-server/start.sh` |
| Restartovat server | `~/minecraft-server/restart.sh` |
| Připojit ke konzoli | `screen -r minecraft` |
| Odpojit od konzole | `Ctrl+A`, pak `D` |
| Spustit Playit tunel | `screen -S playit -d -m /usr/local/bin/playit` |
| Ruční záloha | `~/minecraft-server/backup.sh` |

### Minecraft konzole

| Příkaz | Co dělá |
| :---- | :---- |
| `stop` | Bezpečně vypne server |
| `op <jméno>` | Dá hráči admin práva |
| `whitelist on` | Zapne whitelist |
| `whitelist add <jméno>` | Přidá hráče na whitelist |
| `tps` | Zobrazí výkon serveru |

## Autostart (crontab)

Aktuální konfigurace (`crontab -e`):

```
@reboot sleep 60 && /home/austy/minecraft-server/start.sh
@reboot sleep 30 && screen -S playit -d -m /usr/local/bin/playit
0 19 * * * /home/austy/minecraft-server/backup.sh >> /home/austy/minecraft-server/backup.log 2>&1
#DISABLED_GEYSER */10 * * * * ~/minecraft-server/healthcheck.sh >> ~/minecraft-server/healthcheck.log 2>&1
```

| Úloha | Kdy | Co dělá |
| ----- | ----- | ----- |
| Minecraft start | Po startu Pi (60 s) | Spustí server |
| Playit start | Po startu Pi (30 s) | Spustí tunel |
| Záloha | Denně 19:00 | Zálohuje svět |
| Healthcheck | (zakázán) | Původně kontroloval Geyser — po odstranění Geyseru zakomentován |

> Healthcheck skript kontroloval `Started Geyser` a při chybějícím Geyseru restartoval server.
> Protože Geyser byl odstraněn, je tento cron řádek zakomentován, aby nezpůsoboval restartovou smyčku.
