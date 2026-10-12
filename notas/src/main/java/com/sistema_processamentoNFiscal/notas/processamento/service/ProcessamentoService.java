package com.sistema_processamentoNFiscal.notas.processamento.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema_processamentoNFiscal.notas.processamento.model.NotaFiscal; // Ajustado para o seu model real
import com.sistema_processamentoNFiscal.notas.processamento.parser.NfeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

@Service
public class ProcessamentoService {

    private static final Logger log = LoggerFactory.getLogger(ProcessamentoService.class);

    private final S3Client s3;
    private final NfeParser parser;
    private final DynamoDbClient dynamo;
    private final ObjectMapper mapper;
    private final String bucket;
    private final String table;

    public ProcessamentoService(
            S3Client s3,
            NfeParser parser,
            DynamoDbClient dynamo,
            ObjectMapper mapper,
            @Value("${app.bucket}") String bucket,
            @Value("${app.table}") String table) {
        this.s3 = s3;
        this.parser = parser;
        this.dynamo = dynamo;
        this.mapper = mapper;
        this.bucket = bucket;
        this.table = table;
    }

    public void processar(String mensagem) {
        try {
            String rawKey = mapper.readTree(mensagem).at("/Records/0/s3/object/key").asText();
            String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);

            log.info("Baixando arquivo do S3 - Bucket: {}, Key: {}", bucket, key);

            try (var in = s3.getObject(b -> b.bucket(bucket).key(key))) {
                NotaFiscal nota = parser.parse(in); // Mudado de NfeDTO para NotaFiscal
                salvar(nota, key);
            }
        } catch (IOException e) {
            log.error("Falha ao ler o payload da mensagem SQS", e);
            throw new IllegalStateException("Falha ao processar mensagem", e);
        } catch (Exception e) {
            log.error("Erro inesperado ao processar nota fiscal", e);
            throw new RuntimeException("Erro ao processar nota", e);
        }
    }

    private void salvar(NotaFiscal nota, String s3Key) { // Mudado de NfeDTO para NotaFiscal
        String tenant = s3Key.split("/")[1];
        var item = Map.of(
                "PK", AttributeValue.fromS("TENANT#" + tenant),
                "SK", AttributeValue.fromS("NOTA#" + nota.chaveAcesso()),
                "status", AttributeValue.fromS("PROCESSADA"),
                "emitenteCnpj", AttributeValue.fromS(nota.emitenteCnpj()),
                "valorTotal", AttributeValue.fromN(nota.valorTotal().toString()),
                "dataEmissao", AttributeValue.fromS(nota.dataEmissao().toString()),
                "s3Key", AttributeValue.fromS(s3Key),
                "schemaVersion", AttributeValue.fromN("1"),
                "criadoEm", AttributeValue.fromS(Instant.now().toString())
        );

        try {
            dynamo.putItem(r -> r.tableName(table)
                    .item(item)
                    .conditionExpression("attribute_not_exists(SK)"));
            log.info("Nota {} salva com sucesso no DynamoDB para o tenant {}", nota.chaveAcesso(), tenant);
        } catch (ConditionalCheckFailedException e) {
            log.info("Nota {} já processada anteriormente, ignorando (idempotência garantida)", nota.chaveAcesso());
        }
    }
}