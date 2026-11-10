# Guion del video — CDY2204 Semana 3 (Teams, ~10 minutos)

> Antes de grabar: laboratorio AWS iniciado, EC2 encendida, último pipeline en verde, Postman con
> `baseUrl = http://<IP_EC2>:8080`, y estas pestañas abiertas: GitHub (Actions), Docker Hub, consola de
> S3, consola de EFS y una terminal SSH a la EC2.

| Min | Qué mostrar | Qué decir (idea) | Criterio |
|---|---|---|---|
| 0:00–0:45 | Diagrama de arquitectura (doc, sección 3) | Presentación, el caso de la startup y los componentes: EC2, EFS, S3, Docker Hub, GitHub Actions | — |
| 0:45–2:30 | Editar algo visible (ej. un texto del `DataSeeder` o del PDF), hacer `git push` a main y abrir Actions | El push dispara 3 jobs: pruebas, imagen en Docker Hub y deploy por SSH. Mientras corre, mostrar `ci-cd.yml` | 6 |
| 2:30–3:15 | Docker Hub (tag con el SHA nuevo) y en SSH: `docker ps` | La imagen se publica con el SHA del commit y el contenedor se reemplaza en EC2 | 6 |
| 3:15–4:15 | SSH: `df -hT /mnt/efs` · Postman, carpeta **0**: estado del EFS | El EFS está montado como nfs4 y el contenedor lo ve en `/data/efs` | 1 |
| 4:15–5:30 | Postman, carpeta **1**: generar 3 tickets y uno con `subirS3=false`. SSH: `ls -R /mnt/efs/ticket-service/tickets` | El PDF se genera en EFS, se organiza por evento/usuario y se sube solo a S3 | 1, 2 |
| 5:30–6:30 | Carpeta **2**: subir el pendiente y listar S3. **Consola S3**: navegar `evento123/usuarioX/` y abrir los metadatos del objeto | Estructura `/evento/usuario/ticket.pdf`; metadatos con fecha, usuario y versión | 2 |
| 6:30–7:15 | Carpeta **3**: descargar el PDF (*Send and Download*) y abrirlo, y bajar el ZIP por usuario | La descarga viene desde S3 y se verifica con SHA-256 (cabecera `X-Checksum-SHA256`) | 4 |
| 7:15–8:15 | Carpeta **4**: modificar y transferir a usuarioY. **Consola S3**: activar *Show versions* y ver que el objeto se movió a `usuarioY/` | Se regenera el PDF (versión 2), se sobrescribe en S3 y el versionado guarda la versión previa | 3 |
| 8:15–9:00 | Carpeta **5**: historial con los distintos filtros | Cada operación queda registrada; los filtros se combinan | 5 |
| 9:00–9:30 | Carpeta **6** (estadísticas) y **7** (eliminar + historial ELIMINADO) | Ventas, reservas, recaudación y ocupación; al eliminar se borra de S3 y EFS, pero queda trazabilidad | caso |
| 9:30–10:00 | Carpeta **8** (validaciones) y cierre | Seguridad: path traversal bloqueado y control de aforo. Conclusión | — |

Tip: en vez de ir request por request, puedes usar **Run collection** en Postman para mostrar todos los tests
en verde al final, pero en el video conviene mostrar las respuestas una a una.
