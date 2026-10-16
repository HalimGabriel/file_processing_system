package com.sistema_processamentoNFiscal.notas.ingestao.web;

import com.sistema_processamentoNFiscal.notas.ingestao.service.UploadService;
import com.sistema_processamentoNFiscal.notas.ingestao.service.UploadUrlResponse;
import com.sistema_processamentoNFiscal.notas.shared.security.TenantResolver;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notas")
public class UploadController {

    private final UploadService service;
    private final TenantResolver tenantResolver;

    public UploadController(UploadService service, TenantResolver tenantResolver) {
        this.service = service;
        this.tenantResolver = tenantResolver;
    }

    @PostMapping("/upload-url")
    public UploadUrlResponse uploadUrl() {
        return service.gerarUrl(tenantResolver.tenantAtual());
    }
}