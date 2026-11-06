package cn.todayeat.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

public final class MainActivity extends Activity {
    private static final int BG = 0xFFF7F6F0, INK = 0xFF253C34, MUTED = 0xFF63746C,
            GREEN = 0xFF28624F, PALE = 0xFFE6EDE5, ORANGE = 0xFF995225, CREAM = 0xFFF7EAD6;
    private MealDatabase db;
    private LinearLayout root, page;
    private int tab = 0, selectedMeal = MealLogic.LUNCH;
    private long selectedId = 0;
    private boolean relaxed = false;
    private String selectionDay = "", visibleDay = "", search = "";
    private final Random random = new Random();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new MealDatabase(this);
        if (state != null) {
            tab = state.getInt("tab"); selectedMeal = state.getInt("meal", MealLogic.LUNCH);
            selectedId = state.getLong("selected"); relaxed = state.getBoolean("relaxed");
            selectionDay = state.getString("day", ""); search = state.getString("search", "");
        }
        try { db.getWritableDatabase(); render(); }
        catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("暂时无法读取本地数据")
                    .setMessage("请检查手机可用空间后重新打开。原有数据不会被自动清除。")
                    .setPositiveButton("关闭", (d,w) -> finish()).setCancelable(false).show();
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if (root != null && !today().equals(visibleDay)) { selectedId = 0; relaxed = false; render(); }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("tab", tab); state.putInt("meal", selectedMeal); state.putLong("selected", selectedId);
        state.putBoolean("relaxed", relaxed); state.putString("day", selectionDay); state.putString("search", search);
    }
    @Override protected void onDestroy() { if (db != null) db.close(); super.onDestroy(); }
    private String today() { return LocalDate.now().toString(); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setIncludeFontPadding(true); v.setLineSpacing(dp(3), 1);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private void space(LinearLayout parent, int height) { View s = new View(this); parent.addView(s, new LinearLayout.LayoutParams(1, dp(height))); }
    private Button button(String title, boolean primary, Runnable action) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setTextSize(16);
        b.setTextColor(primary ? Color.WHITE : GREEN); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(primary ? GREEN : PALE, 16)); b.setMinHeight(dp(52));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void fullButton(LinearLayout parent, String title, boolean primary, Runnable action) {
        parent.addView(button(title, primary, action), new LinearLayout.LayoutParams(-1, -2));
    }
    private LinearLayout card(LinearLayout parent, int color) {
        LinearLayout box = column(); box.setPadding(dp(20), dp(18), dp(20), dp(18));
        box.setBackground(rounded(color, 24));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(14);
        parent.addView(box, lp); return box;
    }
    private void render() {
        visibleDay = today();
        root = column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        page = column(); page.setPadding(dp(22), dp(24), dp(22), dp(20));
        scroll.addView(page); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout nav = new LinearLayout(this); nav.setPadding(dp(16), dp(10), dp(16), dp(10));
        nav.setBackgroundColor(Color.WHITE);
        String[] tabs = {"选饭", "饭菜", "记录"};
        for (int i = 0; i < tabs.length; i++) {
            final int target = i;
            Button b = button(tabs[i], i == tab, () -> { tab = target; render(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.leftMargin = dp(8); nav.addView(b, lp);
        }
        root.addView(nav); setContentView(root); root.requestApplyInsets();
        if (tab == 0) home(); else if (tab == 1) manage(); else history();
    }
    private void heading(String eyebrow, String title, String note) {
        page.addView(label(eyebrow, 12, GREEN, true)); space(page, 8);
        page.addView(label(title, 30, INK, true)); space(page, 8);
        page.addView(label(note, 14, MUTED, false)); space(page, 24);
    }
    private void home() {
        heading(LocalDate.now().format(DateTimeFormatter.ofPattern("M 月 d 日")) + "  ·  好好吃饭",
                "今天吃什么", "把纠结交给抽选，把时间留给吃饭。");
        int active = 0;
        for (Dish d : db.dishes()) if (d.enabled) active++;
        LinearLayout hero = card(page, PALE);
        hero.addView(label("我的饭菜清单", 14, GREEN, true)); space(hero, 8);
        hero.addView(label(active == 0 ? "先收下你的心头好" : active + " 道饭菜，等你翻牌", 23, INK, true));
        space(hero, 8);
        hero.addView(label("只从你录入的饭菜里选，不塞进陌生推荐。", 14, MUTED, false));
        if (active == 0) { space(hero, 16); fullButton(hero, "去添加饭菜", true, () -> { tab = 1; render(); }); }
        fullButton(page, "☀  午饭吃什么", true, () -> startPick(MealLogic.LUNCH)); space(page, 12);
        fullButton(page, "☾  晚饭吃什么", false, () -> startPick(MealLogic.DINNER)); space(page, 20);

        if (selectedId != 0 && today().equals(selectionDay)) {
            Dish chosen = db.dish(selectedId);
            List<Dish> eligible = db.candidates(selectedMeal, today(), relaxed);
            boolean valid = false; for (Dish d : eligible) if (d.id == selectedId) valid = true;
            if (chosen != null && valid) result(chosen, eligible.size()); else selectedId = 0;
        }
        LinearLayout rule = card(page, Color.WHITE);
        Switch toggle = new Switch(this); toggle.setText("晚饭避开今天午饭\n已确认的饭菜");
        toggle.setTextSize(15); toggle.setTextColor(INK); toggle.setMinHeight(dp(56));
        toggle.setPadding(0, dp(4), 0, dp(4)); toggle.setChecked(db.avoidLunch());
        toggle.setOnCheckedChangeListener((v, checked) -> {
            try { db.setAvoidLunch(checked); if (selectedMeal == MealLogic.DINNER) selectedId = 0; relaxed = false; render(); }
            catch (Exception e) { problem(e); }
        });
        rule.addView(toggle); space(rule, 8);
        rule.addView(label("仅按今天已确认的午饭排除。没有候选时，会先问你是否放宽。", 12, MUTED, false));
        TextView privacy = label("无需登录 · 完全离线 · 数据保存在本机\n卸载应用或清除应用数据后，饭菜和记录会丢失。", 12, MUTED, false);
        privacy.setGravity(Gravity.CENTER); page.addView(privacy);
    }
    private void result(Dish dish, int count) {
        LinearLayout box = card(page, CREAM);
        box.addView(label(MealLogic.mealName(selectedMeal) + "的提案  /  尚未确认", 12, ORANGE, true)); space(box, 10);
        box.addView(label(dish.name, 28, INK, true));
        String detail = details(dish.canteen, dish.stall, dish.priceCents);
        if (!detail.isEmpty()) { space(box, 8); box.addView(label(detail, 15, MUTED, false)); }
        space(box, 12);
        box.addView(label(count == 1 ? "当前只有这一道可选" : "共 " + count + " 道可选 · 换一个不会紧接着重复", 12, ORANGE, false));
        if (relaxed) { space(box, 4); box.addView(label("本轮已放宽“避开午饭”限制", 12, ORANGE, true)); }
        space(box, 18); fullButton(box, "就吃这个", true, this::confirmSelection); space(box, 10);
        fullButton(box, "换一个", false, () -> pick(selectedMeal, relaxed, selectedId));
        box.post(() -> {
            if (box.isAttachedToWindow() && page.getParent() instanceof ScrollView)
                ((ScrollView) page.getParent()).smoothScrollTo(0, Math.max(0, box.getTop() - dp(12)));
        });
    }
    private void startPick(int meal) { relaxed = false; pick(meal, false, selectedMeal == meal ? selectedId : 0); }
    private void pick(int meal, boolean allowRepeatLunch, long previous) {
        try {
            String day = today();
            List<Dish> pool = db.candidates(meal, day, allowRepeatLunch);
            if (pool.isEmpty()) {
                selectedId = 0; selectedMeal = meal; render();
                if (meal == MealLogic.DINNER && db.avoidLunch() && !allowRepeatLunch
                        && !db.candidates(meal, day, true).isEmpty()) {
                    new AlertDialog.Builder(this).setTitle("避开午饭后，没有候选了")
                            .setMessage("要仅为本轮晚饭放宽限制吗？开关会继续保留，下一轮仍会先避开午饭。")
                            .setNegativeButton("先不选", null)
                            .setPositiveButton("本轮允许重复", (d,w) -> pick(meal, true, 0)).show();
                } else {
                    new AlertDialog.Builder(this).setTitle("还没有适合" + MealLogic.mealName(meal) + "的饭菜")
                            .setMessage("去添加饭菜，或在饭菜管理里恢复已停用的饭菜、调整适用餐次。")
                            .setNegativeButton("知道了", null).setPositiveButton("管理饭菜", (d,w) -> { tab = 1; render(); }).show();
                }
                return;
            }
            Dish chosen = MealLogic.choose(pool, previous, random);
            selectedId = chosen.id; selectedMeal = meal; relaxed = allowRepeatLunch; selectionDay = day; render();
        } catch (Exception e) { problem(e); }
    }
    private void confirmSelection() {
        if (!today().equals(selectionDay)) {
            selectedId = 0; relaxed = false; render(); toast("日期已经变化，请为今天重新抽选"); return;
        }
        if (db.hasConfirmation(today(), selectedMeal)) {
            new AlertDialog.Builder(this).setTitle("更新今天的" + MealLogic.mealName(selectedMeal) + "？")
                    .setMessage("今天这顿已经有确认记录。继续会用这道饭菜替换原记录。")
                    .setNegativeButton("保留原记录", null).setPositiveButton("确认替换", (d,w) -> saveConfirmation()).show();
        } else saveConfirmation();
    }
    private void saveConfirmation() {
        try {
            if (!today().equals(selectionDay)) throw new IllegalArgumentException("日期已经变化，请重新抽选");
            db.confirm(selectedId, selectedMeal, selectionDay, relaxed, System.currentTimeMillis());
            selectedId = 0; relaxed = false; render(); toast("已记下这顿，好好吃饭！");
        } catch (Exception e) { problem(e); }
    }
    private String details(String canteen, String stall, Long cents) {
        String result = canteen;
        if (!stall.isEmpty()) result += (result.isEmpty() ? "" : " · ") + stall;
        if (cents != null) result += (result.isEmpty() ? "" : "\n") + "¥ " + MealLogic.price(cents);
        return result;
    }
    private void manage() {
        List<Dish> all = db.dishes(); int enabled = 0; for (Dish d : all) if (d.enabled) enabled++;
        heading("我的专属菜单", "饭菜管理", "共 " + all.size() + " 道 · " + enabled + " 道参与抽选");
        fullButton(page, "＋ 添加一道饭菜", true, () -> editor(null, false)); space(page, 10);
        fullButton(page, "批量添加 · 每行一道", false, () -> editor(null, true)); space(page, 18);
        EditText query = field("搜索名称、食堂或档口", search, false); query.setContentDescription("搜索饭菜");
        page.addView(query); space(page, 16);
        LinearLayout list = column(); page.addView(list);
        renderDishes(list, all, search);
        query.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { search = s.toString(); renderDishes(list, all, search); }
            public void afterTextChanged(Editable e) {}
        });
    }
    private void renderDishes(LinearLayout list, List<Dish> dishes, String query) {
        list.removeAllViews(); int shown = 0;
        for (Dish dish : dishes) {
            if (!MealLogic.key(dish.name + " " + dish.canteen + " " + dish.stall).contains(MealLogic.key(query))) continue;
            shown++;
            LinearLayout box = card(list, Color.WHITE);
            box.addView(label(dish.name, 21, dish.enabled ? INK : MUTED, true)); space(box, 5);
            box.addView(label(MealLogic.mealName(dish.meal) + (dish.enabled ? "  ·  参与抽选" : "  ·  已停用"), 12, dish.enabled ? GREEN : ORANGE, true));
            String detail = details(dish.canteen, dish.stall, dish.priceCents);
            if (!detail.isEmpty()) { space(box, 8); box.addView(label(detail, 14, MUTED, false)); }
            space(box, 12);
            LinearLayout actions = new LinearLayout(this);
            Button edit = button("修改", false, () -> editor(dish, false));
            Button toggle = button(dish.enabled ? "停用" : "恢复", false, () -> {
                try { db.setEnabled(dish.id, !dish.enabled); if (selectedId == dish.id) selectedId = 0; render(); }
                catch (Exception e) { problem(e); }
            });
            actions.addView(edit, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.leftMargin = dp(8); actions.addView(toggle, lp);
            box.addView(actions); space(box, 8);
            Button delete = button("删除", false, () -> new AlertDialog.Builder(this).setTitle("删除「" + dish.name + "」？")
                    .setMessage("删除后不能恢复。已有的历史记录仍会保留；如果只是暂时不想吃，可以使用“停用”。")
                    .setNegativeButton("取消", null).setPositiveButton("删除", (d,w) -> {
                        try { db.delete(dish.id); if (selectedId == dish.id) selectedId = 0; render(); }
                        catch (Exception e) { problem(e); }
                    }).show());
            delete.setTextColor(ORANGE); delete.setBackground(rounded(BG, 16)); box.addView(delete, new LinearLayout.LayoutParams(-1, -2));
        }
        if (shown == 0) {
            LinearLayout empty = card(list, PALE);
            empty.addView(label(dishes.isEmpty() ? "菜单空空，期待第一道" : "没有找到匹配的饭菜", 20, INK, true)); space(empty, 8);
            empty.addView(label(dishes.isEmpty() ? "从学校里常吃的一道开始，也可以一次录入整个清单。" : "试试别的名称、食堂或档口。", 14, MUTED, false));
        }
    }
    private EditText field(String hint, String value, boolean multiline) {
        EditText input = new EditText(this); input.setTextSize(16); input.setTextColor(INK); input.setHintTextColor(MUTED);
        input.setHint(hint); input.setText(value); input.setPadding(dp(12), dp(12), dp(12), dp(12));
        input.setBackground(rounded(BG, 12)); input.setMinHeight(dp(52));
        input.setInputType(InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        input.setSingleLine(!multiline); if (multiline) { input.setMinLines(4); input.setGravity(Gravity.TOP); }
        return input;
    }
    private void formField(LinearLayout form, String title, View field) {
        form.addView(label(title, 13, MUTED, true)); space(form, 6); form.addView(field, new LinearLayout.LayoutParams(-1, -2)); space(form, 16);
    }
    private void editor(Dish existing, boolean bulk) {
        LinearLayout form = column(); form.setPadding(dp(22), dp(12), dp(22), dp(12));
        EditText name = field(bulk ? "每行一道饭菜\n例如：牛肉面\n黄焖鸡" : "例如：牛肉面", existing == null ? "" : existing.name, bulk);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(bulk ? 24300 : 80)});
        EditText canteen = field("例如：一食堂", existing == null ? "" : existing.canteen, false);
        EditText stall = field("例如：二楼面食档口", existing == null ? "" : existing.stall, false);
        canteen.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)}); stall.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
        EditText price = field("例如：12.50", existing == null ? "" : MealLogic.price(existing.priceCents), false);
        price.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Spinner meal = new Spinner(this);
        ArrayAdapter<String> options = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"都可以（默认）", "仅午饭", "仅晚饭"});
        options.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); meal.setAdapter(options); meal.setMinimumHeight(dp(52));
        meal.setSelection(existing == null ? 0 : existing.meal); meal.setContentDescription("适用餐次");
        if (bulk) { form.addView(label("下方选填信息会应用到本次新增的所有饭菜。重复名称会跳过并告知。", 13, MUTED, false)); space(form, 16); }
        formField(form, bulk ? "饭菜名称 *（每行一道）" : "饭菜名称 *", name);
        formField(form, "食堂（选填）", canteen); formField(form, "档口（选填）", stall);
        formField(form, "价格 / 元（选填）", price); formField(form, "适用餐次", meal);
        TextView error = label("", 14, ORANGE, false); error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); form.addView(error);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(existing != null ? "修改饭菜" : bulk ? "批量添加" : "添加饭菜")
                .setView(scroll).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(save -> {
            try {
                Long cents = MealLogic.parsePrice(price.getText().toString());
                if (bulk) {
                    MealDatabase.BatchResult result = db.addBatch(name.getText().toString(), canteen.getText().toString(), stall.getText().toString(), cents, meal.getSelectedItemPosition());
                    dialog.dismiss(); search = ""; render();
                    String message = "已添加 " + result.added + " 道饭菜。";
                    if (!result.duplicates.isEmpty()) message += "\n跳过 " + result.duplicates.size() + " 个重复名称（含清单内和本次输入的重复）：\n" + String.join("、", result.duplicates);
                    new AlertDialog.Builder(this).setTitle("保存完成").setMessage(message).setPositiveButton("知道了", null).show();
                } else {
                    db.save(new Dish(existing == null ? 0 : existing.id, name.getText().toString(), canteen.getText().toString(), stall.getText().toString(), cents, meal.getSelectedItemPosition(), existing == null || existing.enabled));
                    if (existing != null && selectedId == existing.id) selectedId = 0;
                    dialog.dismiss(); search = ""; render(); toast("已保存到手机");
                }
            } catch (IllegalArgumentException e) { error.setText(e.getMessage()); }
            catch (Exception e) { error.setText("未能保存，请检查手机存储空间后重试。输入内容仍在。"); }
        }));
        dialog.show();
    }
    private void history() {
        heading("每一顿，都算数", "选择记录", "最近 100 条确认记录 · 每天每餐保留一条");
        List<MealDatabase.Record> records = db.history();
        if (records.isEmpty()) {
            LinearLayout empty = card(page, PALE); empty.addView(label("第一顿，等你决定", 22, INK, true)); space(empty, 10);
            empty.addView(label("抽选后点“就吃这个”，才会记在这里。仅仅换一换，不算吃过。", 15, MUTED, false)); space(empty, 20);
            fullButton(empty, "去选今天这一顿", true, () -> { tab = 0; render(); });
        }
        String lastDay = "";
        for (MealDatabase.Record record : records) {
            if (!record.day.equals(lastDay)) {
                page.addView(label((record.day.equals(today()) ? "今天  ·  " : "") + record.day, 14, MUTED, true)); space(page, 12); lastDay = record.day;
            }
            LinearLayout box = card(page, Color.WHITE);
            box.addView(label(MealLogic.mealName(record.meal) + "  ·  已确认", 12, GREEN, true)); space(box, 8);
            box.addView(label(record.name, 23, INK, true));
            String detail = details(record.canteen, record.stall, record.priceCents);
            if (!detail.isEmpty()) { space(box, 8); box.addView(label(detail, 14, MUTED, false)); }
        }
        space(page, 10); page.addView(label("记录保存的是确认时的信息。修改或删除饭菜，不会改动过去的记录。", 12, MUTED, false));
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void problem(Exception e) {
        new AlertDialog.Builder(this).setTitle("暂时未完成")
                .setMessage(e instanceof IllegalArgumentException ? e.getMessage() : "请检查手机存储空间后重试。本次操作没有确认成功。")
                .setPositiveButton("知道了", null).show();
    }
}
