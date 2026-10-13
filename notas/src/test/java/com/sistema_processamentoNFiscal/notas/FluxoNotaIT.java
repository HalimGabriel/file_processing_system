package com.sistema_processamentoNFiscal.notas;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class FluxoNotaIT extends AbstractIntegrationTest {

    @Autowired
    private S3Client s3;

    @Autowired
    private SqsTemplate sqs;

    @Autowired
    private DynamoDbClient dynamo;

    // Limpa a tabela antes de cada teste para garantir isolamento total
    @BeforeEach
    void limparTabela() {
        var itens = dynamo.scan(ScanRequest.builder().tableName("notas").build()).items();
        for (var item : itens) {
            dynamo.deleteItem(DeleteItemRequest.builder()
                    .tableName("notas")
                    .key(java.util.Map.of(
                            "PK", item.get("PK"),
                            "SK", item.get("SK")
                    ))
                    .build());
        }
    }

    @Test
    void deveProcessarNotaEGravarNoDynamo() throws Exception {
        String key = "entrada/12345678000190/teste.xml";
        byte[] xml = getClass().getResourceAsStream("/nfe-exemplo.xml").readAllBytes();

        s3.putObject(b -> b.bucket("notas-bucket").key(key), RequestBody.fromBytes(xml));

        String evento = """
            {"Records":[{"s3":{"object":{"key":"%s"}}}]}
            """.formatted(key);

        sqs.send("notas-queue", evento);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var itens = dynamo.scan(r -> r.tableName("notas")).items();
            assertThat(itens).hasSize(1);
            assertThat(itens.get(0).get("status").s()).isEqualTo("PROCESSADA");
        });
    }

    @Test
    void naoDeveDuplicarNotaReenviada() throws Exception {
        byte[] xml = getClass().getResourceAsStream("/nfe-exemplo.xml").readAllBytes();

        String key1 = "entrada/12345678000190/teste-v1.xml";
        s3.putObject(b -> b.bucket("notas-bucket").key(key1), RequestBody.fromBytes(xml));
        sqs.send("notas-queue", """
            {"Records":[{"s3":{"object":{"key":"%s"}}}]}
            """.formatted(key1));

        String key2 = "entrada/12345678000190/teste-v2.xml";
        s3.putObject(b -> b.bucket("notas-bucket").key(key2), RequestBody.fromBytes(xml));
        sqs.send("notas-queue", """
            {"Records":[{"s3":{"object":{"key":"%s"}}}]}
            """.formatted(key2));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var itens = dynamo.scan(r -> r.tableName("notas")).items();
            assertThat(itens).hasSize(1);
            assertThat(itens.get(0).get("status").s()).isEqualTo("PROCESSADA");
        });
    }

    @Test
    void deveLidarComXmlInvalidoSemQuebrarFluxo() throws Exception {
        String key = "entrada/12345678000190/invalido.xml";
        String xmlInvalido = "<nfeProc><NFe>XML Malformado ou Incompleto</NFe></nfeProc>";

        s3.putObject(b -> b.bucket("notas-bucket").key(key), RequestBody.fromString(xmlInvalido));

        String evento = """
            {"Records":[{"s3":{"object":{"key":"%s"}}}]}
            """.formatted(key);

        sqs.send("notas-queue", evento);

        // Como o serviço grava notas inválidas como DESCONHECIDA, validamos que o fluxo tratou sem estourar exceção fatal na fila
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var itens = dynamo.scan(r -> r.tableName("notas")).items();
            assertThat(itens).hasSize(1);
            assertThat(itens.get(0).get("SK").s()).isEqualTo("NOTA#DESCONHECIDA");
        });
    }
}