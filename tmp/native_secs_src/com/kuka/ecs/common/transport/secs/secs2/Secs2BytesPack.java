/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Secs2BytesPack {
    private final List<byte[]> bytes;
    private long cacheSize;

    public Secs2BytesPack(List<byte[]> bytes) {
        this.bytes = new ArrayList<byte[]>(bytes);
        this.cacheSize = -1L;
    }

    public List<byte[]> getBytes() {
        return Collections.unmodifiableList(this.bytes);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public long size() {
        Secs2BytesPack secs2BytesPack = this;
        synchronized (secs2BytesPack) {
            if (this.cacheSize < 0L) {
                this.cacheSize = this.bytes.stream().mapToLong(bs -> ((byte[])bs).length).sum();
            }
            return this.cacheSize;
        }
    }
}

