package org.essupport.core;

import static org.essupport.core.SupportTypes.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

class SupportBoardTest {
    SupportBoard board;
    Actor leader = actor("ATTACK", true, false),
            driver = actor("ATTACK", false, false),
            commander = actor("ATTACK", false, true),
            enemy = actor("DEFEND", true, true);
    Radio radio =
            new Radio(UUID.randomUUID(), "前线电台", "ATTACK", "minecraft:overworld", 10, 64, 20, 0, 0);
    UUID truck = UUID.randomUUID();

    static Actor actor(String team, boolean leader, boolean commander) {
        return new Actor(UUID.randomUUID(), "测试", team, 1, leader, commander, true);
    }

    @BeforeEach
    void reset() {
        board = new SupportBoard(new Rules(4, 5000, 0, 1000));
        board.reset(123);
    }

    Order supply() {
        return board.createSupply(leader, radio, 100, 50, "补给", 0);
    }

    void denied(Runnable action) {
        assertThrows(SupportBoard.Denied.class, action::run);
    }

    @Test
    void onlyLeadersMayRequest() {
        denied(() -> board.createSupply(driver, radio, 1, 0, "", 0));
    }

    @Test
    void enemyRadioIsInaccessible() {
        denied(() -> board.createSupply(enemy, radio, 1, 0, "", 0));
    }

    @Test
    void quantitiesAreBounded() {
        for (int q : new int[] {-1, 5001, Integer.MAX_VALUE})
            denied(() -> board.createSupply(leader, radio, q, 0, "", 0));
        denied(() -> board.createSupply(leader, radio, 0, 0, "", 0));
    }

    @Test
    void oneActiveOrderPerRadio() {
        supply();
        denied(this::supply);
    }

    @Test
    void teamIsolation() {
        Order o = supply();
        assertTrue(board.visible(enemy).isEmpty());
        denied(() -> board.claim(o.id(), enemy, 1));
        denied(() -> board.cancel(o.id(), enemy, 1));
    }

    @Test
    void claimIsExclusive() {
        Order o = supply();
        board.claim(o.id(), driver, 1);
        denied(() -> board.claim(o.id(), leader, 2));
    }

    @Test
    void onlyAssigneeOrCommanderCanRelease() {
        Order o = supply();
        board.claim(o.id(), driver, 1);
        denied(() -> board.release(o.id(), leader, 2));
        board.release(o.id(), commander, 2);
        assertEquals(State.OPEN, board.get(o.id()).state());
    }

    @Test
    void realPartialDeliveryCompletesBothResources() {
        Order o = supply();
        board.claim(o.id(), driver, 1);
        board.transfer(truck, driver, null, Resource.AMMO, true, 100, 0, 2);
        board.transfer(truck, driver, null, Resource.MATERIALS, true, 50, 0, 2);
        board.transfer(truck, driver, radio.id(), Resource.AMMO, false, 40, 100, 3);
        assertEquals(40, board.get(o.id()).deliveredAmmo());
        board.transfer(truck, driver, radio.id(), Resource.AMMO, false, 60, 60, 4);
        assertEquals(State.CLAIMED, board.get(o.id()).state());
        board.transfer(truck, driver, radio.id(), Resource.MATERIALS, false, 50, 50, 5);
        assertEquals(State.COMPLETED, board.get(o.id()).state());
    }

    @Test
    void sameRadioReloadDoesNotCount() {
        Order o = supply();
        board.claim(o.id(), driver, 1);
        board.transfer(truck, driver, radio.id(), Resource.AMMO, true, 100, 0, 2);
        board.transfer(truck, driver, radio.id(), Resource.AMMO, false, 100, 100, 3);
        assertEquals(0, board.get(o.id()).deliveredAmmo());
    }

    @Test
    void unknownInitialCargoIsNotCredited() {
        Order o = supply();
        board.claim(o.id(), driver, 1);
        board.transfer(truck, driver, radio.id(), Resource.AMMO, false, 100, 100, 2);
        assertEquals(0, board.get(o.id()).deliveredAmmo());
    }

    @Test
    void otherDriverCannotCompleteClaim() {
        Order o = supply();
        board.claim(o.id(), driver, 1);
        board.transfer(truck, leader, null, Resource.AMMO, true, 100, 0, 2);
        board.transfer(truck, leader, radio.id(), Resource.AMMO, false, 100, 100, 3);
        assertEquals(0, board.get(o.id()).deliveredAmmo());
    }

    @Test
    void unloadElsewhereConsumesProvenance() {
        CargoLedger ledger = new CargoLedger();
        ledger.transfer(truck, Resource.AMMO, null, true, 100, 0);
        assertEquals(
                100, ledger.transfer(truck, Resource.AMMO, UUID.randomUUID(), false, 100, 100));
        assertEquals(0, ledger.transfer(truck, Resource.AMMO, radio.id(), false, 100, 100));
    }

    @Test
    void externallyConsumedCargoIsReconciled() {
        CargoLedger ledger = new CargoLedger();
        ledger.transfer(truck, Resource.AMMO, radio.id(), true, 50, 0);
        ledger.transfer(truck, Resource.AMMO, null, true, 50, 50);
        assertEquals(50, ledger.transfer(truck, Resource.AMMO, radio.id(), false, 50, 50));
    }

    @Test
    void offlineClaimIsReleasedAndMissingRadioCancelled() {
        Order o = supply();
        board.claim(o.id(), driver, 1);
        board.maintain(2, v -> true, v -> false);
        assertEquals(State.OPEN, board.get(o.id()).state());
        board.maintain(3, v -> false, v -> true);
        assertEquals(State.CANCELLED, board.get(o.id()).state());
    }

    @Test
    void roundEndClosesRequestsAndSessionResetErasesHistory() {
        Order o = supply();
        board.closeRound(4);
        assertEquals(State.CANCELLED, board.get(o.id()).state());
        board.reset(124);
        assertNull(board.get(o.id()));
        assertEquals(124, board.session());
    }

    @Test
    void creationIsRateLimited() {
        board = new SupportBoard(Rules.defaults());
        supply();
        denied(
                () ->
                        board.createSupply(
                                leader,
                                new Radio(
                                        UUID.randomUUID(),
                                        "其他电台",
                                        "ATTACK",
                                        "world",
                                        0,
                                        64,
                                        0,
                                        0,
                                        0),
                                1,
                                0,
                                "",
                                1));
    }

    @Test
    void terminalHistoryIsEvictedToBoundSnapshots() {
        for (int i = 0; i < 10; i++) {
            Order o = supply();
            board.cancel(o.id(), leader, i);
        }
        assertEquals(4, board.visible(leader).size());
    }

    @Test
    void noteCannotInjectFormattingOrControlCharacters() {
        String note = board.createSupply(leader, radio, 1, 0, "a\n§b" + "长".repeat(200), 0).note();
        assertEquals(96, note.length());
        assertFalse(note.contains("\n"));
        assertFalse(note.contains("§"));
    }
}
