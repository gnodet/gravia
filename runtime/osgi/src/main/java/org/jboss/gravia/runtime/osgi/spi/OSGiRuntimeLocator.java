/*
 * #%L
 * Gravia :: Runtime :: OSGi
 * %%
 * Copyright (C) 2013 - 2014 JBoss by Red Hat
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
package org.jboss.gravia.runtime.osgi.spi;

import org.jboss.gravia.runtime.Runtime;
import org.jboss.gravia.runtime.RuntimeLocator;
import org.jboss.gravia.runtime.spi.PropertiesProvider;
import org.osgi.framework.BundleContext;

/**
 * Locates the an OSGi Runtime instance
 *
 * @author thomas.diesler@jboss.com
 * @since 27-Sep-2013
 *
 * @ThreadSafe
 */
public final class OSGiRuntimeLocator {

    public static Runtime getRuntime() {
        return RuntimeLocator.getRuntime();
    }

    public static Runtime createRuntime(BundleContext syscontext) {
        PropertiesProvider propsProvider = new OSGiPropertiesProvider(syscontext);
        return RuntimeLocator.createRuntime(new OSGiRuntimeFactory(syscontext), propsProvider);
    }

    public static void releaseRuntime() {
        RuntimeLocator.releaseRuntime();
    }
}
