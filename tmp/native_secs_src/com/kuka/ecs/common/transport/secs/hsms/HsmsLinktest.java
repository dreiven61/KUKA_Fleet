/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.hsms.HsmsException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSendMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;

public interface HsmsLinktest {
    public void testing() throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException;

    public void resetTimer();
}

