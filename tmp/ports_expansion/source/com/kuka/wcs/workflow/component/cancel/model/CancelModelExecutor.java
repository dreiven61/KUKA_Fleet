/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.kuka.wcs.common.entity.workflow.request.CancelRequestContext
 *  com.kuka.wcs.workflow.component.cancel.model.CancelModelFactory
 *  org.springframework.stereotype.Component
 */
package com.kuka.wcs.workflow.component.cancel.model;

import com.kuka.wcs.common.entity.workflow.request.CancelRequestContext;
import com.kuka.wcs.workflow.component.cancel.model.CancelModelFactory;
import org.springframework.stereotype.Component;

@Component
public class CancelModelExecutor {
    public void execute(CancelRequestContext context) {
        CancelModelFactory.getHandler((CancelRequestContext)context).handle(context);
    }
}
