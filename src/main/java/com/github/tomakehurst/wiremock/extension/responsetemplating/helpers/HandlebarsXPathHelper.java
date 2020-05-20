/*
 * Copyright (C) 2011 Thomas Akehurst
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.tomakehurst.wiremock.extension.responsetemplating.helpers;

import com.github.jknack.handlebars.Options;
import com.github.tomakehurst.wiremock.common.Xml;
import com.github.tomakehurst.wiremock.common.XmlException;
import com.github.tomakehurst.wiremock.extension.responsetemplating.RenderCache;
import org.custommonkey.xmlunit.exceptions.XpathException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;

import static com.github.tomakehurst.wiremock.common.Exceptions.throwUnchecked;
import static javax.xml.xpath.XPathConstants.NODE;

/**
 * This class uses javax.xml.xpath.* for reading a xml via xPath so that the result can be used for response
 * templating.
 */
public class HandlebarsXPathHelper extends HandlebarsHelper<String> {

    private static final InheritableThreadLocal<XPath> localXPath = new InheritableThreadLocal<XPath>() {
        @Override
        protected XPath initialValue() {
            final XPathFactory xPathfactory = XPathFactory.newInstance();
            return xPathfactory.newXPath();
        }
    };

    @Override
    public Object apply(final String inputXml, final Options options) throws IOException {
        if (inputXml == null ) {
            return "";
        }

        if (options.param(0, null) == null) {
            return handleError("The XPath expression cannot be empty");
        }

        final String xPathInput = options.param(0);

        Document doc;
        try {
            doc = getDocument(inputXml, options);
        } catch (XmlException se) {
            return handleError(inputXml + " is not valid XML");
        }

        try {
            Node node = getNode(getXPathPrefix() + xPathInput, doc, options);

            if (node == null) {
                return "";
            }

            return Xml.toStringValue(node);
        } catch (XpathException e) {
            return handleError(xPathInput + " is not a valid XPath expression", e);
        }
    }

    private Node getNode(String xPathExpression, Document doc, Options options) throws XpathException {
        RenderCache renderCache = getRenderCache(options);
        RenderCache.Key cacheKey = RenderCache.Key.keyFor(Document.class, xPathExpression, doc);
        Node node = renderCache.get(cacheKey);

        if (node == null) {
//            NodeList nodes = Xml.findNodesByXPath(doc, xPathExpression, null);
//            node = nodes.getLength() == 0 ? null : nodes.item(0);
            XPath xPath = localXPath.get();
            try {
                node = (Node) xPath.evaluate(xPathExpression, doc, NODE);
                renderCache.put(cacheKey, node);
            } catch (XPathExpressionException e) {
                throw new XpathException(e);
            }
        }

        return node;
    }

    private Document getDocument(String xml, Options options) {
        RenderCache renderCache = getRenderCache(options);
        RenderCache.Key cacheKey = RenderCache.Key.keyFor(Document.class, xml);
        Document document = renderCache.get(cacheKey);
        if (document == null) {
            try (final StringReader reader = new StringReader(xml)) {
                InputSource source = new InputSource(reader);
                document = Xml.read(source);
                renderCache.put(cacheKey, document);
            }
        }

        return document;
    }

    /**
     * No prefix by default. It allows to extend this class with a specified prefix. Just overwrite this method to do
     * so.
     *
     * @return a prefix which will be applied before the specified xpath.
     */
    protected String getXPathPrefix() {
        return "";
    }
}
