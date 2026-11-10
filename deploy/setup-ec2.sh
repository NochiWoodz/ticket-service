#!/bin/bash
# =============================================================================
# Preparacion de la instancia EC2 (Amazon Linux 2023) - ejecutar UNA vez por SSH:
#   chmod +x setup-ec2.sh && ./setup-ec2.sh fs-0123456789abcdef0
# Instala Docker y amazon-efs-utils, monta el EFS en /mnt/efs de forma persistente
# y deja el directorio de la app con dueno UID 1000 (usuario del contenedor).
# =============================================================================
set -euo pipefail

EFS_ID="${1:?Uso: ./setup-ec2.sh <EFS_FILE_SYSTEM_ID>}"
MOUNT_POINT=/mnt/efs

echo ">> Instalando Docker y amazon-efs-utils"
sudo dnf update -y
sudo dnf install -y docker amazon-efs-utils
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user

echo ">> Montando EFS $EFS_ID en $MOUNT_POINT (TLS en transito)"
sudo mkdir -p "$MOUNT_POINT"
if ! mountpoint -q "$MOUNT_POINT"; then
  sudo mount -t efs -o tls "$EFS_ID":/ "$MOUNT_POINT"
fi

# Montaje persistente ante reinicios
if ! grep -q "$EFS_ID" /etc/fstab; then
  echo "$EFS_ID:/ $MOUNT_POINT efs _netdev,tls 0 0" | sudo tee -a /etc/fstab
fi

echo ">> Directorio de la aplicacion en el EFS"
sudo mkdir -p "$MOUNT_POINT/ticket-service"
sudo chown 1000:1000 "$MOUNT_POINT/ticket-service"

df -hT "$MOUNT_POINT"
echo ">> Listo. Cierre la sesion SSH y vuelva a entrar para usar docker sin sudo."
