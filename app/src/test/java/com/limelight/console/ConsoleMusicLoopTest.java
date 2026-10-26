package com.limelight.console;

import static org.junit.Assert.assertEquals;

import android.media.AudioTrack;

import org.junit.Test;

public class ConsoleMusicLoopTest {
    @Test public void aFailedWriteGivesItsAudioUpInsteadOfOfferingItAgain() {
        assertEquals(4096, ConsoleMusicLoop.droppedAfterWrite(4096, AudioTrack.ERROR_DEAD_OBJECT));
        assertEquals(4096, ConsoleMusicLoop.droppedAfterWrite(4096, AudioTrack.ERROR_INVALID_OPERATION));
        assertEquals(0, ConsoleMusicLoop.droppedAfterWrite(4096, 1024));
        assertEquals(0, ConsoleMusicLoop.droppedAfterWrite(4096, 0));
    }
}
