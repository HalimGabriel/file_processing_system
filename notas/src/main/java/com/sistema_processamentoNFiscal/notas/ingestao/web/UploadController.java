package com.sistema_processamentoNFiscal.notas.ingestao.web;

import com.sistema_processamentoNFiscal.notas.ingestao.service.UploadService;
import com.sistema_processamentoNFiscal.notas.ingestao.service.UploadUrlResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notas")
public class UploadController {

    private final UploadService service;

    public UploadController(UploadService service) {
        this.service = service;
    }

    @PostMapping("/upload-url")
    public UploadUrlResponse uploadUrl() {
        // tenantId fixo por enquanto; vira claim do JWT na etapa de segurança
        return service.gerarUrl("12345678000190");
    }
}