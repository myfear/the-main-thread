package example;

/** This leading comment is outside the declaration's source span. */
public class Everything {
    private int first, second;
    private String name;

    public Everything() {}
    public void run() {}
    public int size() { return first + second; }

    static class Nested {
        int ignored;
        void ignoredMethod() {}
    }
}

interface Action {
    String NAME = "action";
    void run();
    default int size() { return 0; }
}

record Point(int x, String label) {
    static int total;
    Point { if (x < 0) throw new IllegalArgumentException(); }
    Point(int x) { this(x, ""); }
    int twice() { return x * 2; }
}

enum State {
    NEW, DONE;
    int code;
    void reset() {}
}

@interface Label {
    String value();
    int priority() default 0;
}

class Helper {
    void help() {}
}
