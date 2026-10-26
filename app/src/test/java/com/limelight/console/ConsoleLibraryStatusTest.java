package com.limelight.console;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConsoleLibraryStatusTest {
    @Test
    public void emptyRefreshTakesPriorityOverOldError() {
        assertEquals(ConsoleLibraryStatus.State.REFRESHING,
                ConsoleLibraryStatus.resolve(true, false,
                        PlayniteLibraryRepository.ErrorKind.TIMEOUT, true, false));
    }

    @Test
    public void authenticationDistinguishesMissingGateway() {
        assertEquals(ConsoleLibraryStatus.State.GATEWAY_NOT_CONFIGURED,
                ConsoleLibraryStatus.resolve(false, true,
                        PlayniteLibraryRepository.ErrorKind.AUTHENTICATION, false, false));
        assertEquals(ConsoleLibraryStatus.State.AUTHENTICATION_ERROR,
                ConsoleLibraryStatus.resolve(false, true,
                        PlayniteLibraryRepository.ErrorKind.AUTHENTICATION, true, false));
    }

    @Test
    public void currentIsNotAnError() {
        assertFalse(ConsoleLibraryStatus.isError(ConsoleLibraryStatus.State.CURRENT));
        assertFalse(ConsoleLibraryStatus.isError(ConsoleLibraryStatus.State.REFRESHING));
        assertTrue(ConsoleLibraryStatus.isError(ConsoleLibraryStatus.State.TIMEOUT));
    }

    @Test
    public void populatedRefreshKeepsLastKnownStatusAndReportsErrors() {
        assertEquals(ConsoleLibraryStatus.State.CURRENT,
                ConsoleLibraryStatus.resolve(true, true, null, true, false));
        assertEquals(ConsoleLibraryStatus.State.SERVER_ERROR,
                ConsoleLibraryStatus.resolve(true, true,
                        PlayniteLibraryRepository.ErrorKind.SERVER, true, false));
    }

    /** A library restored from cache on return from a stream does not push the carousel. */
    @Test
    public void cachedLibraryAwaitingRefreshShowsNoStatusLine() {
        assertFalse(ConsoleLibraryStatus.visible(
                ConsoleLibraryStatus.resolve(false, true, null, true, false)));
        assertFalse(ConsoleLibraryStatus.visible(
                ConsoleLibraryStatus.resolve(true, true, null, true, false)));
        assertTrue(ConsoleLibraryStatus.visible(
                ConsoleLibraryStatus.resolve(true, false, null, true, false)));
        assertTrue(ConsoleLibraryStatus.visible(ConsoleLibraryStatus.State.TIMEOUT));
    }

    /** A host this client put to sleep is shown asleep, never as a timeout or a refresh. */
    @Test
    public void knownSleepOutranksTimeoutAndRefresh() {
        ConsoleLibraryStatus.State asleep = ConsoleLibraryStatus.resolve(false, true,
                PlayniteLibraryRepository.ErrorKind.TIMEOUT, true, true);
        assertEquals(ConsoleLibraryStatus.State.HOST_ASLEEP, asleep);
        assertTrue(ConsoleLibraryStatus.visible(asleep));
        assertFalse(ConsoleLibraryStatus.isError(asleep));
        assertEquals(ConsoleLibraryStatus.State.HOST_ASLEEP,
                ConsoleLibraryStatus.resolve(true, false, null, true, true));
        // A usable library says nothing, asleep or not.
        assertEquals(ConsoleLibraryStatus.State.CURRENT,
                ConsoleLibraryStatus.resolve(true, true, null, true, true));
        assertEquals(ConsoleLibraryStatus.State.TIMEOUT, ConsoleLibraryStatus.resolve(false,
                true, PlayniteLibraryRepository.ErrorKind.TIMEOUT, true, false));
    }
}
