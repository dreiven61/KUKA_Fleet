/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.AbstractSecsLog;
import com.kuka.ecs.common.transport.secs.secs1.Secs1MessageBlock;
import java.time.LocalDateTime;

public class Secs1ReceiveMessageBlockLog
extends AbstractSecsLog {
    private static final long serialVersionUID = -1852819059794948858L;
    private static final String commonSubject = "Receive SECS1-Message-Block";
    private final Secs1MessageBlock block;

    public Secs1ReceiveMessageBlockLog(Secs1MessageBlock block, LocalDateTime timestamp) {
        super(commonSubject, timestamp, block);
        this.block = block;
    }

    public Secs1ReceiveMessageBlockLog(Secs1MessageBlock block) {
        super((CharSequence)commonSubject, block);
        this.block = block;
    }

    public Secs1MessageBlock messageBlock() {
        return this.block;
    }
}

