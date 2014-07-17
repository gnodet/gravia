/*
 * #%L
 * Gravia :: Container :: Tomcat :: Support
 * %%
 * Copyright (C) 2010 - 2014 JBoss by Red Hat
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
package org.jboss.gravia.container.tomcat.support;

import javax.servlet.ServletContext;

import org.jboss.gravia.container.tomcat.WebAppContextListener;
import org.jboss.gravia.resource.spi.AttachableSupport;
import org.jboss.gravia.runtime.Runtime;
import org.jboss.gravia.runtime.spi.PropertiesProvider;
import org.jboss.gravia.runtime.spi.RuntimeFactory;

/**
 * The Tomcat {@link RuntimeFactory}
 *
 * @author thomas.diesler@jboss.com
 * @since 27-Sep-2013
 */
public class TomcatRuntimeFactory implements RuntimeFactory {

    private final ServletContext servletContext;

    public TomcatRuntimeFactory(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Override
    public Runtime createRuntime(PropertiesProvider propertiesProvider) {
        AttachableSupport context = new AttachableSupport();
        context.putAttachment(WebAppContextListener.SERVLET_CONTEXT_KEY, servletContext);
        return new TomcatRuntime(propertiesProvider, context);
    }
}
