/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.AbstractSecsLog;
import com.kuka.ecs.common.transport.secs.SecsThrowableLog;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

public abstract class AbstractSecsThrowableLog
extends AbstractSecsLog
implements SecsThrowableLog {
    private static final long serialVersionUID = -7109145044702257924L;
    private static final String commonSubject = "Throwable";
    private final Throwable cause;
    private String cacheToSubject;
    private String cacheToStringValue;

    public AbstractSecsThrowableLog(Throwable cause) {
        super((CharSequence)commonSubject, Objects.requireNonNull(cause));
        this.cause = cause;
        this.cacheToSubject = null;
        this.cacheToStringValue = null;
    }

    public AbstractSecsThrowableLog(Throwable cause, LocalDateTime timestamp) {
        super(commonSubject, timestamp, Objects.requireNonNull(cause));
        this.cause = cause;
        this.cacheToSubject = null;
        this.cacheToStringValue = null;
    }

    @Override
    public Throwable getCause() {
        return this.cause;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public String subject() {
        AbstractSecsThrowableLog abstractSecsThrowableLog = this;
        synchronized (abstractSecsThrowableLog) {
            if (this.cacheToSubject == null) {
                this.cacheToSubject = this.cause.toString();
            }
            return this.cacheToSubject;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public Optional<String> optionalValueString() {
        AbstractSecsThrowableLog abstractSecsThrowableLog = this;
        synchronized (abstractSecsThrowableLog) {
            if (this.cacheToStringValue == null) {
                try (StringWriter sw = new StringWriter();
                     PrintWriter pw = new PrintWriter(sw);){
                    this.cause.printStackTrace(pw);
                    pw.flush();
                    this.cacheToStringValue = sw.toString();
                }
                catch (IOException e) {
                    this.cacheToStringValue = "THROWABLE-PARSE-FAILED";
                }
            }
            return Optional.of(this.cacheToStringValue);
        }
    }
}

