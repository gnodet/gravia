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
package org.jboss.gravia.provision.spi;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.gravia.provision.Environment;
import org.jboss.gravia.resource.Capability;
import org.jboss.gravia.resource.DefaultResourceStore;
import org.jboss.gravia.resource.Requirement;
import org.jboss.gravia.resource.Resource;
import org.jboss.gravia.resource.ResourceIdentity;
import org.jboss.gravia.resource.ResourceStore;
import org.jboss.gravia.resource.spi.AbstractResourceStore;
import org.jboss.gravia.runtime.Module;
import org.jboss.gravia.runtime.ModuleContext;
import org.jboss.gravia.runtime.ModuleEvent;
import org.jboss.gravia.runtime.ModuleListener;
import org.jboss.gravia.runtime.Runtime;
import org.jboss.gravia.runtime.SynchronousModuleListener;
import org.jboss.gravia.utils.NotNullException;

/**
 * An {@link Environment} that maintains the set of runtime resources.
 *
 * @author thomas.diesler@jboss.com
 * @since 07-Jan-2014
 */
public class RuntimeEnvironment implements Environment {

    private final RuntimeStore runtimeStore;
    private final ResourceStore systemStore;

    public RuntimeEnvironment(Runtime runtime) {
        this(runtime, new DefaultResourceStore("SystemResourceStore"));
    }

    public RuntimeEnvironment(Runtime runtime, ResourceStore systemStore) {
        NotNullException.assertValue(runtime, "runtime");
        NotNullException.assertValue(systemStore, "systemStore");
        this.systemStore = systemStore;
        this.runtimeStore = new RuntimeStore(runtime);
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    public ResourceStore getSystemStore() {
        return systemStore;
    }

    public RuntimeStore getRuntimeStore() {
        return runtimeStore;
    }

    @Override
    public Resource getResource(ResourceIdentity identity) {
        Resource resource = systemStore.getResource(identity);
        if (resource == null) {
            resource = runtimeStore.getResource(identity);
        }
        return resource;
    }

    @Override
    public Set<Capability> findProviders(Requirement requirement) {
        Set<Capability> result = new LinkedHashSet<Capability>();
        result.addAll(systemStore.findProviders(requirement));
        result.addAll(runtimeStore.findProviders(requirement));
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Environment cloneEnvironment() {
        return new ClonedRuntimeEnvironment("Cloned " + getName(), runtimeStore, systemStore);
    }

    @Override
    public Iterator<Resource> getResources() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Resource addResource(Resource resource) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Resource removeResource(ResourceIdentity identity) {
        throw new UnsupportedOperationException();
    }

    static class RuntimeStore extends AbstractResourceStore {

        public RuntimeStore(Runtime runtime) {
            super(RuntimeStore.class.getSimpleName());

            // Add the initial set of modules
            for (Module module : runtime.getModules()) {
                addModuleResource(module.adapt(Resource.class));
            }

            // Track installed/uninstalled modules
            ModuleListener listener = new SynchronousModuleListener() {
                @Override
                public void moduleChanged(ModuleEvent event) {
                    Module module = event.getModule();
                    if (event.getType() == ModuleEvent.INSTALLED) {
                        addModuleResource(module.adapt(Resource.class));
                    } else if (event.getType() == ModuleEvent.UNINSTALLED) {
                        removeModuleResource(module.getIdentity());
                    }
                }
            };
            ModuleContext syscontext = runtime.getModuleContext();
            syscontext.addModuleListener(listener);
        }

        private Resource addModuleResource(Resource resource) {
            return super.addResource(resource);
        }

        private Resource removeModuleResource(ResourceIdentity identity) {
            return super.removeResource(identity);
        }
    }

    static class ClonedRuntimeEnvironment implements Environment {

        private final String name;
        private final ResourceStore snapshotStore;
        private final ResourceStore systemStore;

        ClonedRuntimeEnvironment(String name, ResourceStore runtimeStore, ResourceStore systemStore) {
            this.name = name;
            this.systemStore = systemStore;
            this.snapshotStore = new DefaultResourceStore("Cloned " + runtimeStore.getName());
            Iterator<Resource> itres = runtimeStore.getResources();
            while (itres.hasNext()) {
                snapshotStore.addResource(itres.next());
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Iterator<Resource> getResources() {
            return snapshotStore.getResources();
        }

        @Override
        public Resource addResource(Resource resource) {
            return snapshotStore.addResource(resource);
        }

        @Override
        public Resource removeResource(ResourceIdentity identity) {
            return snapshotStore.removeResource(identity);
        }

        @Override
        public Environment cloneEnvironment() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Resource getResource(ResourceIdentity identity) {
            Resource resource = systemStore.getResource(identity);
            if (resource == null) {
                resource = snapshotStore.getResource(identity);
            }
            return resource;
        }

        @Override
        public Set<Capability> findProviders(Requirement requirement) {
            Set<Capability> result = new LinkedHashSet<Capability>();
            result.addAll(systemStore.findProviders(requirement));
            result.addAll(snapshotStore.findProviders(requirement));
            return Collections.unmodifiableSet(result);
        }
    }
}
