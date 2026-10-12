package com.sistema_processamentoNFiscal.notas.processamento.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NotaFiscal(
        String chaveAcesso,
        String emitenteCnpj,
        BigDecimal valorTotal,
        LocalDate dataEmissao
) {}