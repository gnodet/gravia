/*
 * #%L
 * JBossOSGi Runtime
 * %%
 * Copyright (C) 2013 JBoss by Red Hat
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
package org.jboss.gravia.runtime.osgi;

import org.jboss.gravia.runtime.RuntimeLocator;
import org.osgi.framework.BundleContext;

/**
 * [TODO]
 *
 * @author thomas.diesler@jboss.com
 * @since 27-Sep-2013
 */
public final class OSGiRuntimeLocator {

    public static OSGiRuntime getRuntime() {
        return (OSGiRuntime) RuntimeLocator.getRuntime();
    }

    public static void setRuntime(OSGiRuntime runtime) {
        RuntimeLocator.setRuntime(runtime);
    }

    public static OSGiRuntime locateRuntime(BundleContext context) {
        OSGiRuntime runtime = getRuntime();
        if (runtime == null) {
            BundleContextPropertiesProvider props = new BundleContextPropertiesProvider(context);
            runtime = new OSGiRuntimeFactory().createRuntime(props);
            RuntimeLocator.setRuntime(runtime);
        }
        return runtime;
    }
}