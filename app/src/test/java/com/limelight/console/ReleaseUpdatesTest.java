package com.limelight.console;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class ReleaseUpdatesTest {
    private static final String PACKAGE = "app.moonwaker.tv";
    private static final Ed25519PrivateKeyParameters OWNER =
            new Ed25519PrivateKeyParameters(new SecureRandom());
    private static final String OWNER_KEY =
            Base64.getEncoder().encodeToString(OWNER.generatePublicKey().getEncoded());
    private static final String MANIFEST = "{\"schema\": 1, \"apk\": {\"package\": \"" + PACKAGE
            + "\", \"version_code\": 801, \"version_name\": \"0.8.1\","
            + " \"url\": \"https://github.com/Maladie/moonwaker/releases/download/v0.8.1/a.apk\","
            + " \"sha256\": \"ab\", \"size\": 42}, \"host\": {\"version\": \"0.7.173\"}}\n";

    private static String sign(Ed25519PrivateKeyParameters key, byte[] bytes) {
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, key);
        signer.update(bytes, 0, bytes.length);
        return Base64.getEncoder().encodeToString(signer.generateSignature());
    }

    private static JSONObject response(String manifest, String signature, int protocol)
            throws Exception {
        byte[] bytes = manifest.getBytes(StandardCharsets.UTF_8);
        return new JSONObject()
                .put("host", new JSONObject().put("version", "0.7.172")
                        .put("protocol_version", protocol))
                .put("release", new JSONObject().put("state", "published")
                        .put("manifest", Base64.getEncoder().encodeToString(bytes))
                        .put("signature", signature));
    }

    private static JSONObject signed(String manifest) throws Exception {
        return response(manifest, sign(OWNER, manifest.getBytes(StandardCharsets.UTF_8)), 7);
    }

    @Test
    public void ownerSignedReleaseOffersTheNewerApkAndHost() throws Exception {
        ReleaseUpdates updates = ReleaseUpdates.parse(signed(MANIFEST), OWNER_KEY, PACKAGE);
        assertEquals(801, updates.apk.versionCode);
        assertEquals("0.8.1", updates.apk.versionName);
        assertEquals(42, updates.apk.size);
        assertTrue(updates.appUpdate(800));
        assertFalse(updates.appUpdate(801));
        assertTrue(updates.hostUpdate());
        assertEquals(ReleaseUpdates.Compatibility.COMPATIBLE, updates.compatibility());
    }

    @Test
    public void nothingCountsWithoutTheOwnersSignature() throws Exception {
        Ed25519PrivateKeyParameters stranger = new Ed25519PrivateKeyParameters(new SecureRandom());
        String tampered = MANIFEST.replace("801", "999");
        for (JSONObject response : new JSONObject[]{
                response(MANIFEST, sign(stranger, MANIFEST.getBytes(StandardCharsets.UTF_8)), 7),
                response(tampered, sign(OWNER, MANIFEST.getBytes(StandardCharsets.UTF_8)), 7),
                response(MANIFEST, "not base64!", 7),
                new JSONObject().put("host", new JSONObject().put("version", "0.7.172"))
                        .put("release", new JSONObject().put("state", "none"))}) {
            ReleaseUpdates updates = ReleaseUpdates.parse(response, OWNER_KEY, PACKAGE);
            assertNull(updates.apk);
            assertFalse(updates.appUpdate(1));
            assertFalse(updates.hostUpdate());
        }
        // A build without the public key verifies nothing.
        assertNull(ReleaseUpdates.parse(signed(MANIFEST), "", PACKAGE).apk);
    }

    @Test
    public void apkIsOfferedOnlyToItsOwnPackageOverHttps() throws Exception {
        assertNull(ReleaseUpdates.parse(signed(MANIFEST), OWNER_KEY, PACKAGE + ".test").apk);
        String http = MANIFEST.replace("https://", "http://");
        assertNull(ReleaseUpdates.parse(signed(http), OWNER_KEY, PACKAGE).apk);
    }

    @Test
    public void protocolOutsideTheAppsRangeNamesTheSideToUpdate() throws Exception {
        String signature = sign(OWNER, MANIFEST.getBytes(StandardCharsets.UTF_8));
        assertEquals(ReleaseUpdates.Compatibility.HOST_OLDER, ReleaseUpdates.parse(
                response(MANIFEST, signature, ReleaseUpdates.MIN_HOST_PROTOCOL - 1),
                OWNER_KEY, PACKAGE).compatibility());
        assertEquals(ReleaseUpdates.Compatibility.APP_OLDER, ReleaseUpdates.parse(
                response(MANIFEST, signature, ReleaseUpdates.MAX_HOST_PROTOCOL + 1),
                OWNER_KEY, PACKAGE).compatibility());
        assertNull(ReleaseUpdates.parse(new JSONObject(), OWNER_KEY, PACKAGE).compatibility());
    }

    @Test
    public void versionsCompareByNumberPerPart() {
        assertTrue(ReleaseUpdates.compareVersions("0.7.173", "0.7.172") > 0);
        assertTrue(ReleaseUpdates.compareVersions("0.7.99", "0.7.172") < 0);
        assertEquals(0, ReleaseUpdates.compareVersions("0.8", "0.8.0"));
        assertTrue(ReleaseUpdates.compareVersions("0.8.0", "unknown") > 0);
    }
}
