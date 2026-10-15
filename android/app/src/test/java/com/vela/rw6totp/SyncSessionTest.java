package com.vela.rw6totp;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

public class SyncSessionTest {
    static final class Fixture implements SyncSession.Transport,SyncSession.Events,SyncSession.Timer {
        final List<String> sent=new ArrayList<>(),statuses=new ArrayList<>();
        final List<Runnable> timers=new ArrayList<>();
        final SyncSession session=new SyncSession(this,this,this);
        int opened,successes,failures;
        @Override public void send(String packet){sent.add(packet);}
        @Override public void openWatch(){opened++;}
        @Override public void status(String text){statuses.add(text);}
        @Override public void finished(boolean success){if(success)successes++;else failures++;}
        @Override public void after(Runnable action,long ms){timers.add(action);}
        JSONObject last() throws Exception{return new JSONObject(sent.get(sent.size()-1));}
        void response(String type,String id,String status) throws Exception{
            session.receive(new JSONObject().put("type",type).put("version",1).put("id",id).put("status",status).toString());
        }
        void ready() throws Exception{response("rw6-totp-ready",last().getString("id"),"");}
        void saved() throws Exception{response("rw6-totp-result",last().getString("id"),"saved");}
    }
    private OtpAccount a(String name){return OtpAccount.direct(name,"JBSWY3DPEHPK3PXP","SHA1","6","30");}
    @Test public void opensWatchHandshakesThenWaitsForEachPersistentAck() throws Exception{
        Fixture f=new Fixture();f.session.start(List.of(a("one"),a("two")));
        assertEquals(1,f.opened);assertEquals("rw6-totp-hello",f.last().getString("type"));
        assertFalse(f.sent.get(0).contains("secret"));
        f.ready();assertEquals("one",f.last().getJSONObject("account").getString("name"));
        assertEquals(0,f.successes);assertEquals(2,f.sent.size());
        f.saved();assertEquals("two",f.last().getJSONObject("account").getString("name"));
        f.saved();assertEquals(1,f.successes);assertFalse(f.session.isRunning());
        for(Runnable timeout:new ArrayList<>(f.timers))timeout.run();assertEquals(0,f.failures);
    }
    @Test public void ignoresWrongIdsVersionsAndDuplicateAcks() throws Exception{
        Fixture f=new Fixture();f.session.start(List.of(a("one"),a("two")));String hello=f.last().getString("id");
        f.response("rw6-totp-ready","wrong","");assertEquals(1,f.sent.size());
        f.session.receive("{broken");f.ready();String first=f.last().getString("id");f.saved();
        f.response("rw6-totp-result",first,"saved");f.response("rw6-totp-ready",hello,"");assertEquals(3,f.sent.size());
        f.session.receive(f.last().put("type","rw6-totp-result").put("version",2).put("status","saved").toString());
        assertEquals(0,f.successes);f.saved();assertEquals(1,f.successes);
    }
    @Test public void failedSaveStopsQueueWithoutClaimingSuccess() throws Exception{
        Fixture f=new Fixture();f.session.start(List.of(a("one"),a("two")));f.ready();
        f.response("rw6-totp-result",f.last().getString("id"),"error");
        assertEquals(1,f.failures);assertEquals(0,f.successes);assertEquals(2,f.sent.size());assertFalse(f.session.isRunning());
    }
    @Test public void timesOutWithoutAnAckAndDoesNotSendNextAccount() throws Exception{
        Fixture f=new Fixture();f.session.start(List.of(a("one"),a("two")));f.ready();
        f.timers.get(f.timers.size()-1).run();assertEquals(1,f.failures);assertEquals(2,f.sent.size());
    }
    @Test public void handshakeTimeoutNeverTransmitsSecrets(){
        Fixture f=new Fixture();f.session.start(List.of(a("one")));
        for(int i=0;i<15;i++)f.timers.get(f.timers.size()-1).run();
        assertEquals(1,f.failures);assertEquals(15,f.sent.size());assertTrue(f.sent.stream().noneMatch(s->s.contains("secret")));
    }
    @Test public void cancelAndRestartDiscardOldCallbacks() throws Exception{
        Fixture f=new Fixture();f.session.start(List.of(a("one")));Runnable oldTimer=f.timers.get(0);String oldId=f.last().getString("id");
        f.session.cancel();f.session.start(List.of(a("two")));oldTimer.run();f.response("rw6-totp-ready",oldId,"");
        assertEquals(2,f.sent.size());f.ready();f.saved();assertEquals(1,f.successes);
    }
    @Test public void rejectsEmptyListsAndDoesNotStartTwice(){
        Fixture f=new Fixture();f.session.start(List.of());assertEquals(0,f.opened);
        f.session.start(List.of(a("one")));f.session.start(List.of(a("two")));assertEquals(1,f.opened);
    }
    @Test public void acceptsTheActualVelaObjectEnvelope() throws Exception{
        Fixture f=new Fixture();f.session.start(List.of(a("one")));
        JSONObject ready=new JSONObject().put("type","rw6-totp-ready").put("version",1).put("id",f.last().getString("id"));
        f.session.receive(new JSONObject().put("data",ready.toString()).toString());
        assertEquals("rw6-totp-sync",f.last().getString("type"));
        JSONObject saved=new JSONObject().put("type","rw6-totp-result").put("version",1).put("id",f.last().getString("id")).put("status","saved");
        f.session.receive(new JSONObject().put("data",saved.toString()).toString());assertEquals(1,f.successes);
    }
}
