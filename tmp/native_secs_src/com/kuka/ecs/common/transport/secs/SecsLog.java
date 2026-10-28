/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SecsLog {
    public String subject();

    public LocalDateTime timestamp();

    public Optional<Object> value();

    public String subjectHeader();

    public Optional<String> optionalValueString();
}

