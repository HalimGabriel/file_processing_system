package com.sistema_processamentoNFiscal.notas;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(AbstractIntegrationTest.AwsTestConfig.class)
public abstract class AbstractIntegrationTest {

    static final LocalStackContainer localstack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(
                            LocalStackContainer.Service.S3,
                            LocalStackContainer.Service.SQS,
                            LocalStackContainer.Service.DYNAMODB);

    static final GenericContainer<?> oauthServer =
            new GenericContainer<>(DockerImageName.parse("ghcr.io/navikt/mock-oauth2-server:2.1.10"))
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/default/.well-known/openid-configuration").forPort(8080));

    // Singleton: sobe uma vez para todas as classes e cria os recursos ANTES do contexto Spring
    static {
        localstack.start();
        oauthServer.start();
        criarRecursos();
    }

    private static void criarRecursos() {
        try {
            localstack.execInContainer("awslocal", "s3", "mb", "s3://notas-bucket");
            localstack.execInContainer("awslocal", "sqs", "create-queue", "--queue-name", "notas-dlq");
            localstack.execInContainer("awslocal", "sqs", "create-queue", "--queue-name", "notas-queue",
                    "--attributes",
                    "{\"VisibilityTimeout\":\"1\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":"
                            + "\\\"arn:aws:sqs:us-east-1:000000000000:notas-dlq\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}");
            localstack.execInContainer("awslocal", "dynamodb", "create-table",
                    "--table-name", "notas",
                    "--attribute-definitions", "AttributeName=PK,AttributeType=S", "AttributeName=SK,AttributeType=S",
                    "--key-schema", "AttributeName=PK,KeyType=HASH", "AttributeName=SK,KeyType=RANGE",
                    "--billing-mode", "PAY_PER_REQUEST");
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao criar recursos no LocalStack", e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.cloud.aws.endpoint", () -> localstack.getEndpoint().toString());
        r.add("spring.cloud.aws.region.static", localstack::getRegion);
        r.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
        r.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
        r.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () ->
                "http://" + oauthServer.getHost() + ":" + oauthServer.getMappedPort(8080) + "/default");
    }

    @Autowired
    private DynamoDbClient dynamoBase;

    // Roda antes do @BeforeEach das subclasses: todo teste começa com a tabela vazia
    @BeforeEach
    void limparTabela() {
        for (var item : dynamoBase.scan(r -> r.tableName("notas")).items()) {
            dynamoBase.deleteItem(r -> r.tableName("notas")
                    .key(java.util.Map.of("PK", item.get("PK"), "SK", item.get("SK"))));
        }
    }

    @TestConfiguration
    static class AwsTestConfig {
        private static StaticCredentialsProvider creds() {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
        }

        @Bean
        S3Client s3Client() {
            return S3Client.builder().endpointOverride(localstack.getEndpoint())
                    .region(Region.of(localstack.getRegion())).credentialsProvider(creds())
                    .forcePathStyle(true).build();
        }

        @Bean
        SqsClient sqsClient() {
            return SqsClient.builder().endpointOverride(localstack.getEndpoint())
                    .region(Region.of(localstack.getRegion())).credentialsProvider(creds()).build();
        }

        @Bean
        DynamoDbClient dynamoDbClient() {
            return DynamoDbClient.builder().endpointOverride(localstack.getEndpoint())
                    .region(Region.of(localstack.getRegion())).credentialsProvider(creds()).build();
        }
    }
}