/*
 * #%L
 * Gravia :: Runtime :: API
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
package org.jboss.gravia.runtime.spi;

import java.util.Dictionary;
import java.util.jar.Manifest;

import org.jboss.gravia.utils.IllegalArgumentAssertion;
import org.jboss.gravia.utils.ManifestUtils;

/**
 * Provides Moduel headers from a manifest
 *
 * @author thomas.diesler@jboss.com
 * @since 27-Sep-2013
 *
 * @ThreadSafe
 */
public final class ManifestHeadersProvider implements HeadersProvider {

    private final Dictionary<String, String> headers;

    public ManifestHeadersProvider(Manifest manifest) {
        IllegalArgumentAssertion.assertNotNull(manifest, "manifest");
        headers = ManifestUtils.getManifestHeaders(manifest);
    }

    /**
     * Return a mutable dictionary of manifest headers
     */
    public Dictionary<String, String> getHeaders() {
        return headers;
    }
}
