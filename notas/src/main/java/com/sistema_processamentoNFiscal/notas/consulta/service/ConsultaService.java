package com.sistema_processamentoNFiscal.notas.consulta.service;

import com.sistema_processamentoNFiscal.notas.consulta.web.NotaResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.Optional;

@Service
public class ConsultaService {

    private final DynamoDbClient dynamo;
    private final String tableName;

    public ConsultaService(DynamoDbClient dynamo, @Value("${aws.dynamodb.table:notas}") String tableName) {
        this.dynamo = dynamo;
        this.tableName = tableName;
    }

    public Optional<NotaResponse> buscar(String tenantId, String chave) {
        var resp = dynamo.getItem(r -> r.tableName(tableName).key(Map.of(
                "PK", AttributeValue.fromS("TENANT#" + tenantId),
                "SK", AttributeValue.fromS("NOTA#" + chave))));

        if (!resp.hasItem()) {
            return Optional.empty();
        }

        return Optional.of(toResponse(resp.item()));
    }

    private NotaResponse toResponse(Map<String, AttributeValue> item) {
        return new NotaResponse(
                item.containsKey("SK") ? item.get("SK").s().replace("NOTA#", "") : null,
                item.containsKey("status") ? item.get("status").s() : null,
                item.containsKey("emitenteCnpj") ? item.get("emitenteCnpj").s() : null,
                item.containsKey("valorTotal") ? Double.parseDouble(item.get("valorTotal").n()) : 0.0,
                item.containsKey("dataEmissao") ? item.get("dataEmissao").s() : null
        );
    }
}