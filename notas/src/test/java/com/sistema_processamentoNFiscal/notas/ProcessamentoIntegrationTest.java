package com.sistema_processamentoNFiscal.notas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ProcessamentoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private S3Client s3Client;

    @Autowired
    private SqsClient sqsClient;

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Value("${app.bucket}")
    private String bucketName;

    @Value("${app.queue}")
    private String queueName;

    @Test
    void deveProcessarNotaFiscalComSucessoDoS3ParaDynamoDB() {
        // Simula o upload do arquivo XML no S3 do LocalStack
        String s3Key = "entrada/12345678000190/teste-nota.xml";
        String xmlContent = """
            <nfeProc xmlns="http://www.portalfiscal.inf.br/nfe">
                <NFe>
                    <infNFe Id="NFe35261000000000000000550010000000011000000014">
                        <ide>
                            <dhEmi>2026-10-08T10:00:00-03:00</dhEmi>
                        </ide>
                        <emit>
                            <CNPJ>00000000000191</CNPJ>
                        </emit>
                        <total>
                            <ICMSTot>
                                <vNF>150.00</vNF>
                            </ICMSTot>
                        </total>
                    </infNFe>
                </NFe>
            </nfeProc>
            """;

        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucketName).key(s3Key).build(),
                software.amazon.awssdk.core.sync.RequestBody.fromString(xmlContent, StandardCharsets.UTF_8)
        );

        // Descobre a URL da fila SQS no LocalStack e envia a mensagem simulando o evento do S3
        String queueUrl = sqsClient.getQueueUrl(r -> r.queueName(queueName)).queueUrl();

        String sqsMessage = String.format("""
            {
              "Records": [
                {
                  "s3": {
                    "bucket": { "name": "%s" },
                    "object": { "key": "%s" }
                  }
                }
              ]
            }
            """, bucketName, s3Key);

        sqsClient.sendMessage(r -> r.queueUrl(queueUrl).messageBody(sqsMessage));

        // Usa o Awaitility para aguardar o listener processar a mensagem e salvar no DynamoDB de forma assíncrona
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            var getItemResponse = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName("notas")
                    .key(java.util.Map.of(
                            "PK", software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS("TENANT#12345678000190"),
                            "SK", software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS("NOTA#35261000000000000000550010000000011000000014")
                    ))
                    .build());

            assertThat(getItemResponse.hasItem()).isTrue();
            assertThat(getItemResponse.item().get("status").s()).isEqualTo("PROCESSADA");
        });
    }
}