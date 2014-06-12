/*
 * #%L
 * Gravia :: Repository
 * %%
 * Copyright (C) 2012 - 2014 JBoss by Red Hat
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.jboss.gravia.repository.spi;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.gravia.repository.Namespace100;
import org.jboss.gravia.repository.RepositoryStorageException;
import org.jboss.gravia.repository.RepositoryWriter;
import org.jboss.gravia.repository.Namespace100.Attribute;
import org.jboss.gravia.repository.Namespace100.Element;
import org.jboss.gravia.resource.Capability;
import org.jboss.gravia.resource.ContentCapability;
import org.jboss.gravia.resource.ContentNamespace;
import org.jboss.gravia.resource.Requirement;
import org.jboss.gravia.resource.Resource;
import org.jboss.gravia.resource.spi.AttributeValueHandler;
import org.jboss.gravia.resource.spi.AttributeValueHandler.AttributeValue;
import org.jboss.gravia.utils.IllegalArgumentAssertion;


/**
 * Write repository contnet to XML.
 *
 * @author thomas.diesler@jboss.com
 * @since 21-May-2012
 */
public abstract class AbstractRepositoryXMLWriter implements RepositoryWriter {

    private final XMLStreamWriter writer;

    public AbstractRepositoryXMLWriter(OutputStream outputStream) {
        IllegalArgumentAssertion.assertNotNull(outputStream, "outputStream");
        writer = createXMLStreamWriter(outputStream);
    }

    protected abstract XMLStreamWriter createXMLStreamWriter(OutputStream outputStream);

    @Override
    public void writeRepositoryElement(Map<String, String> attributes) {
        try {
            writer.writeStartDocument();
            writer.setDefaultNamespace(Namespace100.REPOSITORY_NAMESPACE);
            writer.writeStartElement(Element.REPOSITORY.getLocalName());
            writer.writeDefaultNamespace(Namespace100.REPOSITORY_NAMESPACE);
            for (Entry<String, String> entry : attributes.entrySet()) {
                writer.writeAttribute(entry.getKey(), entry.getValue());
            }
        } catch (XMLStreamException ex) {
            throw new RepositoryStorageException("Cannot write repository element", ex);
        }
    }

    @Override
    public void writeResource(Resource resource) {
        try {
            writeResource(writer, resource, null);
        } catch (IOException ex) {
            throw new RepositoryStorageException("Cannot write resource", ex);
        }
    }

    public static void writeResource(XMLStreamWriter writer, Resource resource, ContentHandler contentHandler) throws IOException {
        try {
            writer.writeStartElement(Element.RESOURCE.getLocalName());
            for (Capability cap : resource.getCapabilities(null)) {
                if (ContentNamespace.CONTENT_NAMESPACE.equals(cap.getNamespace())) {
                    writeContentCapability(writer, cap.adapt(ContentCapability.class), contentHandler);
                } else {
                    writeCapability(writer, cap);
                }
            }
            for (Requirement req : resource.getRequirements(null)) {
                writeRequirement(writer, req);
            }
            writer.writeEndElement();
        } catch (XMLStreamException ex) {
            throw new IllegalStateException("Cannot initialize repository writer", ex);
        }
    }

    public static void writeCapability(XMLStreamWriter writer, Capability cap) throws IOException, XMLStreamException {
        writer.writeStartElement(Element.CAPABILITY.getLocalName());
        writer.writeAttribute(Attribute.NAMESPACE.getLocalName(), cap.getNamespace());
        writeAttributes(writer, cap.getAttributes());
        writeDirectives(writer, cap.getDirectives());
        writer.writeEndElement();
    }

    public static void writeContentCapability(XMLStreamWriter writer, ContentCapability ccap, ContentHandler contentHandler) throws IOException, XMLStreamException {
        writer.writeStartElement(Element.CAPABILITY.getLocalName());
        writer.writeAttribute(Attribute.NAMESPACE.getLocalName(), ccap.getNamespace());
        writeAttributes(writer, contentHandler != null ? contentHandler.process(ccap) : ccap.getAttributes());
        writeDirectives(writer, ccap.getDirectives());
        writer.writeEndElement();
    }

    public static void writeRequirement(XMLStreamWriter writer, Requirement req) throws XMLStreamException {
        writer.writeStartElement(Element.REQUIREMENT.getLocalName());
        writer.writeAttribute(Attribute.NAMESPACE.getLocalName(), req.getNamespace());
        writeAttributes(writer, req.getAttributes());
        writeDirectives(writer, req.getDirectives());
        writer.writeEndElement();
    }

    @Override
    public void close() {
        try {
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.close();
        } catch (XMLStreamException ex) {
            throw new RepositoryStorageException("Cannot write repository element", ex);
        }
    }

    private static void writeAttributes(XMLStreamWriter writer, Map<String, Object> attributes) throws XMLStreamException {
        for (Entry<String, Object> entry : attributes.entrySet()) {
            AttributeValue attval = AttributeValue.create(entry.getValue());
            writer.writeStartElement(Element.ATTRIBUTE.getLocalName());
            writer.writeAttribute(Attribute.NAME.getLocalName(), entry.getKey());
            if (attval.isListType()) {
                writer.writeAttribute(Attribute.VALUE.getLocalName(), attval.getValueString());
                writer.writeAttribute(Attribute.TYPE.getLocalName(), "List<" + attval.getType() + ">");
            } else {
                writer.writeAttribute(Attribute.VALUE.getLocalName(), attval.getValueString());
                if (attval.getType() != AttributeValueHandler.Type.String) {
                    writer.writeAttribute(Attribute.TYPE.getLocalName(), attval.getType().toString());
                }
            }
            writer.writeEndElement();
        }
    }

    private static void writeDirectives(XMLStreamWriter writer, Map<String, String> directives) throws XMLStreamException {
        for (Entry<String, String> entry : directives.entrySet()) {
            writer.writeStartElement(Element.DIRECTIVE.getLocalName());
            writer.writeAttribute(Attribute.NAME.getLocalName(), entry.getKey());
            writer.writeAttribute(Attribute.VALUE.getLocalName(), entry.getValue());
            writer.writeEndElement();
        }
    }
}
