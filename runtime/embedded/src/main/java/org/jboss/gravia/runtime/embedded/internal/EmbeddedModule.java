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


import static org.jboss.gravia.runtime.spi.RuntimeLogger.LOGGER;

import java.io.File;
import java.util.Dictionary;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.gravia.Constants;
import org.jboss.gravia.resource.AttachmentKey;
import org.jboss.gravia.resource.Resource;
import org.jboss.gravia.runtime.Module;
import org.jboss.gravia.runtime.ModuleActivator;
import org.jboss.gravia.runtime.ModuleContext;
import org.jboss.gravia.runtime.ModuleEvent;
import org.jboss.gravia.runtime.ModuleException;
import org.jboss.gravia.runtime.embedded.spi.BundleAdaptor;
import org.jboss.gravia.runtime.spi.AbstractModule;
import org.jboss.gravia.runtime.spi.AbstractRuntime;
import org.jboss.gravia.runtime.spi.RuntimeEventsManager;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;

/**
 * The embedded module
 *
 * @author thomas.diesler@jboss.com
 * @since 27-Sep-2013
 */
final class EmbeddedModule extends AbstractModule {

    private static AttachmentKey<ModuleActivator> MODULE_ACTIVATOR_KEY = AttachmentKey.create(ModuleActivator.class);
    private static final AtomicLong moduleIdGenerator = new AtomicLong();
    private static final Long START_STOP_TIMEOUT = new Long(10000);

    private final AtomicReference<State> stateRef = new AtomicReference<State>();
    private final AtomicReference<ModuleContext> contextRef = new AtomicReference<ModuleContext>();
    private final ReentrantLock startStopLock = new ReentrantLock();
    private final long moduleId;

