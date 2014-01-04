/*
 * #%L
 * JBossOSGi Resolver API
 * %%
 * Copyright (C) 2010 - 2013 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.gravia.resource;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import org.jboss.gravia.resource.ResourceType.AttributeType;
import org.jboss.gravia.resource.ResourceType.CapabilityType;
import org.jboss.gravia.resource.ResourceType.DirectiveType;


/**
 * A {@link Resource} builder for {@link CompositeData}.
 *
 * @author thomas.diesler@jboss.com
 * @since 03-Jan-2014
 *
 * @NotThreadSafe
 */
public final class ManagementResourceBuilder extends DefaultResourceBuilder {

    public ManagementResourceBuilder(CompositeData resData) {
        CompositeData capsData = (CompositeData) resData.get(ResourceType.ITEM_CAPABILITIES);
        for(Object obj : capsData.values()) {
            CompositeData capData = (CompositeData) obj;
            String namespace = (String) capData.get(CapabilityType.ITEM_NAMESPACE);
            TabularData attsData = (TabularData) capData.get(CapabilityType.ITEM_ATTRIBUTES);
            Map<String, Object> atts = getAttributes(attsData);
            TabularData dirsData = (TabularData) capData.get(CapabilityType.ITEM_DIRECTIVES);
            Map<String, String> dirs = getDirectives(dirsData);
            addCapability(namespace, atts, dirs);
        }
        CompositeData reqsData = (CompositeData) resData.get(ResourceType.ITEM_REQUIREMENTS);
        for(Object obj : reqsData.values()) {
            CompositeData reqData = (CompositeData) obj;
            String namespace = (String) reqData.get(CapabilityType.ITEM_NAMESPACE);
            TabularData attsData = (TabularData) reqData.get(CapabilityType.ITEM_ATTRIBUTES);
            Map<String, Object> atts = getAttributes(attsData);
            TabularData dirsData = (TabularData) reqData.get(CapabilityType.ITEM_DIRECTIVES);
            Map<String, String> dirs = getDirectives(dirsData);
            addRequirement(namespace, atts, dirs);
        }
    }

    private Map<String, Object> getAttributes(TabularData attsData) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for(Object obj : attsData.values()) {
            CompositeData attData = (CompositeData) obj;
            String key = (String) attData.get(AttributeType.ITEM_KEY);
            Object value = attData.get(AttributeType.ITEM_VALUE);
            result.put(key, value);
        }
        return result;
    }

    private Map<String, String> getDirectives(TabularData dirsData) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for(Object obj : dirsData.values()) {
            CompositeData dirData = (CompositeData) obj;
            String key = (String) dirData.get(DirectiveType.ITEM_KEY);
            String value = (String) dirData.get(DirectiveType.ITEM_VALUE);
            result.put(key, value);
        }
        return result;
    }
}
