package com.limelight.console;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * What the host's {@code /api/v1/updates} says (#56 U2, REMOTE_UPDATES_DESIGN_PL.md): this
 * host's version and protocol, and the latest published release. The Gateway hands the
 * release over as GitHub served it; nothing in it counts without the owner's Ed25519
 * signature over its exact bytes, checked here against the key built into the app.
 */
final class ReleaseUpdates {
    /** The Gateway protocol versions this app speaks; outside them TV and host disagree. */
    static final int MIN_HOST_PROTOCOL = 7;
    static final int MAX_HOST_PROTOCOL = 7;

    enum Compatibility { COMPATIBLE, HOST_OLDER, APP_OLDER }

    /** A release APK for this app's own package. */
    static final class Apk {
        final long versionCode;
        final String versionName;
        final String url;
        final String sha256;
        final long size;

        Apk(long versionCode, String versionName, String url, String sha256, long size) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.url = url;
            this.sha256 = sha256;
            this.size = size;
        }
    }

    final String hostVersion;
    /** -1 when the host does not say. */
    final int hostProtocol;
    /** Null without a verified release, or when it is not this app's package. */
    final Apk apk;
    /** The verified release's host version; empty without one. */
    final String releaseHostVersion;

    private ReleaseUpdates(String hostVersion, int hostProtocol, Apk apk,
                           String releaseHostVersion) {
        this.hostVersion = hostVersion;
        this.hostProtocol = hostProtocol;
        this.apk = apk;
        this.releaseHostVersion = releaseHostVersion;
    }

    static ReleaseUpdates parse(JSONObject response, String publicKey, String packageName) {
        JSONObject host = response.optJSONObject("host");
        String hostVersion = host == null ? "" : host.optString("version", "");
        int hostProtocol = host == null ? -1 : host.optInt("protocol_version", -1);
        JSONObject manifest = verifiedManifest(response.optJSONObject("release"), publicKey);
        if (manifest == null) return new ReleaseUpdates(hostVersion, hostProtocol, null, "");
        JSONObject apk = manifest.optJSONObject("apk");
        JSONObject releaseHost = manifest.optJSONObject("host");
        return new ReleaseUpdates(hostVersion, hostProtocol,
                apk != null && packageName.equals(apk.optString("package"))
                        && apk.optString("url").startsWith("https://")
                        ? new Apk(apk.optLong("version_code"), apk.optString("version_name"),
                        apk.optString("url"), apk.optString("sha256"), apk.optLong("size"))
                        : null,
                releaseHost == null ? "" : releaseHost.optString("version", ""));
    }

    /** The release's manifest when the owner signed it; null otherwise. */
    static JSONObject verifiedManifest(JSONObject release, String publicKey) {
        if (release == null || publicKey.isEmpty()) return null;
        try {
            byte[] manifest = Base64.getDecoder().decode(release.optString("manifest"));
            byte[] signature = Base64.getDecoder().decode(release.optString("signature"));
            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, new Ed25519PublicKeyParameters(
                    Base64.getDecoder().decode(publicKey), 0));
            verifier.update(manifest, 0, manifest.length);
            if (!verifier.verifySignature(signature)) return null;
            return new JSONObject(new String(manifest, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | JSONException malformed) {
            return null;
        }
    }

    /** Null when the host does not say which protocol it speaks. */
    Compatibility compatibility() {
        if (hostProtocol < 0) return null;
        if (hostProtocol < MIN_HOST_PROTOCOL) return Compatibility.HOST_OLDER;
        if (hostProtocol > MAX_HOST_PROTOCOL) return Compatibility.APP_OLDER;
        return Compatibility.COMPATIBLE;
    }

    boolean appUpdate(long installedVersionCode) {
        return apk != null && apk.versionCode > installedVersionCode;
    }

    boolean hostUpdate() {
        return !releaseHostVersion.isEmpty() && compareVersions(releaseHostVersion, hostVersion) > 0;
    }

    /** Dotted numeric versions ("0.7.172"); a part that is not a number counts as 0. */
    static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int index = 0; index < Math.max(a.length, b.length); index++) {
            int difference = Long.compare(part(a, index), part(b, index));
            if (difference != 0) return difference;
        }
        return 0;
    }

    private static long part(String[] parts, int index) {
        try {
            return index < parts.length ? Long.parseLong(parts[index]) : 0L;
        } catch (NumberFormatException notNumeric) {
            return 0L;
        }
    }
}
