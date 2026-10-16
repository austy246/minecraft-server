# Záloha světa

## Automatické zálohy

Zálohy běží automaticky každý den v 19:00 a uchovávají se 30 dní zpětně.

**Soubory:**

- Skript: `~/minecraft-server/backup.sh`
- Zálohy: `~/minecraft-backups/`
- Log: `~/minecraft-server/backup.log`

## Reset světa (vytvoření nového)

```bash
# Zastav server
screen -S minecraft -p 0 -X stuff "stop$(printf '\r')"
sleep 10

# Přesuň starý svět do archivu
mkdir -p ~/minecraft-archive/world-$(date +%Y-%m-%d)
mv ~/minecraft-server/world ~/minecraft-server/world_nether ~/minecraft-server/world_the_end ~/minecraft-archive/world-$(date +%Y-%m-%d)/

# Spusť server (vygeneruje nový svět)
~/minecraft-server/start.sh
```

## Obnovení ze zálohy

```bash
# Zastav server
screen -r minecraft   # pak napiš: stop

# Obnov zálohu
cd ~/minecraft-server
rm -rf world world_nether world_the_end
tar -xzf ~/minecraft-backups/world-YYYY-MM-DD_HH-MM.tar.gz

# Spusť server
~/minecraft-server/start.sh
```
