package com.themainthread.codemode;

/** Messages are fixed here or at trusted call sites; never include guest/upstream exception text. */
final class CodeModeFailure extends RuntimeException {
    final String kind;

    CodeModeFailure(String kind, String message) {
        super(message);
        this.kind = kind;
    }
}
