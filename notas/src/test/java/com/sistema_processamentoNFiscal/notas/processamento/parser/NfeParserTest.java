package com.sistema_processamentoNFiscal.notas.processamento.parser;

import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.testng.Assert;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.testng.Assert.*;

public class NfeParserTest {

    @Test
    public void deveExtrairDadosDaNfeComSucesso() throws Exception {
        // Arrange: Carrega o XML de exemplo da pasta src/test/resources/nfe-exemplo.xml
        ClassPathResource resource = new ClassPathResource("nfe-exemplo.xml");
        InputStream inputStream = resource.getInputStream();

        NfeParser parser = new NfeParser();

        // Act: Executa o parsing passando o InputStream do XML
        NfeDTO nfeDto = parser.parse(inputStream);

        // Assert: Verifica se os dados foram extraídos perfeitamente usando JUnit 5
        Assert.assertNotNull(nfeDto, "O DTO retornado não deveria ser nulo");
        assertEquals("NFe35261000000000000000550010000000011000000014", nfeDto.chaveAcesso());
        assertEquals("00000000000191", nfeDto.cnpjEmitente());
        Assert.assertEquals(0, new BigDecimal("150.00").compareTo(nfeDto.valorTotal()), "O valor total deve ser 150.00");
        Assert.assertTrue(OffsetDateTime.parse("2026-10-08T10:00:00-03:00").isEqual(nfeDto.dataEmissao()), "A data de emissão deve bater");
    }
}