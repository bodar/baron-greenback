package com.googlecode.barongreenback.queues;

import com.googlecode.utterlyidle.Resources;
import com.googlecode.utterlyidle.modules.ApplicationScopedModule;
import com.googlecode.utterlyidle.modules.Module;
import com.googlecode.utterlyidle.modules.ResourcesModule;
import com.googlecode.utterlyidle.services.Services;
import com.googlecode.utterlyidle.services.ServicesModule;
import com.googlecode.yadic.Container;

import static com.googlecode.utterlyidle.annotations.AnnotatedBindings.annotatedClass;

public class QueuesModule implements ResourcesModule, ApplicationScopedModule {
    public Resources addResources(Resources resources) throws Exception {
        return resources.add(annotatedClass(QueuesResource.class));
    }

    public Container addPerApplicationObjects(Container container) throws Exception {
        return container.
                add(Queues.class, RequestQueues.class).
                add(Completer.class, CpuBoundedCompleter.class);
    }
}