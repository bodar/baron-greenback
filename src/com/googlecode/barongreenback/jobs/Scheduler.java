package com.googlecode.barongreenback.jobs;

import com.googlecode.utterlyidle.Response;

import java.util.UUID;
import java.util.concurrent.Callable;

public interface Scheduler {
    Job schedule(UUID id, Callable<?> command, long numberOfSeconds);

    void cancel(UUID id);
}
