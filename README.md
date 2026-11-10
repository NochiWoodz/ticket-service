# ticket-service — Sistema de Gestión de Reservas de Eventos con Tickets

Microservicio Spring Boot (Java 17) para **CDY2204 Desarrollo Cloud Native – Semana 3**.

Genera tickets personalizados en PDF, los almacena temporalmente en **Amazon EFS**, los sube a **Amazon S3**
organizados por evento y usuario (`evento123/usuarioX/ticket001.pdf`) y se despliega automáticamente en
**EC2** con **GitHub Actions** + **Docker Hub** en cada push a `main`.

```
Push a main ──► GitHub Actions ──► mvn verify ──► docker build/push (Docker Hub) ──► SSH a EC2 ──► docker run
                                                                                          │
                              Cliente (Postman) ──HTTP:8080──► contenedor ticket-service ─┤
                                                                   │ /data/efs  ◄── EFS (NFS, /mnt/efs/ticket-service)
                                                                   └── AWS SDK v2 ──► S3 (bucket por evento/usuario)
```

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/tickets?subirS3=true` | Generar ticket (PDF en EFS + subida automática a S3) |
| POST | `/api/tickets/{id}/subir` | Subir / re-subir un ticket a S3 |
| POST | `/api/tickets/subir-pendientes` | Subir todos los tickets que aún no están en S3 |
| GET | `/api/tickets/{id}/descargar` | Descargar un ticket (PDF) desde S3, con verificación SHA-256 |
| GET | `/api/tickets/descargar?eventoCodigo=&usuarioId=` | Descargar los tickets de un evento y/o usuario (ZIP) |
| PUT | `/api/tickets/{id}` | Modificar detalles (regenera el PDF y actualiza el objeto en S3) |
| DELETE | `/api/tickets/{id}` | Eliminar ticket (S3 + EFS + BD; queda en el historial) |
| GET | `/api/tickets?eventoCodigo=&usuarioId=&estado=` | Listar tickets con filtros |
| GET | `/api/historial?eventoCodigo=&usuarioId=&ticketCodigo=&accion=&desde=&hasta=` | Historial de archivos generados |
| GET | `/api/estadisticas/eventos[/{codigo}]` | Estadísticas de ventas y reservas por evento |
| GET | `/api/almacenamiento/efs/estado` | Estado del montaje EFS (tipo de FS, espacio, escritura) |
| GET | `/api/almacenamiento/efs/archivos` | Archivos presentes en EFS |
| GET | `/api/almacenamiento/s3/archivos?eventoCodigo=&usuarioId=` | Objetos en S3 por prefijo |
| GET/POST | `/api/eventos` | Listar / crear eventos |

Eventos precargados: `evento123`, `evento456`, `evento789` (capacidad 3, para probar el control de aforo).

## Variables de entorno

| Variable | Por defecto | Uso |
|---|---|---|
| `EFS_BASE_PATH` | `./efs-local` (`/data/efs` en Docker) | Punto de montaje del EFS dentro del contenedor |
| `S3_BUCKET` | `tickets-eventos-bucket` | Bucket de destino |
| `AWS_REGION` | `us-east-1` | Región |
| `S3_AUTO_UPLOAD` | `true` | Subida automática al generar |
| `EFS_DELETE_AFTER_UPLOAD` | `false` | Borrar el PDF del EFS tras subirlo a S3 (EFS 100% temporal) |
| `S3_ENDPOINT` | vacío | Solo para LocalStack |

Credenciales AWS: se usa la cadena por defecto del SDK. En EC2 se recomienda adjuntar el rol
**LabInstanceProfile** (AWS Academy) y no definir llaves. Opcionalmente, los secrets `AWS_ACCESS_KEY_ID`,
`AWS_SECRET_ACCESS_KEY` y `AWS_SESSION_TOKEN` se inyectan al contenedor.

## Ejecución local

```powershell
mvn test                       # pruebas (S3 simulado con Mockito, no requiere AWS)
mvn spring-boot:run            # requiere credenciales AWS válidas y un bucket real
# o sin AWS, con LocalStack:
docker compose -f docker-compose.local.yml up --build
```

## Secrets de GitHub Actions (Settings → Secrets and variables → Actions)

| Secret | Valor |
|---|---|
| `DOCKERHUB_USERNAME` | Usuario de Docker Hub |
| `DOCKERHUB_TOKEN` | Access Token de Docker Hub (Account Settings → Personal access tokens) |
| `EC2_HOST` | IP pública o DNS de la instancia |
| `EC2_USER` | `ec2-user` |
| `EC2_SSH_KEY` | Contenido completo del archivo `.pem` |
| `S3_BUCKET` | Nombre del bucket |
| `AWS_REGION` | `us-east-1` |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_SESSION_TOKEN` | *Opcionales* (si la EC2 no tiene rol IAM) |

## Aprovisionamiento

- `deploy/aws-provision.sh`: S3, Security Groups, EFS y EC2 con AWS CLI (CloudShell).
- `deploy/setup-ec2.sh <EFS_ID>`: instala Docker y amazon-efs-utils y monta el EFS en `/mnt/efs`.

Pruebas: importar `postman/ticket-service.postman_collection.json` y cambiar `baseUrl` a `http://<IP_EC2>:8080`.
