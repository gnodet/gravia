/*
 * #%L
 * Wildfly Gravia Subsystem
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

package org.jboss.gravia.container.tomcat.webapp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.catalina.ant.DeployTask;
import org.apache.catalina.ant.UndeployTask;
import org.apache.catalina.users.MemoryUserDatabase;
import org.jboss.gravia.container.tomcat.extension.SharedModuleClassLoader;
import org.jboss.gravia.provision.DefaultResourceHandle;
import org.jboss.gravia.provision.ResourceHandle;
import org.jboss.gravia.provision.ResourceInstaller;
import org.jboss.gravia.provision.spi.AbstractResourceInstaller;
import org.jboss.gravia.provision.spi.RuntimeEnvironment;
import org.jboss.gravia.resource.Capability;
import org.jboss.gravia.resource.ContentCapability;
import org.jboss.gravia.resource.ContentNamespace;
import org.jboss.gravia.resource.DefaultResourceBuilder;
import org.jboss.gravia.resource.Requirement;
import org.jboss.gravia.resource.Resource;
import org.jboss.gravia.resource.ResourceContent;
import org.jboss.gravia.resource.ResourceIdentity;
import org.jboss.gravia.runtime.Runtime;
import org.jboss.gravia.runtime.RuntimeLocator;
import org.jboss.gravia.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service providing the {@link ResourceInstaller}.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 13-Jan-2014
 */
public class TomcatResourceInstaller extends AbstractResourceInstaller {

    static final Logger LOGGER = LoggerFactory.getLogger(TomcatResourceInstaller.class);

    private final static File catalinaHome = new File(SecurityActions.getSystemProperty("catalina.home", null));
    private final static File catalinaLib = new File(catalinaHome.getPath() + File.separator + "lib");
    private final static File catalinaTemp = new File(catalinaHome.getPath() + File.separator + "temp");

    private final static String TOMCAT_USER = "tomcat";

    private final UserDatabase userDatabase;
    private final RuntimeEnvironment environment;

    public TomcatResourceInstaller(RuntimeEnvironment environment) {
        this.environment = environment;
        try {
            userDatabase = new MemoryUserDatabase();
            userDatabase.open();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot open user database", ex);
        }
        if (userDatabase.findUser(TOMCAT_USER) == null)
            throw new IllegalStateException("Cannot obtain user: " + TOMCAT_USER);
    }

    @Override
    public RuntimeEnvironment getEnvironment() {
        return environment;
    }

    @Override
    public ResourceHandle installSharedResource(Resource resource, Map<Requirement, Resource> mapping) throws Exception {
        LOGGER.info("Installing shared resource: {}", resource);

        ResourceIdentity resid = resource.getIdentity();
        ResourceContent content = resource.adapt(ResourceContent.class);
        if (content == null)
            throw new IllegalStateException("Cannot obtain content from: " + resource);

        // copy resource content
        File targetFile = new File(catalinaLib, resid.getSymbolicName() + "-" + resid.getVersion() + ".jar");
        if (targetFile.exists())
            throw new IllegalStateException("Module already exists: " + targetFile);

        IOUtils.copyStream(content.getContent(), new FileOutputStream(targetFile));

        installSharedResource(resource, targetFile);

        return new DefaultResourceHandle(resource) {
            @Override
            public void uninstall() {
                // cannot uninstall shared resource
            }
        };
    }

    @Override
    public ResourceHandle installUnsharedResource(Resource res, Map<Requirement, Resource> mapping) throws Exception {
        LOGGER.info("Installing unshared resource: {}", res);

        File tempfile = null;
        ResourceIdentity identity = res.getIdentity();
        ContentCapability ccap = (ContentCapability) res.getCapabilities(ContentNamespace.CONTENT_NAMESPACE).get(0);
        URL contentURL = ccap.getContentURL();
        if (contentURL == null) {
            InputStream content = res.adapt(ResourceContent.class).getContent();
            tempfile = new File(catalinaTemp, identity.getSymbolicName() + "-" + identity.getVersion() + ".war");
            IOUtils.copyStream(content, new FileOutputStream(tempfile));
            contentURL = tempfile.toURI().toURL();
        }

        // Get contextPath, username, password
        final String contextPath = getContextPath(res);
        final User user = userDatabase.findUser(TOMCAT_USER);
        final String password = user.getPassword();

        try {
            DeployTask task = new DeployTask();
            task.setWar(contentURL.toExternalForm());
            task.setUsername(user.getName());
            task.setPassword(password);
            task.setPath(contextPath);
            task.execute();
        } finally {
            if (tempfile != null) {
                tempfile.delete();
            }
        }

        return new DefaultResourceHandle(res) {
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

    private Resource installSharedResource(Resource resource, File targetFile) throws Exception {

        // Get a resource copy with updated content capability
        DefaultResourceBuilder builder = new DefaultResourceBuilder();
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
        runtime.installModule(classLoader, resource, null);

        return resource;
    }

    private String getContextPath(Resource res) {
        String contextPath = (String) res.getIdentityCapability().getAttribute("contextPath");
        if (contextPath == null)
            contextPath = res.getIdentity().getSymbolicName();
        return "/" + contextPath;
    }
}
