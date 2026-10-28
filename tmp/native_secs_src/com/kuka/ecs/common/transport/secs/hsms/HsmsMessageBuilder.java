/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsControlMessage;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsDataMessage;
import com.kuka.ecs.common.transport.secs.hsms.AbstractHsmsSession;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageDeselectStatus;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageRejectReason;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessageSelectStatus;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;

public interface HsmsMessageBuilder {
    public AbstractHsmsControlMessage buildSelectRequest(AbstractHsmsSession var1);

    public AbstractHsmsControlMessage buildSelectResponse(HsmsMessage var1, HsmsMessageSelectStatus var2);

    public AbstractHsmsControlMessage buildDeselectRequest(AbstractHsmsSession var1);

    public AbstractHsmsControlMessage buildDeselectResponse(HsmsMessage var1, HsmsMessageDeselectStatus var2);

    public AbstractHsmsControlMessage buildLinktestRequest(AbstractHsmsSession var1);

    public AbstractHsmsControlMessage buildLinktestResponse(HsmsMessage var1);

    public AbstractHsmsControlMessage buildRejectRequest(HsmsMessage var1, HsmsMessageRejectReason var2);

    public AbstractHsmsControlMessage buildSeparateRequest(AbstractHsmsSession var1);

    public AbstractHsmsDataMessage buildDataMessage(AbstractHsmsSession var1, int var2, int var3, boolean var4);

    public AbstractHsmsDataMessage buildDataMessage(AbstractHsmsSession var1, int var2, int var3, boolean var4, Secs2 var5);

    public AbstractHsmsDataMessage buildDataMessage(SecsMessage var1, int var2, int var3, boolean var4);

    public AbstractHsmsDataMessage buildDataMessage(SecsMessage var1, int var2, int var3, boolean var4, Secs2 var5);
}

