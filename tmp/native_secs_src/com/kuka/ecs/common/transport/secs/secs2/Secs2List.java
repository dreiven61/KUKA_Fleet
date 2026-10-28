/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.secs2;

import com.kuka.ecs.common.transport.secs.secs2.AbstractSecs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BuildException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2BytesPackBuilder;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import com.kuka.ecs.common.transport.secs.secs2.Secs2IndexOutOfBoundsException;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Item;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Secs2List
extends AbstractSecs2 {
    private static final long serialVersionUID = -2380901931225887256L;
    private static final Secs2Item secs2Item = Secs2Item.LIST;
    private static final String SPACE = "  ";
    private static final String BR = System.lineSeparator();
    private final List<Secs2> values;
    private String proxyToString;
    private String proxyToJson;

    public Secs2List() {
        this.values = Collections.emptyList();
        this.proxyToString = null;
        this.proxyToJson = null;
    }

    public Secs2List(Secs2 ... values) {
        this.values = Stream.of(values).collect(Collectors.toList());
        this.proxyToString = null;
        this.proxyToJson = null;
    }

    public Secs2List(List<? extends Secs2> values) {
        this.values = new ArrayList<Secs2>(values);
        this.proxyToString = null;
        this.proxyToJson = null;
    }

    @Override
    public int size() {
        return this.values.size();
    }

    @Override
    public Iterator<Secs2> iterator() {
        return this.values.iterator();
    }

    @Override
    public Stream<Secs2> stream() {
        return this.values.stream();
    }

    @Override
    protected void putBytesPack(Secs2BytesPackBuilder builder) throws Secs2BuildException {
        this.putHeaderBytesToBytesPack(builder, this.size());
        for (Secs2 ss : this.values) {
            if (ss instanceof AbstractSecs2) {
                ((AbstractSecs2)ss).putBytesPack(builder);
                continue;
            }
            throw new Secs2BuildException("cast failed");
        }
    }

    @Override
    protected AbstractSecs2 get(LinkedList<Integer> list) throws Secs2Exception {
        if (list.isEmpty()) {
            return this;
        }
        try {
            int index = list.removeFirst();
            Secs2 ss = this.values.get(index);
            if (ss instanceof AbstractSecs2) {
                return ((AbstractSecs2)ss).get(list);
            }
            throw new Secs2Exception("cast failed");
        }
        catch (IndexOutOfBoundsException e) {
            throw new Secs2IndexOutOfBoundsException(e);
        }
    }

    @Override
    public Secs2Item secs2Item() {
        return secs2Item;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public String toString() {
        Secs2List secs2List = this;
        synchronized (secs2List) {
            if (this.proxyToString == null) {
                this.proxyToString = this.lineStrings("").stream().collect(Collectors.joining(BR));
            }
            return this.proxyToString;
        }
    }

    private List<String> lineStrings(String space) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(space + "<" + this.secs2Item().symbol() + " [" + this.size() + "]");
        this.values.stream().map(s -> {
            String ss = space + SPACE;
            if (s instanceof Secs2List) {
                return ((Secs2List)s).lineStrings(ss).stream().collect(Collectors.joining(BR));
            }
            return ss + s.toString();
        }).forEach(lines::add);
        lines.add(space + ">");
        return lines;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public String toJson() {
        Secs2List secs2List = this;
        synchronized (secs2List) {
            if (this.proxyToJson == null) {
                this.proxyToJson = super.toJson();
            }
            return this.proxyToJson;
        }
    }

    @Override
    protected String toJsonValue() {
        return this.stream().map(ss -> ss.toJson()).collect(Collectors.joining(",", "[", "]"));
    }

    @Override
    protected String toStringValue() {
        return "";
    }
}

