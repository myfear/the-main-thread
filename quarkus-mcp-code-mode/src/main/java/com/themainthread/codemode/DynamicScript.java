package com.themainthread.codemode;

import io.roastedroot.quickjs4j.annotations.ScriptInterface;

@ScriptInterface(context = HostBridge.class)
public interface DynamicScript {
    String run(String inputJson);
}
