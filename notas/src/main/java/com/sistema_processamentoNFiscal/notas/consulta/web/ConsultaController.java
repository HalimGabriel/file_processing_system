package com.sistema_processamentoNFiscal.notas.consulta.web;

import com.sistema_processamentoNFiscal.notas.consulta.service.ConsultaService;
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

    public ConsultaController(ConsultaService service) {
        this.service = service;
    }

    @GetMapping("/{chave}")
    public ResponseEntity<NotaResponse> buscar(
            @PathVariable @Pattern(regexp = "\\d{44}", message = "A chave de acesso deve conter exatamente 44 dígitos numéricos") String chave) {

        // Tenant fixo por enquanto conforme estipulado
        return service.buscar("12345678000190", chave)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}