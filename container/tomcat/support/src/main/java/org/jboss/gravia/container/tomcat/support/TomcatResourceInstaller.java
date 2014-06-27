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

import static org.jboss.gravia.container.tomcat.support.GraviaTomcatLogger.LOGGER;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.file.Path;

import org.apache.catalina.User;
import org.apache.catalina.ant.DeployTask;
import org.apache.catalina.ant.UndeployTask;
import org.jboss.gravia.container.tomcat.extension.SharedModuleClassLoader;
import org.jboss.gravia.provision.ResourceHandle;
import org.jboss.gravia.provision.ResourceInstaller;
import org.jboss.gravia.provision.spi.AbstractResourceHandle;
import org.jboss.gravia.provision.spi.AbstractResourceInstaller;
import org.jboss.gravia.provision.spi.RuntimeEnvironment;
import org.jboss.gravia.resource.Capability;
import org.jboss.gravia.resource.ContentCapability;
import org.jboss.gravia.resource.ContentNamespace;
import org.jboss.gravia.resource.DefaultResourceBuilder;
import org.jboss.gravia.resource.Requirement;
import org.jboss.gravia.resource.Resource;
import org.jboss.gravia.resource.ResourceBuilder;
import org.jboss.gravia.resource.ResourceContent;
import org.jboss.gravia.resource.ResourceIdentity;
import org.jboss.gravia.resource.Version;
import org.jboss.gravia.runtime.Module;
import org.jboss.gravia.runtime.Runtime;
import org.jboss.gravia.runtime.RuntimeLocator;
import org.jboss.gravia.runtime.spi.NamedResourceAssociation;
import org.jboss.gravia.utils.IOUtils;
import org.jboss.gravia.utils.ResourceUtils;

/**
 * Service providing the {@link ResourceInstaller}.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 13-Jan-2014
 */
public class TomcatResourceInstaller extends AbstractResourceInstaller {

    private final RuntimeEnvironment environment;
    private final TomcatRuntime runtime;

    public TomcatResourceInstaller(RuntimeEnvironment environment) {
        this.runtime = (TomcatRuntime) RuntimeLocator.getRequiredRuntime();
        this.environment = environment;
    }

    @Override
    public RuntimeEnvironment getEnvironment() {
        return environment;
    }

    @Override
    public ResourceHandle installResourceProtected(Context context, Resource resource) throws Exception {
        ResourceHandle handle;
        if (ResourceUtils.isShared(resource)) {
            handle = installSharedResourceInternal(context, resource);
        } else {
            handle = installUnsharedResourceInternal(context, resource);
        }
        return handle;
    }

    private ResourceHandle installSharedResourceInternal(Context context, Resource resource) throws Exception {
        LOGGER.info("Installing shared resource: {}", resource);

        ResourceIdentity resid = resource.getIdentity();
        String symbolicName = resid.getSymbolicName();
        Version version = resid.getVersion();

        // copy resource content
        Path catalinaLib = runtime.getCatalinaHome().resolve("lib");
        final File targetFile = catalinaLib.resolve(symbolicName + "-" + version + ".jar").toFile();
        if (targetFile.exists()) {
            LOGGER.warn("Module already exists: " + targetFile);
        } else {
            ResourceContent content = getFirstRelevantResourceContent(resource);
            IOUtils.copyStream(content.getContent(), new FileOutputStream(targetFile));
        }

        // Install the shared module
        final Module module = installSharedResource(resource, targetFile);

        // Start the module
        module.start();

        final Resource modres = module.adapt(Resource.class);
        return new AbstractResourceHandle(modres, module) {
            @Override
            public void uninstall() {
                LOGGER.info("Uninstall shared resource: {}", modres);
                SharedModuleClassLoader.removeSharedModule(modres);
                module.uninstall();
                targetFile.delete();
            }
        };
    }

    private ResourceHandle installUnsharedResourceInternal(Context context, Resource resource) throws Exception {
        LOGGER.info("Install unshared resource: {}", resource);

        File tempfile = null;
        ResourceIdentity identity = resource.getIdentity();
        String runtimeName = getRuntimeName(resource);

        Path catalinaTemp = runtime.getCatalinaHome().resolve("temp");
        ContentCapability ccap = (ContentCapability) resource.getCapabilities(ContentNamespace.CONTENT_NAMESPACE).get(0);
        URL contentURL = ccap.getContentURL();
        if (contentURL == null || !contentURL.toExternalForm().startsWith("file:")) {
            ResourceContent content = getFirstRelevantResourceContent(resource);
            tempfile = catalinaTemp.resolve(runtimeName).toFile();
            IOUtils.copyStream(content.getContent(), new FileOutputStream(tempfile));
            contentURL = tempfile.toURI().toURL();
        }

        // Get contextPath, username, password
        final String contextPath = getContextPath(resource);
        final User user = runtime.getUserDatabase().findUser(TomcatRuntime.TOMCAT_USER);
        final String password = user.getPassword();

        NamedResourceAssociation.putResource(contextPath, resource);
        try {
            DeployTask task = new DeployTask();
            task.setWar(contentURL.toExternalForm());
            task.setUsername(user.getName());
            task.setPassword(password);
            task.setPath(contextPath);
            task.execute();
        } finally {
            NamedResourceAssociation.removeResource(contextPath);
            if (tempfile != null) {
                tempfile.delete();
            }
        }

        // Get the resource as module (may be null)
        Runtime runtime = RuntimeLocator.getRequiredRuntime();
        Module module = runtime.getModule(identity);

        Resource modres = module != null ? module.adapt(Resource.class) : resource;
        return new AbstractResourceHandle(modres, module) {
            @Override
            public void uninstall() {
                UndeployTask task = new UndeployTask();
                task.setUsername(user.getName());
                task.setPassword(password);
                task.setPath(contextPath);
                task.execute();
            }
        };
    }

    private Module installSharedResource(Resource resource, File targetFile) throws Exception {

        // Get a resource copy with updated content capability
        ResourceBuilder builder = new DefaultResourceBuilder();
        for (Capability cap : resource.getCapabilities(null)) {
            String namespace = cap.getNamespace();
            if (!ContentNamespace.CONTENT_NAMESPACE.equals(namespace)) {
                builder.addCapability(namespace, cap.getAttributes(), cap.getDirectives());
            }
        }
        builder.addContentCapability(targetFile.toURI().toURL());
        for (Requirement req : resource.getRequirements(null)) {
            builder.addRequirement(req.getNamespace(), req.getAttributes(), req.getDirectives());
        }
        resource = builder.getResource();

        // Add the module to the {@link SharedModuleClassLoader}
        SharedModuleClassLoader.addSharedModule(resource);

        Runtime runtime = RuntimeLocator.getRequiredRuntime();
        ClassLoader classLoader = SharedModuleClassLoader.class.getClassLoader();
        return runtime.installModule(classLoader, resource, null);
    }

    private String getContextPath(Resource res) {
        String contextPath = (String) res.getIdentityCapability().getAttribute("contextPath");
        if (contextPath == null)
            contextPath = res.getIdentity().getSymbolicName();
        return "/" + contextPath;
    }
}
