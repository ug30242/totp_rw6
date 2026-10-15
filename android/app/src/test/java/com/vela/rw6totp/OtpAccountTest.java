package com.vela.rw6totp;

import static org.junit.Assert.*;
import java.util.HashSet;
import org.json.JSONObject;
import org.junit.Test;

public class OtpAccountTest {
    private static final String URI = "otpauth://totp/Example:alice?secret=JBSWY3DPEHPK3PXP&issuer=Example";
    @Test public void parsesDefaultsAndCreatesVersionedPacket() throws Exception {
        OtpAccount a = OtpAccount.parse(URI);
        assertEquals("Example:alice",a.name); assertEquals("SHA1",a.algorithm); assertEquals(6,a.digits); assertEquals(30,a.period);
        JSONObject json = new JSONObject(a.packet("a".repeat(32)));
        assertEquals("rw6-totp-sync",json.getString("type")); assertEquals(1,json.getInt("version"));
        assertEquals(a.secret,json.getJSONObject("account").getString("secret"));
        assertTrue(a.packet("a".repeat(32)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 2048);
    }
    @Test public void directEntryRequiresNoQrAndRoundTripsVaultJson() throws Exception {
        OtpAccount a = OtpAccount.direct("네이버 + alice","jbsw y3dp ehpk 3pxp","SHA512","8","60");
        assertEquals("네이버 + alice",a.name); assertEquals("JBSWY3DPEHPK3PXP",a.secret);
        assertEquals(a.identity(),OtpAccount.fromJson(a.toJson()).identity());
        assertEquals(a.syncId,OtpAccount.fromJson(a.toJson()).syncId);
    }
    @Test public void preservesPlusInLabelAndDecodesQueryIssuer() {
        assertEquals("A+B",OtpAccount.parse("otpauth://totp/A+B?secret=MY======").name);
        assertEquals("A B:user",OtpAccount.parse("otpauth://totp/user?secret=MY&issuer=A+B").name);
    }
    @Test public void rejectsWebLinksHotpMigrationAndAmbiguousUris() {
        for(String text : new String[]{"https://example.com/","otpauth://hotp/a?secret=MY","otpauth-migration://offline?data=123",
            URI+"&secret=MY",URI+"#secret",URI+"&period=30.5",URI+"&period=4",URI+"&period=3601",
            URI+"&algorithm=MD5",URI+"&digits=7","otpauth://totp@evil/a?secret=MY","otpauth://totp/a?secret=MZ",
            "otpauth://totp/a?secret=M","otpauth://totp/a?secret=MY=","otpauth://totp/a?secret=MY!"}) {
            assertThrows(text,IllegalArgumentException.class,() -> OtpAccount.parse(text));
        }
    }
    @Test public void enforcesNameAndSecretBoundsAndRedactsErrors() {
        assertThrows(IllegalArgumentException.class,() -> OtpAccount.direct("x".repeat(65),"MY","SHA1","6","30"));
        assertThrows(IllegalArgumentException.class,() -> OtpAccount.direct("line\nbreak","MY","SHA1","6","30"));
        assertThrows(IllegalArgumentException.class,() -> OtpAccount.direct("a","A".repeat(257),"SHA1","6","30"));
        String sensitive="MYPRIVATESECRET!";
        Exception error=assertThrows(IllegalArgumentException.class,() -> OtpAccount.parse("otpauth://totp/a?secret="+sensitive));
        assertFalse(error.getMessage().contains(sensitive));
    }
    @Test public void generatesUnique128BitRequestIds() {
        HashSet<String> ids = new HashSet<>();
        for(int i=0;i<1000;i++) {String id=OtpAccount.requestId();assertTrue(id.matches("[a-f0-9]{32}"));assertTrue(ids.add(id));}
    }
}
