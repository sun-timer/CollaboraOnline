package org.libreoffice.androidlib.ai;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InferenceSessionTest {
    @Test
    public void cancelOnlyAffectsCurrentSession() {
        InferenceSession first = new InferenceSession("req-1");
        first.resetCancelled();
        first.requestCancel();
        assertTrue(first.isCancelled());

        InferenceSession second = new InferenceSession("req-2");
        second.resetCancelled();
        assertFalse(second.isCancelled());
    }
}
