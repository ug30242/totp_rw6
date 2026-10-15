package com.vela.rw6totp;

import java.security.SecureRandom;
import java.util.Locale;
import org.json.JSONObject;

/** Validated local account data; no scanner, URI importer or network access. */
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
    static OtpAccount direct(String name,String secret,String algorithm,String digits,String periodText) {
        try {
            String label=name.trim().isEmpty()?"New account":name.trim();
            if(label.length()>64 || label.matches("(?s).*[\\x00-\\x1f\\x7f].*"))throw new IllegalArgumentException();
            String normalized=secret.replaceAll("[\\s-]+", "").toUpperCase(Locale.ROOT);
            validateBase32(normalized);normalized=normalized.replaceAll("=+$", "");
            String hash=algorithm.toUpperCase(Locale.ROOT).replace("-", "");
            if(!hash.matches("SHA1|SHA256|SHA512") || !digits.matches("6|8") || !periodText.matches("[0-9]{1,4}"))throw new IllegalArgumentException();
            int period=Integer.parseInt(periodText);if(period<5 || period>3600)throw new IllegalArgumentException();
            return new OtpAccount(label,normalized,hash,Integer.parseInt(digits),period);
        } catch(Exception e) { throw new IllegalArgumentException("이름, 인증키와 TOTP 설정을 확인하세요."); }
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
