package com.sistema_processamentoNFiscal.notas.consulta;

import com.sistema_processamentoNFiscal.notas.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ConsultaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DynamoDbClient dynamo;

    private final String tenantA = "12345678000190";
    private final String tenantB = "98765432000110";
    private final String chaveValida = "35261000000000000000550010000000011000000014";

    @BeforeEach
    void setup() {
        // Insere a nota pertencente ao Tenant A no DynamoDB
        dynamo.putItem(PutItemRequest.builder()
                .tableName("notas")
                .item(Map.of(
                        "PK", AttributeValue.fromS("TENANT#" + tenantA),
                        "SK", AttributeValue.fromS("NOTA#" + chaveValida),
                        "status", AttributeValue.fromS("PROCESSADA"),
                        "emitenteCnpj", AttributeValue.fromS(tenantA),
                        "valorTotal", AttributeValue.fromN("150.50"),
                        "dataEmissao", AttributeValue.fromS("2026-10-13")
                ))
                .build());
    }

    @Test
    void deveRetornarNotaQuandoTenantA_ConsultaSuaPrpriaNota() throws Exception {
        mockMvc.perform(get("/notas/" + chaveValida)
                        .with(jwt().jwt(token -> token
                                .claim("custom:tenantId", tenantA)
                                .subject("usuario-tenant-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaveAcesso").value(chaveValida))
                .andExpect(jsonPath("$.status").value("PROCESSADA"))
                .andExpect(jsonPath("$.emitenteCnpj").value(tenantA));
    }

    @Test
    void deveRetornar404QuandoTenantB_TentaConsultarNotaDoTenantA() throws Exception {
        // Tenant B tenta buscar a nota, mas o token dele tem outro CNPJ
        mockMvc.perform(get("/notas/" + chaveValida)
                        .with(jwt().jwt(token -> token
                                .claim("custom:tenantId", tenantB)
                                .subject("usuario-tenant-b"))))
                .andExpect(status().isNotFound()); // Como a PK no banco é do Tenant A, o Tenant B não acha nada
    }

    @Test
    void deveRetornar401QuandoRequisicaoNaoTemTokenJWT() throws Exception {
        mockMvc.perform(get("/notas/" + chaveValida))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornar400QuandoChaveTiverFormatoInvalido() throws Exception {
        String chaveInvalida = "12345";

        mockMvc.perform(get("/notas/" + chaveInvalida)
                        .with(jwt().jwt(token -> token.claim("custom:tenantId", tenantA))))
                .andExpect(status().isBadRequest());
    }
}