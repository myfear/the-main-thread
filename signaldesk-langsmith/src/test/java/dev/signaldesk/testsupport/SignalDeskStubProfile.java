package dev.signaldesk.testsupport;

import java.util.Set;

import io.quarkus.test.junit.QuarkusTestProfile;

public class SignalDeskStubProfile implements QuarkusTestProfile {

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(SignalDeskStubChatModel.class);
    }
}
