package mk.workfashion.personal;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.AtomicFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Exercises real WebView import handlers and native AtomicFile writes. */
public class BackupRestoreInstrumentation extends SmokeInstrumentation {
    private static final String LEGACY="{\"appId\":\"workfashion-personal-time\",\"schemaVersion\":1,\"createdAt\":\"2026-11-01T08:00:00Z\",\"settings\":{\"name\":\"LEGACY_CURRENT\",\"company\":\"Workfashion\",\"trackStart\":\"2026-11-01\",\"start\":\"08:00\",\"end\":\"16:00\",\"workdays\":[1,2,3,4,5],\"dayMinutes\":480,\"weekMinutes\":2400,\"breaksIncluded\":true},\"budgets\":{},\"records\":{\"2026-11-17\":{\"type\":\"work\",\"start\":\"08:00\",\"end\":\"16:00\",\"overnight\":false,\"pause\":0,\"pausePaid\":true,\"note\":\"Keep before restore\"}},\"trips\":[],\"audit\":[],\"lastBackupAt\":null,\"workdaySettings\":{\"mode\":\"geofence\",\"start\":\"08:00\",\"radiusM\":100,\"activatedOn\":\"2026-11-01\",\"workplace\":{\"lat\":41.12,\"lon\":20.8}},\"workdaySessions\":{\"2026-11-18\":{\"date\":\"2026-11-18\",\"startAt\":1794985200000,\"endAt\":1795014000000,\"status\":\"finished\",\"travel\":[]}}}";

    private void seedLegacy()throws Exception{
        AtomicFile file=new AtomicFile(new File(getTargetContext().getFilesDir(),"workfashion-state.json"));
        FileOutputStream out=file.startWrite();
        try{out.write(LEGACY.getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(Exception e){file.failWrite(out);throw e;}
    }
    private void beforeRestorePreserved()throws Exception{
        File file=new File(getTargetContext().getFilesDir(),"before-restore.json");
        JSONObject old=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
        check("LEGACY_CURRENT".equals(old.getJSONObject("settings").getString("name")),"Before-restore safety copy is missing");
        check(old.has("workdaySessions"),"Legacy data was not retained in safety copy");
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            seedLegacy();
            Intent intent=new Intent(getTargetContext(),MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            MainActivity a=(MainActivity)startActivitySync(intent);
            boolean loaded=false;
            for(int i=0;i<80;i++){
                Thread.sleep(250);
                if("true".equals(js(a,"typeof WFApp==='object'&&typeof WFDocuments==='object'&&!!WFApp.getState()"))){loaded=true;break;}
            }
            check(loaded,"WFAG did not load legacy records");
            check("true".equals(js(a,"JSON.parse(Android.loadState()).workdaySettings.mode==='geofence'")),"Fixture does not exercise legacy GPS guard");

            check("true".equals(js(a,
                "(()=>{const old=Android.loadState();const b=Core.fresh();b.settings.name='CANCELLED';window.confirm=()=>false;handleImport(JSON.stringify(b));return Android.loadState()===old;})()"
            )),"Cancelling restore changed saved data");

            check("true".equals(js(a,
                "(()=>{const b=Core.fresh(new Date('2026-11-01T08:00:00Z'));b.settings.trackStart='2026-11-01';b.settings.name='RESTORED_JSON';b.records['2026-11-18']={type:'work',start:'08:00',end:'13:30',overnight:false,pause:0,pausePaid:true,note:'Restored manual correction'};window.confirm=()=>true;handleImport(JSON.stringify(b));const disk=JSON.parse(Android.loadState());return WFApp.getState().settings.name==='RESTORED_JSON'&&disk.settings.name==='RESTORED_JSON'&&disk.records['2026-11-18'].end==='13:30'&&!disk.workdaySettings&&Core.day(disk,'2026-11-18',new Date('2026-11-20T12:00:00Z')).worked===330;})()"
            )),"JSON restore was blocked by legacy workday settings");
            beforeRestorePreserved();

            check("true".equals(js(a,
                "(()=>{const next=WFApp.getState();next.records['2026-11-18'].end='12:15';WFApp.commit(next,'Manual correction after restore');if(!load())return false;return WFApp.getState().records['2026-11-18'].end==='12:15'&&JSON.parse(Android.loadState()).records['2026-11-18'].end==='12:15';})()"
            )),"Manual correction did not survive native reload");

            String saved=new String(java.nio.file.Files.readAllBytes(new File(getTargetContext().getFilesDir(),"workfashion-state.json").toPath()),StandardCharsets.UTF_8);
            ByteArrayOutputStream zip=new ByteArrayOutputStream();
            Documents.exportArchive(a,zip,saved,RouteTrackingService.stateJson(a));
            seedLegacy();
            check("true".equals(js(a,"load()&&JSON.parse(Android.loadState()).workdaySettings.mode==='geofence'")),"ZIP legacy fixture failed to reload");
            File stage=new File(a.getCacheDir(),"wfag-restore-stage");
            String staged=Documents.stageArchive(a,new ByteArrayInputStream(zip.toByteArray()),stage);
            check("true".equals(js(a,
                "window.confirm=()=>true;handleImport("+JSONObject.quote(staged)+");(()=>{const disk=JSON.parse(Android.loadState());return disk.settings.name==='RESTORED_JSON'&&disk.records['2026-11-18'].end==='12:15'&&WFApp.getState().settings.name==='RESTORED_JSON'&&!Android.hasStagedArchive();})()"
            )),"Full ZIP backup restore failed");
            beforeRestorePreserved();

            check("true".equals(js(a,
                "(()=>{const before=Android.loadState();const rejected=!Android.saveState('not-json');return rejected&&Android.loadState()===before&&Android.getLastSaveError().length>0&&!Android.getLastSaveError().includes('слободниот простор');})()"
            )),"Invalid JSON changed data or gave misleading storage error");
            check("true".equals(js(a,
                "(()=>{const before=Android.loadState();return !Android.saveStateWithAdmin(before,'wrong-password')&&Android.loadState()===before;})()"
            )),"Admin verification was bypassed");
            check("true".equals(js(a,
                "(()=>{const s=Android.loadState();return Android.saveState(s)&&Android.getLastSaveError()==='';})()"
            )),"Valid save did not clear previous error");

            result.putString("stream","WFAG_BACKUP_RESTORE_OK: legacy GPS state, cancelled restore, old JSON restore, manual correction and reload, complete ZIP roundtrip, before-restore preservation, invalid JSON rejection, admin verification\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable t){
            java.io.StringWriter trace=new java.io.StringWriter();t.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream","WFAG_BACKUP_RESTORE_FAILED: "+trace+"\n");
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
