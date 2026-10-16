package com.sistema_processamentoNFiscal.notas.processamento.parser;

import com.sistema_processamentoNFiscal.notas.processamento.model.NotaFiscal;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class NfeParser {

    public NotaFiscal parse(InputStream xmlStream) {
        try {
            Document doc = novaFactorySegura().newDocumentBuilder().parse(xmlStream);
            doc.getDocumentElement().normalize();

            String chave = extrairChave(doc);
            if (chave == null || !chave.matches("\\d{44}")) {
                throw new IllegalArgumentException("Chave de acesso ausente ou inválida");
            }

            // CNPJ do emitente: dentro da tag <emit>, não o primeiro <CNPJ> do documento
            String cnpj = textoDentro(doc, "emit", "CNPJ");
            if (cnpj == null || !cnpj.matches("\\d{14}")) {
                throw new IllegalArgumentException("CNPJ do emitente ausente ou inválido");
            }

            String vNF = textoDentro(doc, "ICMSTot", "vNF");
            if (vNF == null) {
                throw new IllegalArgumentException("Valor total (vNF) ausente");
            }

            String dhEmi = primeiroTexto(doc, "dhEmi");
            if (dhEmi == null || dhEmi.length() < 10) {
                throw new IllegalArgumentException("Data de emissão (dhEmi) ausente");
            }

            return new NotaFiscal(
                    chave,
                    cnpj,
                    new BigDecimal(vNF.trim()),
                    LocalDate.parse(dhEmi.trim().substring(0, 10))
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Erro ao fazer parse do XML da NF-e", e);
        }
    }

    private DocumentBuilderFactory novaFactorySegura() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // bloqueia XXE
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f;
    }

    private String extrairChave(Document doc) {
        String chave = primeiroTexto(doc, "chNFe");
        if (chave != null) {
            return chave.trim();
        }
        var nodes = doc.getElementsByTagNameNS("*", "infNFe");
        if (nodes.getLength() > 0) {
            String id = ((Element) nodes.item(0)).getAttribute("Id");
            return id.replace("NFe", "").trim();
        }
        return null;
    }

    private String primeiroTexto(Document doc, String tag) {
        var nodes = doc.getElementsByTagNameNS("*", tag);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : null;
    }

    private String textoDentro(Document doc, String pai, String filho) {
        var pais = doc.getElementsByTagNameNS("*", pai);
        if (pais.getLength() == 0) {
            return null;
        }
        var filhos = ((Element) pais.item(0)).getElementsByTagNameNS("*", filho);
        return filhos.getLength() > 0 ? filhos.item(0).getTextContent().trim() : null;
    }
}