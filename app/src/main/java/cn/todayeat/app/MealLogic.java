package cn.todayeat.app;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class MealLogic {
    public static final int BOTH = 0, LUNCH = 1, DINNER = 2;
    private MealLogic() {}

    public static String clean(String name) {
        return Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}]+", " ").trim();
    }
    public static String key(String name) { return clean(name).toLowerCase(Locale.ROOT); }
    public static String mealName(int meal) {
        return meal == LUNCH ? "午饭" : meal == DINNER ? "晚饭" : "午晚都可以";
    }
    public static Long parsePrice(String raw) {
        String value = clean(raw);
        if (value.isEmpty()) return null;
        if (!value.matches("[0-9]{1,5}(\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException("价格请填 0～99999.99 的数字，最多两位小数");
        return new BigDecimal(value).movePointRight(2).longValueExact();
    }
    public static String price(Long cents) {
        return cents == null ? "" : BigDecimal.valueOf(cents, 2).toPlainString();
    }
    public static List<Dish> candidates(List<Dish> dishes, int meal, Set<Long> excludedIds, Set<String> excludedKeys) {
        if (meal != LUNCH && meal != DINNER) throw new IllegalArgumentException("请选择午饭或晚饭");
        List<Dish> result = new ArrayList<>();
        for (Dish d : dishes) {
            if (d.enabled && (d.meal == BOTH || d.meal == meal)
                    && !excludedIds.contains(d.id) && !excludedKeys.contains(key(d.name))) result.add(d);
        }
        return result;
    }
    public static Dish choose(List<Dish> candidates, long previousId, Random random) {
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);
        List<Dish> pool = new ArrayList<>();
        for (Dish d : candidates) if (d.id != previousId) pool.add(d);
        return pool.get(random.nextInt(pool.size()));
    }
}
