/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.hsms;

import com.kuka.ecs.common.transport.secs.PropertyChangeListener;
import com.kuka.ecs.common.transport.secs.ReadOnlyTimeProperty;
import com.kuka.ecs.common.transport.secs.hsms.HsmsException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsLinktest;
import com.kuka.ecs.common.transport.secs.hsms.HsmsMessage;
import com.kuka.ecs.common.transport.secs.hsms.HsmsSendMessageException;
import com.kuka.ecs.common.transport.secs.hsms.HsmsWaitReplyMessageException;
import java.util.Optional;

public abstract class AbstractHsmsLinktest
implements HsmsLinktest {
    private final Object sync = new Object();
    private final PropertyChangeListener<Number> lstnr = n -> {
        Object object = this.sync;
        synchronized (object) {
            this.sync.notifyAll();
        }
    };
    private boolean resetted = false;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void testing() throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException {
        try {
            this.timer().addChangeListener(this.lstnr);
            while (true) {
                Object object;
                if (this.timer().gtZero()) {
                    object = this.sync;
                    synchronized (object) {
                        this.resetted = false;
                        this.timer().wait(this.sync);
                        if (this.resetted) {
                            continue;
                        }
                    }
                    if (!this.send().isPresent()) {
                        throw new HsmsException();
                    }
                } else {
                    object = this.sync;
                    synchronized (object) {
                        this.sync.wait();
                    }
                }
                this.resetted = false;
            }
        }
        catch (Throwable throwable) {
            this.timer().removeChangeListener(this.lstnr);
            throw throwable;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void resetTimer() {
        Object object = this.sync;
        synchronized (object) {
            this.resetted = true;
            this.sync.notifyAll();
        }
    }

    protected abstract ReadOnlyTimeProperty timer();

    protected abstract Optional<HsmsMessage> send() throws HsmsSendMessageException, HsmsWaitReplyMessageException, HsmsException, InterruptedException;
}

