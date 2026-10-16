package com.sistema_processamentoNFiscal.notas.consulta.web;

import com.sistema_processamentoNFiscal.notas.consulta.service.ConsultaService;
import com.sistema_processamentoNFiscal.notas.shared.security.TenantResolver;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/notas")
public class ConsultaController {

    private final ConsultaService service;
    private final TenantResolver tenantResolver;

    public ConsultaController(ConsultaService service, TenantResolver tenantResolver) {
        this.service = service;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping("/{chave}")
    public ResponseEntity<NotaResponse> buscar(
            @PathVariable @Pattern(regexp = "\\d{44}", message = "A chave de acesso deve conter exatamente 44 dígitos numéricos") String chave) {

        // Obtém o tenant dinamicamente do token JWT autenticado
        String tenantId = tenantResolver.tenantAtual();

        return service.buscar(tenantId, chave)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}