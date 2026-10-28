/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import java.io.Closeable;
import java.io.IOException;

public interface OpenAndCloseable
extends Closeable {
    public void open() throws IOException;

    public boolean isOpen();

    public boolean isClosed();
}

