/*
 * #%L
 * Gravia :: Runtime :: Embedded
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
package org.jboss.gravia.runtime.embedded.internal;

import org.jboss.gravia.runtime.RuntimeType;
import org.jboss.gravia.runtime.embedded.spi.AbstractRuntimePlugin;


/**
 * The internal HttpService plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 28-Nov-2013
 */
public final class HttpServicePlugin extends AbstractRuntimePlugin {

    @Override
    public String getBundleActivator() {
        if (RuntimeType.getRuntimeType() == RuntimeType.TOMCAT || RuntimeType.getRuntimeType() == RuntimeType.WILDFLY) {
            return "org.apache.felix.http.bridge.internal.BridgeActivator";
        } else {
            return "org.apache.felix.http.bundle.internal.CombinedActivator";
        }
    }
}
