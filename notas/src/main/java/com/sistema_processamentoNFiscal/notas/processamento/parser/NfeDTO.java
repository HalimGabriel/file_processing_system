package com.sistema_processamentoNFiscal.notas.processamento.parser;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record NfeDTO(
        String chaveAcesso,
        String cnpjEmitente,
        BigDecimal valorTotal,
        OffsetDateTime dataEmissao
) {}