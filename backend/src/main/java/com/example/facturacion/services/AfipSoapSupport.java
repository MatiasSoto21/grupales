package com.example.facturacion.services;

import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

final class AfipSoapSupport {
    private AfipSoapSupport() { }

    static String valor(String xml, String etiqueta) throws Exception {
        return valor(xml, null, etiqueta);
    }

    static String valor(String xml, String padre, String etiqueta) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException exception) throws org.xml.sax.SAXException { throw exception; }
            @Override public void fatalError(org.xml.sax.SAXParseException exception) throws org.xml.sax.SAXException { throw exception; }
        });
        Element element = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
        if (padre != null) {
            NodeList parents = element.getElementsByTagNameNS("*", padre);
            if (parents.getLength() == 0) return null;
            element = (Element) parents.item(0);
        }
        NodeList nodes = element.getElementsByTagNameNS("*", etiqueta);
        if (nodes.getLength() == 0) return null;
        String value = nodes.item(0).getTextContent().trim();
        return value.isEmpty() ? null : value;
    }
}
