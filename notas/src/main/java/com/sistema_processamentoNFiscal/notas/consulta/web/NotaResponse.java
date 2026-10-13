package com.sistema_processamentoNFiscal.notas.consulta.web;

public record NotaResponse(
        String chaveAcesso,
        String status,
        String emitenteCnpj,
        Double valorTotal,
        String dataEmissao
) {}