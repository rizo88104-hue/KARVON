# Server setup and hardening (Ubuntu 24.04)

One server runs the whole E-Market stack with Docker Compose (see [deploy.md](deploy.md)).
Recommended size: 4 vCPU, 8 GB RAM, 80 GB SSD (2 vCPU / 4 GB works for the start). Commands run as root
until the `deploy` user exists, then as `deploy` with `sudo`.

## Checklist

| # | Item | Done when |
|---|------|-----------|
| 1 | Non-root `deploy` user with sudo | `ssh deploy@server` works |
| 2 | SSH keys only, no root login | `ssh root@server` and password logins are refused |
| 3 | Firewall: only 22, 80, 443 | `sudo ufw status` shows exactly these |
| 4 | fail2ban for SSH | `sudo fail2ban-client status sshd` shows the jail |
| 5 | Automatic security updates | `unattended-upgrades` enabled, reboot window set |
| 6 | Docker Engine + compose plugin | `docker compose version` |
| 7 | Time zone and NTP | `timedatectl` shows Asia/Tashkent, NTP active |
| 8 | Directories for the stack and backups | `/opt/emarket`, `/var/backups/emarket` owned by `deploy` |
| 9 | Disk space alert in cron | test message arrives in Telegram |

## 1. Non-root deploy user

```bash
adduser --disabled-password --gecos "" deploy
usermod -aG sudo deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
# Paste YOUR public key (from your laptop: cat ~/.ssh/id_ed25519.pub):
nano /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys && chmod 600 /home/deploy/.ssh/authorized_keys
passwd deploy        # a strong password, only for sudo (SSH will not accept passwords)
```

A second key for GitHub Actions (deploys) — create it on your laptop, never reuse your own key:

```bash
ssh-keygen -t ed25519 -C "github-actions-deploy" -f emarket_deploy -N ""
# emarket_deploy.pub  -> append to /home/deploy/.ssh/authorized_keys on the server
# emarket_deploy      -> GitHub secret DEPLOY_SSH_KEY (both repositories), then delete the local copy
```

## 2. SSH: keys only

Keep your current SSH session open until a new login works.

```bash
cat > /etc/ssh/sshd_config.d/99-hardening.conf <<'EOF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
AllowUsers deploy
MaxAuthTries 3
X11Forwarding no
EOF
sshd -t && systemctl reload ssh
```

Test from your laptop in a new terminal: `ssh deploy@SERVER_IP` (works) and `ssh root@SERVER_IP` (refused).

## 3. Firewall (ufw)

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status verbose
```

**Docker and ufw:** ports published by Docker bypass ufw. That is why only the `nginx` service has `ports:` in
`docker-compose.prod.yml`. Never add `ports:` to postgres, redis, minio, api or web — they would be public.

## 4. fail2ban

```bash
sudo apt-get update && sudo apt-get install -y fail2ban
sudo tee /etc/fail2ban/jail.local >/dev/null <<'EOF'
[DEFAULT]
bantime = 1h
findtime = 10m
maxretry = 5
backend = systemd

[sshd]
enabled = true
EOF
sudo systemctl enable --now fail2ban
sudo fail2ban-client status sshd
```

## 5. Automatic security updates

```bash
sudo apt-get install -y unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades      # answer "Yes"
sudo tee /etc/apt/apt.conf.d/52emarket-upgrades >/dev/null <<'EOF'
Unattended-Upgrade::Automatic-Reboot "true";
Unattended-Upgrade::Automatic-Reboot-Time "05:00";
Unattended-Upgrade::Remove-Unused-Dependencies "true";
EOF
sudo unattended-upgrade --dry-run --debug | tail -5
```

All containers have `restart: unless-stopped`, so the shop comes back after a reboot.

## 6. Docker

Official repository (not the Ubuntu `docker.io` package):

```bash
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker deploy      # log out and in again; note: the docker group is root-equivalent
docker compose version
```

## 7. Time zone

```bash
sudo timedatectl set-timezone Asia/Tashkent
timedatectl     # "System clock synchronized: yes"
```

## 8. Directories

```bash
sudo install -d -o deploy -g deploy -m 750 /opt/emarket
sudo install -d -o deploy -g deploy -m 750 /var/backups/emarket
```

`/opt/emarket` gets `docker-compose.prod.yml`, `deploy/` and `.env.prod`; `/var/backups/emarket` is the
`BACKUP_DIR` (the second backup location besides MinIO). For a copy off the server set `BACKUP_OFFSITE_REMOTE`.

## 9. Disk space alert

`deploy/scripts/disk-alert.sh` sends a Telegram message when a disk is fuller than `DISK_ALERT_PERCENT` (85 %).
It reads `ALERT_TELEGRAM_BOT_TOKEN` and `ALERT_TELEGRAM_CHAT_ID` from `.env.prod`. Use a separate bot (or the
shop bot) and the chat id of the staff group (send a message to the bot, then open
`https://api.telegram.org/bot<TOKEN>/getUpdates` to find `chat.id`).

```bash
sudo apt-get install -y curl
chmod +x /opt/emarket/deploy/scripts/disk-alert.sh
DISK_ALERT_PERCENT=1 /opt/emarket/deploy/scripts/disk-alert.sh   # test: a message must arrive
( crontab -l 2>/dev/null; echo "*/15 * * * * /opt/emarket/deploy/scripts/disk-alert.sh >/dev/null 2>&1" ) | crontab -
```
