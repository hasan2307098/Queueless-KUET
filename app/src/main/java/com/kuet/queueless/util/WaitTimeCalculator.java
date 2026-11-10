package com.kuet.queueless.util;

import com.kuet.queueless.model.Token;
import com.kuet.queueless.model.TokenStatus;

import java.util.List;

/** Waiting-time estimation: people ahead x average service time (an approximation, not a guarantee). */
public final class WaitTimeCalculator {
    private WaitTimeCalculator() {}

    /** The person currently being served counts as half a slot (they are partly done). */
    public static int estimateMinutes(int peopleAhead, boolean someoneBeingServed, double avgMinutes) {
        double slots = peopleAhead + (someoneBeingServed ? 0.5 : 0.0);
        return (int) Math.ceil(slots * avgMinutes);
    }

    public static String format(int estimateMinutes) {
        if (estimateMinutes <= 0) return "You're next";
        int lo = (int) Math.floor(estimateMinutes * 0.7);
        int hi = (int) Math.ceil(estimateMinutes * 1.3);
        if (lo < 1) lo = 1;
        return "About " + lo + "\u2013" + hi + " min (approximate)";
    }

    public static int countAhead(List<Token> queue, Token mine) {
        int ahead = 0;
        for (Token t : queue) {
            if (t.statusEnum() == TokenStatus.WAITING && !t.id.equals(mine.id) && t.isBefore(mine)) ahead++;
        }
        return ahead;
    }

    public static boolean isServing(List<Token> queue) {
        for (Token t : queue) {
            if (t.statusEnum() == TokenStatus.CALLED) return true;
        }
        return false;
    }
}
