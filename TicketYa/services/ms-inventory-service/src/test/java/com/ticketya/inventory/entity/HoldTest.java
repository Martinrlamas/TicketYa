package com.ticketya.inventory.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketya.inventory.exception.InventoryException;
import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 3.1 Reglas de transición del dominio (sin Spring ni base). */
class HoldTest {

    private static Hold hold(HoldStatus status, Instant expiresAt) throws Exception {
        var ctor = Hold.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Hold h = ctor.newInstance();
        set(h, "status", status);
        set(h, "expiresAt", expiresAt);
        return h;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void activeHoldCanBeConfirmedReleasedOrExpired() throws Exception {
        Hold a = hold(HoldStatus.ACTIVE, Instant.now().plusSeconds(60));
        a.confirm();
        assertThat(a.getStatus()).isEqualTo(HoldStatus.CONFIRMED);

        Hold b = hold(HoldStatus.ACTIVE, Instant.now().plusSeconds(60));
        b.release();
        assertThat(b.getStatus()).isEqualTo(HoldStatus.RELEASED);

        Hold c = hold(HoldStatus.ACTIVE, Instant.now().plusSeconds(60));
        c.expire();
        assertThat(c.getStatus()).isEqualTo(HoldStatus.EXPIRED);
    }

    @Test
    void nonActiveHoldRejectsTransitions() throws Exception {
        for (HoldStatus s : new HoldStatus[] {HoldStatus.CONFIRMED, HoldStatus.RELEASED, HoldStatus.EXPIRED}) {
            Hold h = hold(s, Instant.now().plusSeconds(60));
            assertThatThrownBy(h::confirm).isInstanceOf(InventoryException.class)
                    .extracting("code").isEqualTo("INVALID_STATE_TRANSITION");
            assertThatThrownBy(h::release).isInstanceOf(InventoryException.class);
            assertThatThrownBy(h::expire).isInstanceOf(InventoryException.class);
        }
    }

    @Test
    void isExpiredComparesAgainstGivenInstant() throws Exception {
        Instant exp = Instant.parse("2026-11-07T15:40:00Z");
        Hold h = hold(HoldStatus.ACTIVE, exp);
        assertThat(h.isExpired(exp.minusSeconds(1))).isFalse();
        assertThat(h.isExpired(exp)).isTrue();
        assertThat(h.isExpired(exp.plusSeconds(1))).isTrue();
    }
}
