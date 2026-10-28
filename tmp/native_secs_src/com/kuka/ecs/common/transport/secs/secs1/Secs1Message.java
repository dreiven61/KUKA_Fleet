/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs1;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.secs1.Secs1MessageBlock;
import java.util.List;

public interface Secs1Message
extends SecsMessage {
    public boolean rbit();

    public List<Secs1MessageBlock> toBlocks();
}

