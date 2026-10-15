package com.vela.rw6totp;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** One acknowledged account at a time. A send callback alone never means saved. */
final class SyncSession {
    interface Transport { void send(String packet); void openWatch(); }
    interface Events { void status(String text); void finished(boolean success); }
    interface Timer { void after(Runnable action, long millis); }
    private final Transport transport;
    private final Events events;
    private final Timer timer;
    private List<OtpAccount> accounts = new ArrayList<>();
    private String helloId, requestId;
    private int offset, attempts, epoch;
    private boolean running;
    SyncSession(Transport transport, Events events, Timer timer) { this.transport = transport; this.events = events; this.timer = timer; }
    boolean isRunning() { return running; }
    void start(List<OtpAccount> items) {
        if (running) return;
        if (items.isEmpty() || items.size() > 50) { events.status("동기화할 계정을 먼저 추가하세요. 최대 50개입니다."); return; }
        epoch++; running = true; offset = 0; attempts = 0; accounts = new ArrayList<>(items);
        helloId = OtpAccount.requestId(); requestId = null;
        events.status("워치 TOTP를 여는 중… 필요하면 워치에서 + Phone을 직접 여세요.");
        transport.openWatch(); hello(epoch);
    }
    private void hello(int current) {
        if (!running || current != epoch || helloId == null) return;
        if (++attempts > 15) { fail("워치 응답이 없습니다. TOTP > + Phone을 열고 다시 동기화하세요."); return; }
        try { transport.send(new JSONObject().put("type", "rw6-totp-hello").put("version",1).put("id",helloId).toString()); }
        catch (Exception e) { fail("연결 확인 실패. 다시 연결하세요."); return; }
        timer.after(() -> hello(current), 1000);
    }
    void receive(String raw) {
        if (!running || raw == null || raw.length() > 2048) return;
        try {
            JSONObject packet = new JSONObject(raw);
            // Vela's send(Object) arrives as UTF-8 JSON; our payload is in data.
            if (!packet.has("type") && packet.opt("data") instanceof String) packet = new JSONObject(packet.getString("data"));
            if (packet.optInt("version") != 1) return;
            String type = packet.optString("type"), id = packet.optString("id");
            if ("rw6-totp-ready".equals(type) && helloId != null && helloId.equals(id)) {
                helloId = null; next(); return;
            }
            if (!"rw6-totp-result".equals(type) || requestId == null || !requestId.equals(id)) return;
            String status = packet.optString("status");
            if (!"saved".equals(status)) { fail("워치 저장 실패 (" + safeStatus(status) + "). " + offset + "개 확인됨. 기존 워치 계정은 유지됩니다."); return; }
            offset++; requestId = null;
            if (offset == accounts.size()) {
                int count = offset; clear(); events.status(count + "개 계정 동기화 완료. 워치 저장 응답을 확인했습니다."); events.finished(true);
            } else next();
        } catch (Exception ignored) {}
    }
    private String safeStatus(String status) {
        switch (status) {
            case "busy": return "워치 사용 중";
            case "invalid": return "형식 오류";
            case "error": return "저장소 오류";
            case "cancelled": return "취소";
            default: return "응답 오류";
        }
    }
    private void next() {
        if (!running) return;
        try {
            requestId = OtpAccount.requestId();
            String id = requestId; int current = epoch;
            String packet = accounts.get(offset).packet(id);
            events.status("동기화 중 " + (offset + 1) + " / " + accounts.size() + "…");
            transport.send(packet);
            timer.after(() -> {
                if (running && current == epoch && id.equals(requestId)) fail("저장 응답 시간 초과. 워치 목록을 확인한 후 다시 동기화하세요. 같은 계정은 중복 추가하지 않습니다.");
            }, 15000);
        } catch (Exception e) { fail("동기화 준비 실패. 계정 설정을 확인하세요."); }
    }
    void fail(String text) { if (!running) return; clear(); events.status(text); events.finished(false); }
    void cancel() { if (running) { clear(); events.finished(false); } }
    private void clear() { epoch++; running = false; helloId = null; requestId = null; accounts.clear(); }
}
