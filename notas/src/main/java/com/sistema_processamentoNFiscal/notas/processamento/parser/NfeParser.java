package com.sistema_processamentoNFiscal.notas.processamento.parser;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class NfeParser {

    public NfeDTO parse(InputStream xmlInputStream) throws Exception {
        // Configuração de segurança obrigatória (Proteção contra XXE)
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlInputStream);
        doc.getDocumentElement().normalize();

        // Extrair Chave de Acesso (Atributo Id da tag infNFe)
        NodeList infNFeList = doc.getElementsByTagName("infNFe");
        String chaveAcesso = null;
        if (infNFeList.getLength() > 0) {
            Element infNFeElement = (Element) infNFeList.item(0);
            chaveAcesso = infNFeElement.getAttribute("Id");
        }

        // Extrair CNPJ do Emitente
        String cnpjEmitente = getTagValue(doc, "emit", "CNPJ");

        // Extrair Valor Total da NF-e (vNF dentro de ICMSTot)
        String valorTotalStr = getTagValue(doc, "ICMSTot", "vNF");
        BigDecimal valorTotal = valorTotalStr != null ? new BigDecimal(valorTotalStr) : BigDecimal.ZERO;

        // Extrair Data de Emissão (dhEmi)
        String dataEmissaoStr = getTagValue(doc, "ide", "dhEmi");
        OffsetDateTime dataEmissao = dataEmissaoStr != null ? OffsetDateTime.parse(dataEmissaoStr) : null;

        return new NfeDTO(chaveAcesso, cnpjEmitente, valorTotal, dataEmissao);
    }

    // Método auxiliar para buscar os valores das tags internas com segurança
    private String getTagValue(Document doc, String parentTagName, String tagName) {
        NodeList parentList = doc.getElementsByTagName(parentTagName);
        if (parentList.getLength() > 0) {
            Element parentElement = (Element) parentList.item(0);
            NodeList childList = parentElement.getElementsByTagName(tagName);
            if (childList.getLength() > 0) {
                return childList.item(0).getTextContent();
            }
        }
        return null;
    }
}