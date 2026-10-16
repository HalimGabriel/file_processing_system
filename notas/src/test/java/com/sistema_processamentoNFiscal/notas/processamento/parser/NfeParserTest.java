package com.sistema_processamentoNFiscal.notas.processamento.parser;

import com.sistema_processamentoNFiscal.notas.processamento.model.NotaFiscal;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class NfeParserTest {

    @Test
    public void deveExtrairDadosDaNfeComSucesso() throws Exception {
        // Arrange: Carrega o XML de exemplo da pasta src/test/resources/nfe-exemplo.xml
        ClassPathResource resource = new ClassPathResource("nfe-exemplo.xml");
        InputStream inputStream = resource.getInputStream();

        NfeParser parser = new NfeParser();

        // Act: Executa o parsing passando o InputStream do XML
        NotaFiscal nota = parser.parse(inputStream);

        // Assert: Verifica se os dados foram extraídos perfeitamente usando JUnit 5
        assertNotNull(nota, "A NotaFiscal retornada não deveria ser nula");
        assertEquals("35261000000000000000550010000000011000000014", nota.chaveAcesso());
        assertEquals("00000000000191", nota.emitenteCnpj());
        assertEquals(0, new BigDecimal("150.00").compareTo(nota.valorTotal()), "O valor total deve ser 150.00");
        assertEquals(LocalDate.parse("2026-10-08"), nota.dataEmissao(), "A data de emissão deve bater");
    }
    @Test
    public void deveRejeitarXmlComDoctype() {
        String xml = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <nfeProc><NFe>&x;</NFe></nfeProc>
            """;
        NfeParser parser = new NfeParser();

        assertThrows(IllegalArgumentException.class, () ->
                parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
    }
}