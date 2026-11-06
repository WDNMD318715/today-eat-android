package cn.todayeat.app;

public final class Dish {
    public final long id;
    public final String name, canteen, stall;
    public final Long priceCents;
    public final int meal;
    public final boolean enabled;

    public Dish(long id, String name, String canteen, String stall, Long priceCents, int meal, boolean enabled) {
        this.id = id;
        this.name = name;
        this.canteen = canteen;
        this.stall = stall;
        this.priceCents = priceCents;
        this.meal = meal;
        this.enabled = enabled;
    }
}
