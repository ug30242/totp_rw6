package com.vela.rw6totp;

import static org.junit.Assert.*;
import java.util.HashSet;
import org.json.JSONObject;
import org.junit.Test;

public class OtpAccountTest {
    @Test public void parsesDefaultsAndCreatesVersionedPacket() throws Exception {
        OtpAccount a = OtpAccount.direct("Example:alice","JBSWY3DPEHPK3PXP","SHA1","6","30");
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
    @Test public void preservesPunctuationAndCanonicalizesBase32() {
        assertEquals("A+B:user",OtpAccount.direct("A+B:user","MY======","SHA-1","6","30").name);
        assertEquals("MY",OtpAccount.direct("a","my","SHA1","6","30").secret);
    }
    @Test public void rejectsMalformedSecretsAndSettings() {
        for(String secret:new String[]{"MZ","M","MY=","MY!","https://example.com"})
            assertThrows(IllegalArgumentException.class,()->OtpAccount.direct("a",secret,"SHA1","6","30"));
        for(String period:new String[]{"30.5","4","3601","+30"})
            assertThrows(IllegalArgumentException.class,()->OtpAccount.direct("a","MY","SHA1","6",period));
        assertThrows(IllegalArgumentException.class,()->OtpAccount.direct("a","MY","MD5","6","30"));
        assertThrows(IllegalArgumentException.class,()->OtpAccount.direct("a","MY","SHA1","7","30"));
    }
    @Test public void enforcesNameAndSecretBoundsAndRedactsErrors() {
        assertThrows(IllegalArgumentException.class,() -> OtpAccount.direct("x".repeat(65),"MY","SHA1","6","30"));
        assertThrows(IllegalArgumentException.class,() -> OtpAccount.direct("line\nbreak","MY","SHA1","6","30"));
        assertThrows(IllegalArgumentException.class,() -> OtpAccount.direct("a","A".repeat(257),"SHA1","6","30"));
        String sensitive="MYPRIVATESECRET!";
        Exception error=assertThrows(IllegalArgumentException.class,() -> OtpAccount.direct("a",sensitive,"SHA1","6","30"));
        assertFalse(error.getMessage().contains(sensitive));
    }
    @Test public void generatesUnique128BitRequestIds() {
        HashSet<String> ids = new HashSet<>();
        for(int i=0;i<1000;i++) {String id=OtpAccount.requestId();assertTrue(id.matches("[a-f0-9]{32}"));assertTrue(ids.add(id));}
    }
}
