package com.sistema_processamentoNFiscal.notas.processamento.listener;

import com.sistema_processamentoNFiscal.notas.processamento.service.ProcessamentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import io.awspring.cloud.sqs.annotation.SqsListener;

@Component
public class NotaListener {

    private static final Logger log = LoggerFactory.getLogger(NotaListener.class);
    private final ProcessamentoService service;

    public NotaListener(ProcessamentoService service) {
        this.service = service;
    }

    @SqsListener("${app.queue}")
    public void receber(String mensagem) {
        log.info("Mensagem recebida da fila: {}", mensagem);
        service.processar(mensagem);
    }
}