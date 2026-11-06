package cn.todayeat.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MealLogicTest {
    private Dish dish(long id, int meal, boolean enabled) { return new Dish(id, "饭菜" + id, "", "", null, meal, enabled); }
    @Test public void mealAndEnabledFilters() {
        List<Dish> all = Arrays.asList(dish(1, 0, true), dish(2, 1, true), dish(3, 2, true), dish(4, 0, false));
        List<Dish> lunch = MealLogic.candidates(all, 1, Set.of(), Set.of());
        List<Dish> dinner = MealLogic.candidates(all, 2, Set.of(), Set.of());
        assertEquals(Arrays.asList(all.get(0), all.get(1)), lunch);
        assertEquals(Arrays.asList(all.get(0), all.get(2)), dinner);
    }
    @Test public void emptyAndSingleCandidate() {
        assertNull(MealLogic.choose(Collections.emptyList(), 0, new Random(5)));
        Dish only = dish(1, 0, true);
        assertSame(only, MealLogic.choose(List.of(only), 1, new Random(5)));
    }
    @Test public void neverImmediatelyRepeatsAndOnlyPicksFromPool() {
        List<Dish> pool = Arrays.asList(dish(1, 0, true), dish(2, 0, true), dish(3, 0, true));
        Random rng = new Random(2026); long previous = 0;
        Set<Long> reached = new java.util.HashSet<>();
        for (int i = 0; i < 3000; i++) {
            Dish next = MealLogic.choose(pool, previous, rng);
            assertNotEquals(previous, next.id); assertTrue(pool.contains(next)); reached.add(next.id); previous = next.id;
        }
        assertEquals(Set.of(1L, 2L, 3L), reached);
    }
    @Test public void excludesLunchByIdentityAndSnapshotName() {
        List<Dish> all = Arrays.asList(dish(1, 0, true), dish(2, 0, true), dish(3, 0, true));
        assertEquals(List.of(all.get(2)), MealLogic.candidates(all, 2, Set.of(1L), Set.of("饭菜2")));
    }
    @Test public void priceUsesExactCents() {
        assertNull(MealLogic.parsePrice(" ")); assertEquals(Long.valueOf(1250), MealLogic.parsePrice("12.50"));
        assertEquals(Long.valueOf(0), MealLogic.parsePrice("0")); assertEquals("12.50", MealLogic.price(1250L));
        for (String bad : new String[]{"-1", "0.001", "100000", "NaN", "1e2", "1,000"}) {
            assertThrows(IllegalArgumentException.class, () -> MealLogic.parsePrice(bad));
        }
    }
    @Test public void normalizesNamesForDuplicateChecks() {
        assertEquals("牛肉面", MealLogic.key("　牛肉面  "));
        assertEquals(MealLogic.key("ＡＢＣ 饭"), MealLogic.key("abc  饭"));
    }
}
