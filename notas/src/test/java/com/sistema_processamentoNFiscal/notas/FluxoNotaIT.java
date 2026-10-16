package com.sistema_processamentoNFiscal.notas;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class FluxoNotaIT extends AbstractIntegrationTest {

    @Autowired S3Client s3;
    @Autowired SqsTemplate sqs;
    @Autowired SqsClient sqsClient;
    @Autowired DynamoDbClient dynamo;

    private String evento(String key) {
        return """
            {"Records":[{"s3":{"object":{"key":"%s"}}}]}
            """.formatted(key);
    }

    @Test
    void deveProcessarNotaEGravarNoDynamo() throws Exception {
        String key = "entrada/12345678000190/teste.xml";
        byte[] xml = getClass().getResourceAsStream("/nfe-exemplo.xml").readAllBytes();
        s3.putObject(b -> b.bucket("notas-bucket").key(key), RequestBody.fromBytes(xml));
        sqs.send("notas-queue", evento(key));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var itens = dynamo.scan(r -> r.tableName("notas")).items();
            assertThat(itens).hasSize(1);
            assertThat(itens.get(0).get("PK").s()).isEqualTo("TENANT#12345678000190");
            assertThat(itens.get(0).get("status").s()).isEqualTo("PROCESSADA");
        });
    }

    @Test
    void naoDeveDuplicarNotaReenviada() throws Exception {
        byte[] xml = getClass().getResourceAsStream("/nfe-exemplo.xml").readAllBytes();
        String key1 = "entrada/12345678000190/teste-v1.xml";
        String key2 = "entrada/12345678000190/teste-v2.xml";
        s3.putObject(b -> b.bucket("notas-bucket").key(key1), RequestBody.fromBytes(xml));
        s3.putObject(b -> b.bucket("notas-bucket").key(key2), RequestBody.fromBytes(xml));
        sqs.send("notas-queue", evento(key1));
        sqs.send("notas-queue", evento(key2));

        // .during: o resultado precisa se manter em 1 item por 3s (cobre o processamento da 2ª mensagem)
        await().atMost(Duration.ofSeconds(20)).during(Duration.ofSeconds(3)).untilAsserted(() -> {
            var itens = dynamo.scan(r -> r.tableName("notas")).items();
            assertThat(itens).hasSize(1);
        });
    }

    @Test
    void mesmaNotaEmTenantsDiferentesDeveGerarDoisItens() throws Exception {
        byte[] xml = getClass().getResourceAsStream("/nfe-exemplo.xml").readAllBytes();
        String keyA = "entrada/12345678000190/a.xml";
        String keyB = "entrada/98765432000110/b.xml";
        s3.putObject(b -> b.bucket("notas-bucket").key(keyA), RequestBody.fromBytes(xml));
        s3.putObject(b -> b.bucket("notas-bucket").key(keyB), RequestBody.fromBytes(xml));
        sqs.send("notas-queue", evento(keyA));
        sqs.send("notas-queue", evento(keyB));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(dynamo.scan(r -> r.tableName("notas")).items()).hasSize(2));
    }

    @Test
    void xmlInvalidoNaoGravaNadaEVaiParaDlq() {
        String key = "entrada/12345678000190/invalido.xml";
        s3.putObject(b -> b.bucket("notas-bucket").key(key),
                RequestBody.fromString("<nfeProc><NFe>XML Malformado ou Incompleto</NFe></nfeProc>"));
        sqs.send("notas-queue", evento(key));

        String dlqUrl = sqsClient.getQueueUrl(r -> r.queueName("notas-dlq")).queueUrl();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var msgs = sqsClient.receiveMessage(r -> r.queueUrl(dlqUrl).maxNumberOfMessages(1)).messages();
            assertThat(msgs).isNotEmpty();
        });
        assertThat(dynamo.scan(r -> r.tableName("notas")).items()).isEmpty();
    }
}