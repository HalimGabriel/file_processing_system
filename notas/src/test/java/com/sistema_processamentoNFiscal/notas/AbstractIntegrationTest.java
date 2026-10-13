package com.sistema_processamentoNFiscal.notas;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AbstractIntegrationTest.AwsTestConfig.class)
public abstract class AbstractIntegrationTest {

    @Container
    static LocalStackContainer localstack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(
                            LocalStackContainer.Service.S3,
                            LocalStackContainer.Service.SQS,
                            LocalStackContainer.Service.DYNAMODB
                    );

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.cloud.aws.endpoint", () -> localstack.getEndpoint().toString());
        r.add("spring.cloud.aws.region.static", localstack::getRegion);
        r.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
        r.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
    }

    @BeforeAll
    static void criarRecursos() throws Exception {
        localstack.execInContainer("awslocal", "s3", "mb", "s3://notas-bucket");
        localstack.execInContainer("awslocal", "sqs", "create-queue", "--queue-name", "notas-queue");
        localstack.execInContainer("awslocal", "dynamodb", "create-table",
                "--table-name", "notas",
                "--attribute-definitions", "AttributeName=PK,AttributeType=S", "AttributeName=SK,AttributeType=S",
                "--key-schema", "AttributeName=PK,KeyType=HASH", "AttributeName=SK,KeyType=RANGE",
                "--billing-mode", "PAY_PER_REQUEST");
    }

    @TestConfiguration
    static class AwsTestConfig {

        @Bean
        public S3Client s3Client() {
            return S3Client.builder()
                    .endpointOverride(localstack.getEndpoint())
                    .region(Region.of(localstack.getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
                    ))
                    .forcePathStyle(true)
                    .build();
        }

        @Bean
        public SqsClient sqsClient() {
            return SqsClient.builder()
                    .endpointOverride(localstack.getEndpoint())
                    .region(Region.of(localstack.getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
                    ))
                    .build();
        }

        @Bean
        public DynamoDbClient dynamoDbClient() {
            return DynamoDbClient.builder()
                    .endpointOverride(localstack.getEndpoint())
                    .region(Region.of(localstack.getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
                    ))
                    .build();
        }
    }
}