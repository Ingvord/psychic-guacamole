package ru.ingvord.trading.fixtures;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lambda Context whose logger prints to stdout. */
public final class TestContext {
    private TestContext() {}

    public static Context create() {
        LambdaLogger logger = new LambdaLogger() {
            @Override
            public void log(String message) {
                System.out.println("  [lambda] " + message);
            }

            @Override
            public void log(byte[] message) {
                log(new String(message, StandardCharsets.UTF_8));
            }
        };
        Context context = mock(Context.class);
        when(context.getLogger()).thenReturn(logger);
        return context;
    }
}