    EmbeddedModule(AbstractRuntime runtime, ClassLoader classLoader, Resource resource, Dictionary<String, String> headers) {
        super(runtime, classLoader, resource, headers);
        this.moduleId = moduleIdGenerator.incrementAndGet();
        this.stateRef.set(State.UNINSTALLED);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A> A adapt(Class<A> type) {
        A result = super.adapt(type);
        if (result == null) {
            if (type.isAssignableFrom(EmbeddedRuntime.class)) {
                result = (A) getRuntime();
            }
        }
        return result;
    }

    @Override
    public long getModuleId() {
        return moduleId;
    }

    @Override
    public ModuleContext getModuleContext() {
        return contextRef.get();
    }

    private void createModuleContext() {
        contextRef.set(new EmbeddedModuleContext(this));
    }

    private void destroyModuleContext() {
        EmbeddedModuleContext context = (EmbeddedModuleContext) contextRef.get();
        if (context != null) {
            context.destroy();
        }
        contextRef.set(null);
    }

    @Override
    public State getState() {
        return stateRef.get();
    }

    @Override
    public void setState(State newState) {
        stateRef.set(newState);
    }

    @Override
    protected Bundle getBundleAdaptor(Module module) {
        return new BundleAdaptor(this);
    }

    @Override
    public void start() throws ModuleException {
        assertNotUninstalled();
        try {
            // #1 If this module is in the process of being activated or deactivated
            // then this method must wait for activation or deactivation to complete
            if (!startStopLock.tryLock(START_STOP_TIMEOUT, TimeUnit.MILLISECONDS))
                throw new ModuleException("Cannot aquire start lock for: " + this);

            // #2 If this module's state is {@code ACTIVE} then this method returns immediately.
            if (getState() == State.ACTIVE) {
                LOGGER.debug("Already active: {}", this);
                return;
            }

            // #3 This bundle's state is set to {@code STARTING}.
            setState(State.STARTING);

            // #4 A module event of type {@link ModuleEvent#STARTING} is fired.
            RuntimeEventsManager eventHandler = getRuntime().adapt(RuntimeEventsManager.class);
            eventHandler.fireModuleEvent(this, ModuleEvent.STARTING);

            // Create the {@link ModuleContext}
            createModuleContext();

            // #5 The {@link ModuleActivator#start(ModuleContext)} method if one is specified, is called.
            try {
                Boolean graviaEnabled = Boolean.parseBoolean(getHeaders().get(Constants.GRAVIA_ENABLED));
                String moduleActivatorName = getHeaders().get(Constants.MODULE_ACTIVATOR);
                String bundleActivatorName = getHeaders().get("Bundle-Activator");
                if (moduleActivatorName != null || bundleActivatorName != null) {
                    ModuleActivator moduleActivator;
                    synchronized (MODULE_ACTIVATOR_KEY) {
                        moduleActivator = getAttachment(MODULE_ACTIVATOR_KEY);
                        if (moduleActivator == null) {
                            if (moduleActivatorName != null) {
                                Object result = loadClass(moduleActivatorName).newInstance();
                                moduleActivator = (ModuleActivator) result;
                                putAttachment(MODULE_ACTIVATOR_KEY, moduleActivator);
                            } else if (bundleActivatorName != null && graviaEnabled) {
                                Object result = loadClass(bundleActivatorName).newInstance();
                                moduleActivator = new ModuleActivatorBridge((BundleActivator) result);
                                putAttachment(MODULE_ACTIVATOR_KEY, moduleActivator);
                            }
                        }
                    }
                    if (moduleActivator != null) {
                        moduleActivator.start(getModuleContext());
                    }
                }
            }

            // If the {@code ModuleActivator} is invalid or throws an exception then:
            catch (Throwable th) {

                // This module's state is set to {@code STOPPING}.
                setState(State.STOPPING);

                // A module event of type {@link BundleEvent#STOPPING} is fired.
                eventHandler.fireModuleEvent(this, ModuleEvent.STOPPING);

                // [TODO] Any services registered by this module must be unregistered.
                // [TODO] Any services used by this module must be released.
                // [TODO] Any listeners registered by this module must be removed.

                // This module's state is set to {@code INSTALLED}.
                setState(State.INSTALLED);

                // A module event of type {@link BundleEvent#STOPPED} is fired.
                eventHandler.fireModuleEvent(this, ModuleEvent.STOPPED);

                // Destroy the {@link ModuleContext}
                destroyModuleContext();

                // A {@code ModuleException} is then thrown.
                throw new ModuleException("Cannot start module: " + this, th);
            }

            // #6 This bundle's state is set to {@code ACTIVE}.
            setState(State.ACTIVE);

            // #7 A module event of type {@link ModuleEvent#STARTED} is fired.
            eventHandler.fireModuleEvent(this, ModuleEvent.STARTED);

            LOGGER.info("Started: {}", this);
        } catch (InterruptedException ex) {
            throw ModuleException.launderThrowable(ex);
        } finally {
            startStopLock.unlock();
        }
    }

    @Override
    public void stop() throws ModuleException {
        assertNotUninstalled();
        try {
            // #1 If this module is in the process of being activated or deactivated
            // then this method must wait for activation or deactivation to complete
            if (!startStopLock.tryLock(START_STOP_TIMEOUT, TimeUnit.MILLISECONDS))
                throw new ModuleException("Cannot aquire stop lock for: " + this);

            // #2 If this module's state is not {@code ACTIVE} then this method returns immediately
            if (getState() != State.ACTIVE) {
                return;
            }

            // #3 This module's state is set to {@code STOPPING}
            setState(State.STOPPING);

            // #4 A module event of type {@link ModuleEvent#STOPPING} is fired.
            RuntimeEventsManager eventHandler = getRuntime().adapt(RuntimeEventsManager.class);
            eventHandler.fireModuleEvent(this, ModuleEvent.STOPPING);

            // #5 The {@link ModuleActivator#stop(ModuleContext)} is called
            Throwable stopException = null;
            try {
                ModuleActivator moduleActivator = getAttachment(MODULE_ACTIVATOR_KEY);
                if (moduleActivator != null) {
                    moduleActivator.stop(getModuleContext());
                }
            } catch (Throwable th) {
                stopException = th;
            }

            // #6 [TODO] Any services registered by this module must be unregistered.
            // #7 [TODO] Any services used by this module must be released.
            // #8 [TODO] Any listeners registered by this module must be removed.

            // #9 This module's state is set to {@code INSTALLED}.
            setState(State.INSTALLED);

            // #10 A module event of type {@link ModuleEvent#STOPPED} is fired.
            eventHandler.fireModuleEvent(this, ModuleEvent.STOPPED);

            // Destroy the {@link ModuleContext}
            destroyModuleContext();

            if (stopException != null)
                throw new ModuleException("Cannot stop module: " + this, stopException);

            LOGGER.info("Stopped: {}", this);

        } catch (InterruptedException ex) {
            throw ModuleException.launderThrowable(ex);
        } finally {
            startStopLock.unlock();
        }
    }

    @Override
    public void uninstall() {
        assertNotUninstalled();

        // #1 This module is stopped as described in the {@code Module.stop} method.
        try {
            stop();
        } catch (Exception ex) {
            LOGGER.error("Cannot stop module on uninstall: " + this, ex);
        }

        // #2 This bundle's state is set to {@code UNINSTALLED}.
        setState(State.UNINSTALLED);

        // #3 A module event of type {@link ModuleEvent#UNINSTALLED} is fired.
        RuntimeEventsManager eventHandler = getRuntime().adapt(RuntimeEventsManager.class);
        eventHandler.fireModuleEvent(this, ModuleEvent.UNINSTALLED);

        getRuntime().uninstallModule(this);
    }

    @Override
    public File getDataFile(String filename) {
        assertNotUninstalled();
        RuntimeStorageHandler storageHandler = getStorageHandler();
        return storageHandler.getDataFile(this, filename);
    }

    @Override
    protected EmbeddedRuntime getRuntime() {
        return (EmbeddedRuntime) super.getRuntime();
    }

    private RuntimeStorageHandler getStorageHandler() {
        AbstractRuntime runtime = adapt(AbstractRuntime.class);
        return runtime.adapt(RuntimeStorageHandler.class);
    }
}
