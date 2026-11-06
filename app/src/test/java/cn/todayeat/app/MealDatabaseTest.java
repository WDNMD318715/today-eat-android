package cn.todayeat.app;

import android.content.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class MealDatabaseTest {
    private Context context;
    private MealDatabase db;
    private final String day = "2026-11-06";
    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication(); context.deleteDatabase(MealDatabase.NAME); db = new MealDatabase(context);
    }
    @After public void tearDown() { db.close(); context.deleteDatabase(MealDatabase.NAME); }
    private long add(String name, int meal) { return db.save(new Dish(0, name, "一食堂", "面食", 1250L, meal, true)); }

    @Test public void dishesSettingsAndHistorySurviveDatabaseCloseAndReopen() {
        long id = add("牛肉面", 0); db.setAvoidLunch(true);
        db.confirm(id, 1, day, false, 123456); db.close();
        db = new MealDatabase(context);
        assertEquals(1, db.dishes().size()); assertEquals("牛肉面", db.dish(id).name);
        assertEquals("一食堂", db.dish(id).canteen); assertEquals(Long.valueOf(1250), db.dish(id).priceCents);
        assertTrue(db.avoidLunch()); assertEquals(1, db.history().size()); assertEquals(day, db.history().get(0).day);
    }
    @Test public void disabledAndMealFiltersAreAppliedAfterReopen() {
        long both = add("饺子", 0); long lunch = add("牛肉面", 1); long dinner = add("黄焖鸡", 2);
        db.setEnabled(both, false); db.close(); db = new MealDatabase(context);
        assertEquals(1, db.candidates(1, day, false).size()); assertEquals(lunch, db.candidates(1, day, false).get(0).id);
        assertEquals(1, db.candidates(2, day, false).size()); assertEquals(dinner, db.candidates(2, day, false).get(0).id);
        db.setEnabled(both, true); assertEquals(2, db.candidates(2, day, false).size());
    }
    @Test public void pickingDoesNotRecordAndDinnerNeedsExplicitRelaxation() {
        long id = add("饺子", 0); db.setAvoidLunch(true);
        MealLogic.choose(db.candidates(1, day, false), 0, new java.util.Random()); assertTrue(db.history().isEmpty());
        db.confirm(id, 1, day, false, 1);
        assertTrue(db.candidates(2, day, false).isEmpty()); assertEquals(1, db.candidates(2, day, true).size());
        assertThrows(IllegalArgumentException.class, () -> db.confirm(id, 2, day, false, 2));
        db.confirm(id, 2, day, true, 2); assertEquals(2, db.history().size());
        assertEquals(1, db.candidates(2, "2026-11-07", false).size());
        db.setAvoidLunch(false); assertEquals(1, db.candidates(2, day, false).size());
    }
    @Test public void batchSkipsDuplicatesAndRejectsWhitespace() {
        add("饺子", 0);
        MealDatabase.BatchResult result = db.addBatch(" 饺子 \n牛肉面\n\n牛肉面\n黄焖鸡", "", "", null, 0);
        assertEquals(2, result.added); assertEquals(2, result.duplicates.size()); assertEquals(3, db.dishes().size());
        assertThrows(IllegalArgumentException.class, () -> add("　 ", 0));
        assertThrows(IllegalArgumentException.class, () -> add(" 饺子　", 0));
        assertThrows(IllegalArgumentException.class, () -> db.addBatch("\n　", "", "", null, 0));
    }
    @Test public void failedBatchIsAtomic() {
        add("饺子", 0);
        assertThrows(IllegalArgumentException.class, () -> db.addBatch("牛肉面\n黄焖鸡", "过长".repeat(40), "", null, 0));
        assertEquals(1, db.dishes().size());
    }
    @Test public void editDeleteDoNotRewriteHistoryAndRenamedLunchStillExcluded() {
        long id = add("饺子", 0); db.confirm(id, 1, day, false, 1); db.setAvoidLunch(true);
        db.save(new Dish(id, "水饺", "二食堂", "", null, 0, true));
        assertEquals("水饺", db.dish(id).name); assertTrue(db.candidates(2, day, false).isEmpty());
        assertEquals("饺子", db.history().get(0).name); assertEquals("一食堂", db.history().get(0).canteen);
        db.delete(id); assertTrue(db.dishes().isEmpty()); assertEquals("饺子", db.history().get(0).name);
        add("饺子", 0); assertTrue(db.candidates(2, day, false).isEmpty());
    }
    @Test public void sameMealConfirmationReplacesWithoutDuplicateHistory() {
        long first = add("饺子", 0); long second = add("牛肉面", 0);
        db.confirm(first, 1, day, false, 1); db.confirm(second, 1, day, false, 2);
        assertEquals(1, db.history().size()); assertEquals("牛肉面", db.history().get(0).name);
        db.setAvoidLunch(true); assertEquals(first, db.candidates(2, day, false).get(0).id);
    }
    @Test public void disabledDishCannotBeConfirmedFromStaleResult() {
        long id = add("饺子", 0); db.setEnabled(id, false);
        assertThrows(IllegalArgumentException.class, () -> db.confirm(id, 1, day, false, 1));
        assertTrue(db.history().isEmpty());
    }
    @Test public void duplicateEditDoesNotOverwriteAndDisabledNameIsProtected() {
        long first = add("饺子", 0); long second = add("牛肉面", 0); db.setEnabled(first, false);
        assertThrows(IllegalArgumentException.class, () -> add("饺子", 0));
        assertThrows(IllegalArgumentException.class, () -> db.save(new Dish(second, "饺子", "", "", null, 0, true)));
        assertEquals("牛肉面", db.dish(second).name);
    }
}
