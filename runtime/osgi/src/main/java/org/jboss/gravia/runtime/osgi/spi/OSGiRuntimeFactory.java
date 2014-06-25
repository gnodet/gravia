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
import org.jboss.gravia.runtime.osgi.internal.OSGiRuntime;
import org.jboss.gravia.runtime.spi.PropertiesProvider;
import org.jboss.gravia.runtime.spi.RuntimeFactory;
import org.jboss.gravia.utils.IllegalArgumentAssertion;
import org.osgi.framework.BundleContext;

/**
 * The factory for the OSGi {@link Runtime}.
 *
 * @author thomas.diesler@jboss.com
 * @since 27-Sep-2013
 */
public final class OSGiRuntimeFactory implements RuntimeFactory {

    private final BundleContext context;

    public OSGiRuntimeFactory(BundleContext context) {
        IllegalArgumentAssertion.assertNotNull(context, "context");
        this.context = context;
    }

    @Override
    public Runtime createRuntime(PropertiesProvider props) {
        return new OSGiRuntime(context, props);
    }
}
