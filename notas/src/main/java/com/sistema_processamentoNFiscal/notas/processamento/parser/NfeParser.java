package com.sistema_processamentoNFiscal.notas.processamento.parser;

import com.sistema_processamentoNFiscal.notas.processamento.model.NotaFiscal;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class NfeParser {

    public NotaFiscal parse(InputStream xmlStream) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            Document doc = dbFactory.newDocumentBuilder().parse(xmlStream);
            doc.getDocumentElement().normalize();

            // Exemplo genérico de leitura de tags padrão de NF-e (ajuste conforme a estrutura do seu XML de exemplo)
            String chave = getTextContent(doc, "chNFe");
            if (chave == null || chave.isEmpty()) {
                // Tenta pegar pelo atributo Id da tag infNFe caso chNFe não venha direta
                Element infNFe = (Element) doc.getElementsByTagName("infNFe").item(0);
                if (infNFe != null) {
                    String id = infNFe.getAttribute("Id");
                    chave = id.replace("NFe", "");
                }
            }

            String cnpj = getTextContent(doc, "CNPJ");
            String vTot = getTextContent(doc, "vNF");
            String dEmi = getTextContent(doc, "dhEmi");

            if (dEmi != null && dEmi.length() >= 10) {
                dEmi = dEmi.substring(0, 10); // Pega apenas a data YYYY-MM-DD
            }

            return new NotaFiscal(
                    chave != null ? chave : "DESCONHECIDA",
                    cnpj != null ? cnpj : "00000000000000",
                    vTot != null ? new BigDecimal(vTot) : BigDecimal.ZERO,
                    dEmi != null ? LocalDate.parse(dEmi) : LocalDate.now()
            );

        } catch (Exception e) {
            throw new IllegalArgumentException("Erro ao fazer parse do XML da NF-e", e);
        }
    }

    private String getTextContent(Document doc, String tagName) {
        var nodes = doc.getElementsByTagName(tagName);
        if (nodes != null && nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }
}