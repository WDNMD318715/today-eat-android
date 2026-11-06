package cn.todayeat.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MealDatabase extends SQLiteOpenHelper {
    public static final String NAME = "today-eat.db";
    public MealDatabase(Context context) { super(context, NAME, null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE dishes (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, name_key TEXT NOT NULL UNIQUE, canteen TEXT NOT NULL, stall TEXT NOT NULL, price_cents INTEGER, meal INTEGER NOT NULL CHECK(meal IN (0,1,2)), enabled INTEGER NOT NULL CHECK(enabled IN (0,1)))");
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, day TEXT NOT NULL, meal INTEGER NOT NULL CHECK(meal IN (1,2)), dish_id INTEGER NOT NULL, name TEXT NOT NULL, name_key TEXT NOT NULL, canteen TEXT NOT NULL, stall TEXT NOT NULL, price_cents INTEGER, confirmed_at INTEGER NOT NULL, UNIQUE(day,meal))");
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("数据库版本不支持，保留原有数据");
    }

    private Dish readDish(Cursor c) {
        return new Dish(c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("name")), c.getString(c.getColumnIndexOrThrow("canteen")),
                c.getString(c.getColumnIndexOrThrow("stall")),
                c.isNull(c.getColumnIndexOrThrow("price_cents")) ? null : c.getLong(c.getColumnIndexOrThrow("price_cents")),
                c.getInt(c.getColumnIndexOrThrow("meal")), c.getInt(c.getColumnIndexOrThrow("enabled")) == 1);
    }
    public List<Dish> dishes() {
        List<Dish> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("dishes", null, null, null, null, null, "enabled DESC, id DESC")) {
            while (c.moveToNext()) result.add(readDish(c));
        }
        return result;
    }
    public Dish dish(long id) {
        try (Cursor c = getReadableDatabase().query("dishes", null, "id=?", new String[]{"" + id}, null, null, null)) {
            return c.moveToFirst() ? readDish(c) : null;
        }
    }
    public boolean nameExists(String name, long exceptId) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM dishes WHERE name_key=? AND id<>?", new String[]{MealLogic.key(name), "" + exceptId})) {
            return c.moveToFirst();
        }
    }
    public long save(Dish d) {
        String name = MealLogic.clean(d.name);
        if (name.isEmpty() || name.length() > 80) throw new IllegalArgumentException("饭菜名称必填，最多 80 个字");
        if (d.canteen.length() > 60 || d.stall.length() > 60) throw new IllegalArgumentException("食堂、档口各最多 60 个字");
        if (d.priceCents != null && (d.priceCents < 0 || d.priceCents > 9999999)) throw new IllegalArgumentException("价格超出范围");
        if (d.meal < 0 || d.meal > 2) throw new IllegalArgumentException("餐次无效");
        if (nameExists(name, d.id)) throw new IllegalArgumentException("已存在同名饭菜「" + name + "」，请检查列表（含已停用饭菜）");
        ContentValues v = values(d);
        v.put("name", name);
        v.put("name_key", MealLogic.key(name));
        v.put("meal", d.meal);
        v.put("enabled", d.enabled ? 1 : 0);
        if (d.id == 0) return getWritableDatabase().insertOrThrow("dishes", null, v);
        if (getWritableDatabase().update("dishes", v, "id=?", new String[]{"" + d.id}) != 1)
            throw new IllegalArgumentException("这道饭菜已经被删除");
        return d.id;
    }
    private ContentValues values(Dish d) {
        ContentValues v = new ContentValues();
        v.put("name", d.name); v.put("name_key", MealLogic.key(d.name));
        v.put("canteen", MealLogic.clean(d.canteen)); v.put("stall", MealLogic.clean(d.stall));
        if (d.priceCents == null) v.putNull("price_cents"); else v.put("price_cents", d.priceCents);
        return v;
    }
    public static final class BatchResult {
        public int added;
        public final List<String> duplicates = new ArrayList<>();
    }
    public BatchResult addBatch(String text, String canteen, String stall, Long price, int meal) {
        String[] lines = text.split("\\R");
        List<String> names = new ArrayList<>();
        for (String line : lines) {
            String name = MealLogic.clean(line);
            if (name.length() > 80) throw new IllegalArgumentException("名称最多 80 个字：" + name.substring(0, 12) + "…");
            if (!name.isEmpty()) names.add(name);
        }
        if (names.isEmpty()) throw new IllegalArgumentException("请至少输入一道饭菜，每行一道");
        if (names.size() > 300) throw new IllegalArgumentException("每次最多添加 300 道饭菜");
        BatchResult result = new BatchResult();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String name : names) {
                if (nameExists(name, 0)) result.duplicates.add(name);
                else { save(new Dish(0, name, canteen, stall, price, meal, true)); result.added++; }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return result;
    }
    public void setEnabled(long id, boolean enabled) {
        ContentValues v = new ContentValues(); v.put("enabled", enabled ? 1 : 0);
        getWritableDatabase().update("dishes", v, "id=?", new String[]{"" + id});
    }
    public void delete(long id) { getWritableDatabase().delete("dishes", "id=?", new String[]{"" + id}); }
    public boolean avoidLunch() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM settings WHERE key='avoid_lunch'", null)) {
            return c.moveToFirst() && c.getInt(0) == 1;
        }
    }
    public void setAvoidLunch(boolean value) {
        ContentValues v = new ContentValues(); v.put("key", "avoid_lunch"); v.put("value", value ? 1 : 0);
        if (getWritableDatabase().insertWithOnConflict("settings", null, v, SQLiteDatabase.CONFLICT_REPLACE) == -1)
            throw new android.database.sqlite.SQLiteException("设置保存失败");
    }
    public List<Dish> candidates(int meal, String day, boolean relaxed) {
        Set<Long> ids = new HashSet<>(); Set<String> keys = new HashSet<>();
        if (meal == MealLogic.DINNER && avoidLunch() && !relaxed) {
            try (Cursor c = getReadableDatabase().rawQuery("SELECT dish_id, name_key FROM history WHERE day=? AND meal=1", new String[]{day})) {
                while (c.moveToNext()) { ids.add(c.getLong(0)); keys.add(c.getString(1)); }
            }
        }
        return MealLogic.candidates(dishes(), meal, ids, keys);
    }
    public boolean hasConfirmation(String day, int meal) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM history WHERE day=? AND meal=?", new String[]{day, "" + meal})) {
            return c.moveToFirst();
        }
    }
    public void confirm(long dishId, int meal, String day, boolean relaxed, long now) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Dish selected = null;
            for (Dish d : candidates(meal, day, relaxed)) if (d.id == dishId) selected = d;
            if (selected == null) throw new IllegalArgumentException("饭菜或筛选条件已改变，请重新抽选");
            ContentValues v = values(selected);
            v.put("day", day); v.put("meal", meal); v.put("dish_id", dishId); v.put("confirmed_at", now);
            if (db.insertWithOnConflict("history", null, v, SQLiteDatabase.CONFLICT_REPLACE) == -1)
                throw new android.database.sqlite.SQLiteException("记录保存失败");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public static final class Record {
        public final String day, name, canteen, stall;
        public final int meal;
        public final Long priceCents;
        Record(Cursor c) {
            day = c.getString(c.getColumnIndexOrThrow("day")); name = c.getString(c.getColumnIndexOrThrow("name"));
            canteen = c.getString(c.getColumnIndexOrThrow("canteen")); stall = c.getString(c.getColumnIndexOrThrow("stall"));
            meal = c.getInt(c.getColumnIndexOrThrow("meal"));
            priceCents = c.isNull(c.getColumnIndexOrThrow("price_cents")) ? null : c.getLong(c.getColumnIndexOrThrow("price_cents"));
        }
    }
    public List<Record> history() {
        List<Record> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("history", null, null, null, null, null, "day DESC, meal DESC", "100")) {
            while (c.moveToNext()) result.add(new Record(c));
        }
        return result;
    }
}
