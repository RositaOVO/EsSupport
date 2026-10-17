package org.essupport.network;

import org.essupport.core.SupportTypes.*;

import java.util.List;

public record SupportSnapshot(
        long session,
        long revision,
        long tick,
        Actor actor,
        boolean battle,
        Bounds bounds,
        List<Radio> radios,
        List<Order> orders,
        String message) {}
