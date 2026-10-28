/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs;

import com.kuka.ecs.common.transport.secs.SecsMessage;
import java.util.Optional;

public class SecsException
extends Exception {
    private static final long serialVersionUID = -3938886949828082098L;
    private final SecsMessage secsMsg;

    public SecsException() {
        this.secsMsg = null;
    }

    public SecsException(String message) {
        super(message);
        this.secsMsg = null;
    }

    public SecsException(Throwable cause) {
        super(cause);
        this.secsMsg = null;
    }

    public SecsException(String message, Throwable cause) {
        super(message, cause);
        this.secsMsg = null;
    }

    public SecsException(SecsMessage secsMessage) {
        super(SecsException.createMessage(secsMessage));
        this.secsMsg = secsMessage;
    }

    public SecsException(SecsMessage secsMessage, Throwable cause) {
        super(SecsException.createMessage(secsMessage), cause);
        this.secsMsg = secsMessage;
    }

    private static String createMessage(SecsMessage msg) {
        byte[] bs = msg.header10Bytes();
        return "[" + SecsException.s02X(bs[0]) + " " + SecsException.s02X(bs[1]) + "|" + SecsException.s02X(bs[2]) + " " + SecsException.s02X(bs[3]) + "|" + SecsException.s02X(bs[4]) + " " + SecsException.s02X(bs[5]) + "|" + SecsException.s02X(bs[6]) + " " + SecsException.s02X(bs[7]) + " " + SecsException.s02X(bs[8]) + " " + SecsException.s02X(bs[9]) + "]";
    }

    private static String s02X(byte b) {
        return String.format("%02X", b);
    }

    public Optional<SecsMessage> secsMessage() {
        return this.secsMsg == null ? Optional.empty() : Optional.of(this.secsMsg);
    }
}

