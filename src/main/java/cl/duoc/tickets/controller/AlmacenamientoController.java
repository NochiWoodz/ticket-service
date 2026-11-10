package cl.duoc.tickets.controller;

import cl.duoc.tickets.service.EfsStorageService;
import cl.duoc.tickets.service.RutaArchivoUtil;
import cl.duoc.tickets.service.S3StorageService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Endpoints de inspeccion para evidenciar la organizacion de archivos en EFS y S3. */
@RestController
@RequestMapping("/api/almacenamiento")
public class AlmacenamientoController {

    private final EfsStorageService efs;
    private final S3StorageService s3;

    public AlmacenamientoController(EfsStorageService efs, S3StorageService s3) {
        this.efs = efs;
        this.s3 = s3;
    }

    @GetMapping("/efs/estado")
    public Map<String, Object> estadoEfs() { return efs.estado(); }

    @GetMapping("/efs/archivos")
    public Map<String, Object> archivosEfs(@RequestParam(required = false) String eventoCodigo,
                                           @RequestParam(required = false) String usuarioId) {
        List<Map<String, Object>> archivos = efs.listar(eventoCodigo, usuarioId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", archivos.size());
        r.put("archivos", archivos);
        return r;
    }

    @GetMapping("/s3/archivos")
    public Map<String, Object> archivosS3(@RequestParam(required = false) String eventoCodigo,
                                          @RequestParam(required = false) String usuarioId) {
        String prefijo = RutaArchivoUtil.prefijo(eventoCodigo, usuarioId);
        List<Map<String, Object>> objetos = s3.listar(prefijo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bucket", s3.getBucket());
        r.put("prefijo", prefijo);
        r.put("total", objetos.size());
        r.put("objetos", objetos);
        return r;
    }
}
