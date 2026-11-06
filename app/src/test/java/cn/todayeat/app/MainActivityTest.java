package cn.todayeat.app;

import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MainActivityTest {
    @Before public void clearData() { RuntimeEnvironment.getApplication().deleteDatabase(MealDatabase.NAME); }
    private View find(View v, String text) {
        if (v instanceof TextView && ((TextView)v).getText().toString().equals(text)) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)v).getChildCount(); i++) {
            View found = find(((ViewGroup)v).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    @Test public void emptySelectionShowsActionableDialog() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            find(activity.getWindow().getDecorView(), "☀  午饭吃什么").performClick();
            AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(dialog.isShowing()); assertEquals("管理饭菜", dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        }
    }
    @Test public void singleCandidateRecreationAndExplicitConfirmation() {
        MealDatabase db = new MealDatabase(RuntimeEnvironment.getApplication());
        db.save(new Dish(0, "饺子", "", "", null, 0, true)); db.close();
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            find(activity.getWindow().getDecorView(), "☀  午饭吃什么").performClick();
            assertNotNull(find(activity.getWindow().getDecorView(), "当前只有这一道可选"));
            controller.recreate(); activity = controller.get();
            assertNotNull(find(activity.getWindow().getDecorView(), "饺子"));
            find(activity.getWindow().getDecorView(), "换一个").performClick();
            MealDatabase check = new MealDatabase(activity); assertTrue(check.history().isEmpty());
            find(activity.getWindow().getDecorView(), "就吃这个").performClick();
            assertEquals(1, check.history().size());
            assertNull(find(activity.getWindow().getDecorView(), "就吃这个")); check.close();
        }
    }
    @Test public void dinnerExclusionIsNotRelaxedUntilUserAccepts() {
        MealDatabase db = new MealDatabase(RuntimeEnvironment.getApplication());
        long id = db.save(new Dish(0, "饺子", "", "", null, 0, true)); db.setAvoidLunch(true);
        db.confirm(id, 1, java.time.LocalDate.now().toString(), false, 1); db.close();
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            find(activity.getWindow().getDecorView(), "☾  晚饭吃什么").performClick();
            assertNull(find(activity.getWindow().getDecorView(), "就吃这个"));
            AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertEquals("本轮允许重复", dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertNotNull(find(activity.getWindow().getDecorView(), "本轮已放宽“避开午饭”限制"));
        }
    }
}
