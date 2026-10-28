/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import java.io.Serializable;

public abstract class AbstractSecsMessage
implements SecsMessage,
Serializable {
    private static final long serialVersionUID = 6003913058174391972L;
    private final Object sync = new Object();
    private Integer cacheSystemBytesKey = null;
    private String cacheToJson = null;
    private String cacheToHeaderBytesString = null;
    private String cacheToString = null;

    protected AbstractSecsMessage() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Integer systemBytesKey() {
        Object object = this.sync;
        synchronized (object) {
            if (this.cacheSystemBytesKey == null) {
                byte[] bs = this.header10Bytes();
                int i = bs[6] << 24 & 0xFF000000;
                i |= bs[7] << 16 & 0xFF0000;
                i |= bs[8] << 8 & 0xFF00;
                this.cacheSystemBytesKey = i |= bs[9] & 0xFF;
            }
            return this.cacheSystemBytesKey;
        }
    }

    protected abstract String toJsonProxy();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public String toJson() {
        Object object = this.sync;
        synchronized (object) {
            if (this.cacheToJson == null) {
                this.cacheToJson = this.toJsonProxy();
            }
            return this.cacheToJson;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    protected String toHeaderBytesString() {
        Object object = this.sync;
        synchronized (object) {
            if (this.cacheToHeaderBytesString == null) {
                byte[] bs = this.header10Bytes();
                this.cacheToHeaderBytesString = "[" + String.format("%02X", bs[0]) + " " + String.format("%02X", bs[1]) + "|" + String.format("%02X", bs[2]) + " " + String.format("%02X", bs[3]) + "|" + String.format("%02X", bs[4]) + " " + String.format("%02X", bs[5]) + "|" + String.format("%02X", bs[6]) + " " + String.format("%02X", bs[7]) + " " + String.format("%02X", bs[8]) + " " + String.format("%02X", bs[9]) + "]";
            }
            return this.cacheToHeaderBytesString;
        }
    }

    protected abstract String toStringProxy();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public String toString() {
        Object object = this.sync;
        synchronized (object) {
            if (this.cacheToString == null) {
                this.cacheToString = this.toStringProxy();
            }
            return this.cacheToString;
        }
    }
}

