#!/bin/bash
# =============================================================================
# Aprovisionamiento de referencia con AWS CLI (ejecutable en AWS CloudShell).
# Alternativa a los pasos por consola descritos en la documentacion.
# Ajuste las variables antes de ejecutar. Region por defecto: us-east-1.
# =============================================================================
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
BUCKET="tickets-eventos-$(date +%s)"        # el nombre debe ser unico a nivel mundial
SG_NAME="ticket-service-sg"
EFS_SG_NAME="ticket-service-efs-sg"
KEY_NAME="ticket-service-key"

VPC_ID=$(aws ec2 describe-vpcs --region "$REGION" --filters Name=isDefault,Values=true \
          --query 'Vpcs[0].VpcId' --output text)
SUBNET_ID=$(aws ec2 describe-subnets --region "$REGION" --filters Name=vpc-id,Values="$VPC_ID" \
          --query 'Subnets[0].SubnetId' --output text)
echo "VPC=$VPC_ID  SUBNET=$SUBNET_ID"

# ---------- S3 ----------
aws s3api create-bucket --bucket "$BUCKET" --region "$REGION"   # en us-east-1 no lleva LocationConstraint
aws s3api put-public-access-block --bucket "$BUCKET" --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-versioning --bucket "$BUCKET" --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption --bucket "$BUCKET" --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
echo "Bucket creado: $BUCKET"

# ---------- Security Groups ----------
SG_ID=$(aws ec2 create-security-group --region "$REGION" --group-name "$SG_NAME" \
        --description "ticket-service EC2" --vpc-id "$VPC_ID" --query GroupId --output text)
aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" --protocol tcp --port 22   --cidr 0.0.0.0/0
aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" --protocol tcp --port 8080 --cidr 0.0.0.0/0

EFS_SG_ID=$(aws ec2 create-security-group --region "$REGION" --group-name "$EFS_SG_NAME" \
        --description "ticket-service EFS (NFS)" --vpc-id "$VPC_ID" --query GroupId --output text)
# NFS (2049) solo desde el SG de la instancia EC2: principio de minimo privilegio
aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$EFS_SG_ID" --protocol tcp --port 2049 \
  --source-group "$SG_ID"

# ---------- EFS ----------
EFS_ID=$(aws efs create-file-system --region "$REGION" --encrypted --performance-mode generalPurpose \
         --throughput-mode bursting --tags Key=Name,Value=ticket-service-efs \
         --query FileSystemId --output text)
echo "Esperando que el EFS quede disponible..."
until [ "$(aws efs describe-file-systems --region "$REGION" --file-system-id "$EFS_ID" \
          --query 'FileSystems[0].LifeCycleState' --output text)" = "available" ]; do sleep 5; done
aws efs create-mount-target --region "$REGION" --file-system-id "$EFS_ID" \
  --subnet-id "$SUBNET_ID" --security-groups "$EFS_SG_ID"

# ---------- EC2 ----------
aws ec2 create-key-pair --region "$REGION" --key-name "$KEY_NAME" --query KeyMaterial --output text > "$KEY_NAME.pem"
chmod 400 "$KEY_NAME.pem"
AMI_ID=$(aws ssm get-parameters --region "$REGION" \
  --names /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query 'Parameters[0].Value' --output text)
INSTANCE_ID=$(aws ec2 run-instances --region "$REGION" --image-id "$AMI_ID" --instance-type t2.micro \
  --key-name "$KEY_NAME" --security-group-ids "$SG_ID" --subnet-id "$SUBNET_ID" \
  --iam-instance-profile Name=LabInstanceProfile \
  --metadata-options HttpTokens=required,HttpPutResponseHopLimit=2 \
  --associate-public-ip-address \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=ticket-service-ec2}]' \
  --query 'Instances[0].InstanceId' --output text)
aws ec2 wait instance-running --region "$REGION" --instance-ids "$INSTANCE_ID"
PUBLIC_IP=$(aws ec2 describe-instances --region "$REGION" --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)

cat <<RESUMEN

================= RESUMEN =================
S3_BUCKET   = $BUCKET
EFS_ID      = $EFS_ID
EC2_HOST    = $PUBLIC_IP   (usuario: ec2-user)
Llave SSH   = $KEY_NAME.pem  (contenido -> secret EC2_SSH_KEY)
Siguiente paso: scp deploy/setup-ec2.sh a la instancia y ejecutar ./setup-ec2.sh $EFS_ID
===========================================
RESUMEN
