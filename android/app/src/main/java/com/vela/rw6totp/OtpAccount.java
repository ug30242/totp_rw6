package com.vela.rw6totp;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

/** Strict local TOTP URI parser. Never opens or resolves a scanned URI. */
final class OtpAccount {
    final String name, secret, algorithm, syncId;
    final int digits, period;
    private OtpAccount(String name, String secret, String algorithm, int digits, int period) {
        this(name,secret,algorithm,digits,period,requestId());
    }
    private OtpAccount(String name,String secret,String algorithm,int digits,int period,String syncId) {
        this.name = name; this.secret = secret; this.algorithm = algorithm; this.digits = digits; this.period = period;
        this.syncId = syncId;
    }
    static OtpAccount parse(String raw) {
        try {
            if (raw == null || raw.length() > 4096) throw new IllegalArgumentException();
            URI uri = new URI(raw.trim());
            if (!"otpauth".equalsIgnoreCase(uri.getScheme()) || !"totp".equalsIgnoreCase(uri.getRawAuthority()) ||
                    uri.getFragment() != null || uri.getRawPath() == null || !uri.getRawPath().startsWith("/")) {
                throw new IllegalArgumentException();
            }
            Map<String,String> params = new HashMap<>();
            String query = uri.getRawQuery();
            if (query == null) throw new IllegalArgumentException();
            for (String part : query.split("&")) {
                String[] pair = part.split("=", 2);
                String key = decode(pair[0]);
                if (params.containsKey(key)) throw new IllegalArgumentException();
                params.put(key, pair.length == 2 ? decode(pair[1]) : "");
            }
            String secret = params.getOrDefault("secret", "").replaceAll("\\s", "").toUpperCase(Locale.ROOT);
            validateBase32(secret);
            secret = secret.replaceAll("=+$", "");
            String algorithm = params.getOrDefault("algorithm", "SHA1").toUpperCase(Locale.ROOT);
            if (!algorithm.matches("SHA1|SHA256|SHA512")) throw new IllegalArgumentException();
            String digitsText = params.getOrDefault("digits", "6");
            String periodText = params.getOrDefault("period", "30");
            if (!digitsText.matches("6|8") || !periodText.matches("[0-9]{1,4}")) throw new IllegalArgumentException();
            int period = Integer.parseInt(periodText);
            if (period < 5 || period > 3600) throw new IllegalArgumentException();
            String name = decode(uri.getRawPath().substring(1).replace("+", "%2B"));
            String issuer = params.getOrDefault("issuer", "");
            if (name.isEmpty()) name = issuer.isEmpty() ? "New account" : issuer;
            if (!issuer.isEmpty() && !name.startsWith(issuer + ":")) name = issuer + ":" + name;
            if (name.length() > 64 || name.matches("(?s).*[\\x00-\\x1f\\x7f].*")) throw new IllegalArgumentException();
            return new OtpAccount(name, secret, algorithm, Integer.parseInt(digitsText), period);
        } catch (Exception e) {
            // Do not include the scanned data or secret in errors/logs.
            throw new IllegalArgumentException("유효한 TOTP 계정 QR/URI가 아닙니다. SHA1/256/512, 6/8자리, 5~3600초를 지원합니다.");
        }
    }
    private static String decode(String text) throws Exception {
        return URLDecoder.decode(text, StandardCharsets.UTF_8.name());
    }
    private static void validateBase32(String secret) {
        if (secret.isEmpty() || secret.length() > 256 || !secret.matches("[A-Z2-7]+=*")) throw new IllegalArgumentException();
        String bare = secret.replaceAll("=+$", "");
        int remainder = bare.length() % 8;
        if (remainder == 1 || remainder == 3 || remainder == 6) throw new IllegalArgumentException();
        int padding = secret.length() - bare.length();
        if (padding > 0 && (secret.length() % 8 != 0 || padding != (8 - remainder) % 8)) throw new IllegalArgumentException();
        int bits = bare.length() * 5 % 8;
        int last = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(bare.charAt(bare.length() - 1));
        if (bits > 0 && (last & ((1 << bits) - 1)) != 0) throw new IllegalArgumentException();
    }
    static String requestId() {
        byte[] bytes = new byte[16]; new SecureRandom().nextBytes(bytes);
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
    static OtpAccount direct(String name, String secret, String algorithm, String digits, String period) {
        try {
            String label = name.trim().isEmpty() ? "New account" : name.trim();
            String uri = "otpauth://totp/" + java.net.URLEncoder.encode(label, "UTF-8").replace("+", "%20")
                + "?secret=" + java.net.URLEncoder.encode(secret, "UTF-8")
                + "&algorithm=" + java.net.URLEncoder.encode(algorithm, "UTF-8")
                + "&digits=" + java.net.URLEncoder.encode(digits, "UTF-8")
                + "&period=" + java.net.URLEncoder.encode(period, "UTF-8");
            return parse(uri);
        } catch (Exception e) { throw new IllegalArgumentException("이름, Base32 인증키와 TOTP 설정을 확인하세요."); }
    }
    JSONObject toJson() throws Exception {
        return new JSONObject().put("name", name).put("secret", secret)
            .put("algorithm", algorithm).put("digits", digits).put("period", period).put("syncId",syncId);
    }
    static OtpAccount fromJson(JSONObject json) throws Exception {
        return direct(json.getString("name"), json.getString("secret"), json.getString("algorithm"),
            String.valueOf(json.getInt("digits")), String.valueOf(json.getInt("period"))).withSyncId(json.getString("syncId"));
    }
    OtpAccount withSyncId(String id) {
        if (id == null || !id.matches("[a-f0-9]{32}")) throw new IllegalArgumentException("Invalid sync ID");
        return new OtpAccount(name,secret,algorithm,digits,period,id);
    }
    String identity() { return name + '\0' + secret + '\0' + algorithm + '\0' + digits + '\0' + period; }
    String packet(String id) throws Exception {
        return new JSONObject().put("type", "rw6-totp-sync").put("version", 1).put("id", id).put("account", toJson()).toString();
    }
}
