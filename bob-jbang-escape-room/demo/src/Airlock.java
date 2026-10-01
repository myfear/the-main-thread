class Airlock {
    static boolean canOpen(int pressureKpa, boolean innerDoorSealed) {
        return innerDoorSealed || pressureKpa <= 5;
    }
}
