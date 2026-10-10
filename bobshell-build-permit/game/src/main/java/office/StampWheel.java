package office;

public final class StampWheel {
    private StampWheel() {
    }

    /** The ten counters are numbered 0 through 9. Steps can go in either direction. */
    public static int rotate(int position, int steps) {
        return (position + steps) % 10;
    }
}
