/*
 * #%L
 * JBossOSGi Repository: API
 * %%
 * Copyright (C) 2011 - 2012 JBoss by Red Hat
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
package org.jboss.gravia.repository;

import java.io.OutputStream;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.gravia.repository.AttributeValueHandler.AttributeValue;
import org.jboss.gravia.repository.Namespace100.Attribute;
import org.jboss.gravia.repository.Namespace100.Element;
import org.jboss.gravia.repository.Namespace100.Type;
import org.jboss.gravia.resource.Capability;
import org.jboss.gravia.resource.Requirement;
import org.jboss.gravia.resource.Resource;


/**
 * Write repository contnet to XML.
 *
 * @author thomas.diesler@jboss.com
 * @since 21-May-2012
 */
public class RepositoryXMLWriter implements RepositoryWriter {

    private final XMLStreamWriter writer;

    public static RepositoryWriter create(OutputStream output) {
        return new RepositoryXMLWriter(output);
    }

    private RepositoryXMLWriter(OutputStream output) {
        try {
            writer = XMLOutputFactory.newInstance().createXMLStreamWriter(output);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot initialize repository writer", ex);
        }
    }

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
            writer.writeStartElement(Element.RESOURCE.getLocalName());
            for (Capability cap : resource.getCapabilities(null)) {
                writer.writeStartElement(Element.CAPABILITY.getLocalName());
                writer.writeAttribute(Attribute.NAMESPACE.getLocalName(), cap.getNamespace());
                writeAttributes(cap.getAttributes());
                writeDirectives(cap.getDirectives());
                writer.writeEndElement();
            }
            for (Requirement req : resource.getRequirements(null)) {
                writer.writeStartElement(Element.REQUIREMENT.getLocalName());
                writer.writeAttribute(Attribute.NAMESPACE.getLocalName(), req.getNamespace());
                writeAttributes(req.getAttributes());
                writeDirectives(req.getDirectives());
                writer.writeEndElement();
            }
            writer.writeEndElement();
        } catch (XMLStreamException ex) {
            throw new IllegalStateException("Cannot initialize repository writer", ex);
        }
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

    private void writeAttributes(Map<String, Object> attributes) throws XMLStreamException {
        for (Entry<String, Object> entry : attributes.entrySet()) {
            AttributeValue attval = AttributeValue.create(entry.getValue());
            writer.writeStartElement(Element.ATTRIBUTE.getLocalName());
            writer.writeAttribute(Attribute.NAME.getLocalName(), entry.getKey());
            if (attval.isListType()) {
                writer.writeAttribute(Attribute.VALUE.getLocalName(), attval.getValueString());
                writer.writeAttribute(Attribute.TYPE.getLocalName(), "List<" + attval.getType() + ">");
            } else {
                writer.writeAttribute(Attribute.VALUE.getLocalName(), attval.getValueString());
                if (attval.getType() != Type.String) {
                    writer.writeAttribute(Attribute.TYPE.getLocalName(), attval.getType().toString());
                }
            }
            writer.writeEndElement();
        }
    }

    private void writeDirectives(Map<String, String> directives) throws XMLStreamException {
        for (Entry<String, String> entry : directives.entrySet()) {
            writer.writeStartElement(Element.DIRECTIVE.getLocalName());
            writer.writeAttribute(Attribute.NAME.getLocalName(), entry.getKey());
            writer.writeAttribute(Attribute.VALUE.getLocalName(), entry.getValue());
            writer.writeEndElement();
        }
    }
}