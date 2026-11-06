package cn.todayeat.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Host-rendered Android views for visual QA; not a physical device screenshot. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenRenderTest {
    private View find(View v, String text) {
        if (v instanceof TextView && ((TextView)v).getText().toString().equals(text)) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)v).getChildCount(); i++) {
            View found = find(((ViewGroup)v).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private void capture(MainActivity activity, String name) throws Exception {
        View view = activity.getWindow().getDecorView();
        int width = 1179, height = 2556;
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        File output = new File("build/screenshots/" + name + ".png"); output.getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(output)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
        bitmap.recycle();
    }
    @Test public void renderMainScreens() throws Exception {
        RuntimeEnvironment.getApplication().deleteDatabase(MealDatabase.NAME);
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) { capture(controller.get(), "01-home-empty"); }
        MealDatabase db = new MealDatabase(RuntimeEnvironment.getApplication());
        db.save(new Dish(0, "牛肉面", "一食堂", "二楼面食档口", 1250L, 0, true));
        long id = db.save(new Dish(0, "黄焖鸡", "二食堂", "砂锅档口", 1800L, 2, true));
        db.save(new Dish(0, "饺子", "一食堂", "", null, 0, false));
        db.confirm(id, 2, java.time.LocalDate.now().toString(), false, 1); db.close();
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity a = controller.get(); capture(a, "02-home");
            find(a.getWindow().getDecorView(), "☀  午饭吃什么").performClick(); capture(a, "03-result");
            find(a.getWindow().getDecorView(), "饭菜").performClick(); capture(a, "04-dishes");
            find(a.getWindow().getDecorView(), "记录").performClick(); capture(a, "05-history");
        }
        RuntimeEnvironment.getApplication().deleteDatabase(MealDatabase.NAME);
    }
}
