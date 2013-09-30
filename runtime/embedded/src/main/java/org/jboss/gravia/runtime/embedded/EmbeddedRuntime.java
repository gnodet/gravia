/*
 * #%L
 * JBossOSGi Framework
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
package org.jboss.gravia.runtime.embedded;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.jar.Manifest;

import org.jboss.gravia.resource.Resource;
import org.jboss.gravia.runtime.Module;
import org.jboss.gravia.runtime.ModuleEvent;
import org.jboss.gravia.runtime.Module.State;
import org.jboss.gravia.runtime.Runtime;
import org.jboss.logging.Logger;

/**
 * [TODO]
 *
 * @author thomas.diesler@jboss.com
 * @since 27-Sep-2013
 */
public final class EmbeddedRuntime implements Runtime {

    static Logger LOGGER = Logger.getLogger(Runtime.class.getPackage().getName());

    private final RuntimeEventsHandler runtimeEvents;
    private final RuntimeServicesHandler serviceManager;
    private final RuntimeStorageHandler storageHandler;

    private final Map<Long, Module> modules = new ConcurrentHashMap<Long, Module>();
    private final Map<String, Object> properties;

    public EmbeddedRuntime(Map<String, Object> props) {
        Map<String, Object> auxprops = new ConcurrentHashMap<String, Object>();
        if (props != null) {
            auxprops.putAll(props);
        }
        properties = Collections.unmodifiableMap(auxprops);
        runtimeEvents = new RuntimeEventsHandler(createExecutorService("RuntimeEvents"));
        serviceManager = new RuntimeServicesHandler(runtimeEvents);
        storageHandler = new RuntimeStorageHandler(properties, true);
    }

    @Override
    public Object getProperty(String key) {
        return properties.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A> A adapt(Class<A> type) {
        A result = null;
        if (type.isAssignableFrom(RuntimeEventsHandler.class)) {
            result = (A) runtimeEvents;
        } else if (type.isAssignableFrom(RuntimeServicesHandler.class)) {
            result = (A) serviceManager;
        } else if (type.isAssignableFrom(RuntimeStorageHandler.class)) {
            result = (A) storageHandler;
        }
        return result;
    }

    @Override
    public Module getModule(long id) {
        return modules.get(id);
    }

    @Override
    public Set<Module> getModules() {
        HashSet<Module> snapshot = new HashSet<Module>(modules.values());
        return Collections.unmodifiableSet(snapshot);
    }

    @Override
    public Module installModule(ClassLoader classLoader, Manifest manifest) {
        ModuleImpl module = new ModuleImpl(this, classLoader, manifest);
        return installModuleInternal(module);
    }

    @Override
    public Module installModule(ClassLoader classLoader, Resource resource) {
        ModuleImpl module = new ModuleImpl(this, classLoader, resource);
        return installModuleInternal(module);
    }

    private Module installModuleInternal(ModuleImpl module) {

        // #1 The module's state is set to {@code INSTALLED}.
        module.setState(State.INSTALLED);

        // #2 A module event of type {@link ModuleEvent#INSTALLED} is fired.
        runtimeEvents.fireModuleEvent(module, ModuleEvent.INSTALLED);

        // #3 The module's state is set to {@code RESOLVED}.
        module.setState(State.RESOLVED);

        // #4 A module event of type {@link ModuleEvent#RESOLVED} is fired.
        runtimeEvents.fireModuleEvent(module, ModuleEvent.RESOLVED);

        LOGGER.infof("Installed: %s", module);
        return module;
    }

    void uninstallModule(Module module) {
        modules.remove(module.getModuleId());
        LOGGER.infof("Uninstalled: %s", module);
    }

    private ExecutorService createExecutorService(final String threadName) {
        ExecutorService service = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable run) {
                Thread thread = new Thread(run);
                thread.setName(threadName);
                return thread;
            }
        });
        return service;
    }
}
