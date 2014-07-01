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

import org.jboss.gravia.runtime.spi.PropertiesProvider;
import org.jboss.gravia.utils.IllegalStateAssertion;
import org.osgi.framework.BundleContext;


/**
 * A {@link PropertiesProvider} that delegates to a {@link BundleContext}
 *
 * @author thomas.diesler@jboss.com
 * @since 27-Sep-2013
 */
public final class BundleContextPropertiesProvider implements PropertiesProvider {

    private final BundleContext bundleContext;

    public BundleContextPropertiesProvider(BundleContext context) {
        this.bundleContext = context;
    }

    @Override
    public Object getProperty(String key) {
        return getProperty(key, null);
    }

    @Override
	public Object getRequiredProperty(String key) {
        Object value = bundleContext.getProperty(key);
        IllegalStateAssertion.assertNotNull(value, "Cannot obtain property: " + key);
		return value;
	}

	@Override
    public Object getProperty(String key, Object defaultValue) {
        Object value = bundleContext.getProperty(key);
        return value != null ? value : defaultValue;
    }

}
