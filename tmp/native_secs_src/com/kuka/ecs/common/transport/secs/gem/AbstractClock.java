/*
 * Decompiled with CFR 0.152.
 */
package com.kuka.ecs.common.transport.secs.gem;

import com.kuka.ecs.common.transport.secs.gem.Clock;
import com.kuka.ecs.common.transport.secs.secs2.Secs2;
import com.kuka.ecs.common.transport.secs.secs2.Secs2Exception;
import java.io.Serializable;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public abstract class AbstractClock
implements Clock,
Serializable {
    private static final long serialVersionUID = 7004605301054564451L;
    private static final int century;
    private static final int flacYear;
    private final LocalDateTime dt;
    private Secs2 a12;
    private Secs2 a16;
    private static DateTimeFormatter A14;

    public AbstractClock(LocalDateTime ldt) {
        this.dt = ldt;
        this.a12 = null;
        this.a16 = null;
    }

    public static AbstractClock from(LocalDateTime ldt) {
        return new AbstractClock(ldt){
            private static final long serialVersionUID = 5391862507203354279L;
        };
    }

    public static AbstractClock now() {
        return AbstractClock.from(LocalDateTime.now());
    }

    public static AbstractClock from(Secs2 secs2) throws Secs2Exception {
        String a = secs2.getAscii();
        int len = a.length();
        try {
            if (len == 12) {
                int yyyy = AbstractClock.getYear(a.substring(0, 2));
                int mm = Integer.valueOf(a.substring(2, 4));
                int dd = Integer.valueOf(a.substring(4, 6));
                int hh = Integer.valueOf(a.substring(6, 8));
                int ii = Integer.valueOf(a.substring(8, 10));
                int ss = Integer.valueOf(a.substring(10, 12));
                AbstractClock c = AbstractClock.from(LocalDateTime.of(yyyy, mm, dd, hh, ii, ss));
                c.a12 = secs2;
                return c;
            }
            if (len == 16) {
                int yyyy = Integer.valueOf(a.substring(0, 4));
                int mm = Integer.valueOf(a.substring(4, 6));
                int dd = Integer.valueOf(a.substring(6, 8));
                int hh = Integer.valueOf(a.substring(8, 10));
                int ii = Integer.valueOf(a.substring(10, 12));
                int ss = Integer.valueOf(a.substring(12, 14));
                int sss = Integer.valueOf(a.substring(14, 16)) * 10000000;
                AbstractClock c = AbstractClock.from(LocalDateTime.of(yyyy, mm, dd, hh, ii, ss, sss));
                c.a16 = secs2;
                return c;
            }
            throw new Secs2Exception("Parse Failed \"" + a + "\"");
        }
        catch (NumberFormatException | DateTimeException e) {
            throw new Secs2Exception(e);
        }
    }

    private static int getYear(String a2) {
        int yy = Integer.parseInt(a2);
        if (flacYear < 25) {
            if (yy >= 75) {
                return century - 100 + yy;
            }
        } else if (flacYear >= 75 && yy < 25) {
            return century + 100 + yy;
        }
        return century + yy;
    }

    @Override
    public LocalDateTime toLocalDateTime() {
        return this.dt;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public Secs2 toAscii12() {
        AbstractClock abstractClock = this;
        synchronized (abstractClock) {
            if (this.a12 == null) {
                String tt = this.dt.format(A14).substring(2, 14);
                this.a12 = Secs2.ascii(tt);
            }
            return this.a12;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public Secs2 toAscii16() {
        AbstractClock abstractClock = this;
        synchronized (abstractClock) {
            if (this.a16 == null) {
                String tt = this.dt.format(A14);
                String ss = String.format("%02d", this.dt.getNano() / 10000000);
                this.a16 = Secs2.ascii(tt + ss);
            }
            return this.a16;
        }
    }

    static {
        int nowYear = LocalDateTime.now().getYear();
        century = nowYear / 100 * 100;
        flacYear = nowYear % 100;
        A14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    }
}

