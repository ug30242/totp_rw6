package com.vela.rw6totp;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class InteropTest {
    @Test public void emitsRealAndroidPacketForWatchIntegration() throws Exception {
        OtpAccount account=OtpAccount.direct("RFC 6238","GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ","SHA1","6","30")
            .withSyncId("f".repeat(32));
        Path file=Path.of(System.getProperty("rw6.interop.output"));Files.createDirectories(file.getParent());
        Files.write(file,account.packet("a".repeat(32)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(Files.size(file)>0);
    }
}
