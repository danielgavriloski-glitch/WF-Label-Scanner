package mk.workfashion.personal;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

public class SmokeInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    String js(MainActivity a,String script)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);String[] result={null};
        runOnMainSync(()->a.webView.evaluateJavascript(script,v->{result[0]=v;latch.countDown();}));
        if(!latch.await(20,TimeUnit.SECONDS))throw new Exception("JavaScript timed out");
        return result[0];
    }
    void check(boolean b,String message)throws Exception{if(!b)throw new Exception(message);}
    @Override public void onStart(){
        Bundle result=new Bundle();try{
            Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            MainActivity a=(MainActivity)startActivitySync(intent);
            boolean loaded=false;for(int i=0;i<80;i++){Thread.sleep(250);if("true".equals(js(a,"typeof WFApp!=='undefined'&&!!WFApp.getState()"))){loaded=true;break;}}
            check(loaded,"UI did not load");
            check("true".equals(js(a,"WFApp.getState().settings.start==='08:00'&&WFApp.getState().settings.end==='16:00'")),"Incorrect default schedule");
            check("false".equals(js(a,"Android.saveState('malformed')")),"Invalid JSON accepted");
            check("true".equals(js(a,"(()=>{let s=WFApp.getState();s.settings.trackStart='2026-10-01';s.settings.name='Тест Вработен';s.budgets['2026']={annual:20,carry:2,previous:1};s.records['2026-10-05']={type:'vacation',pause:0,note:'Тест на годишен одмор'};commit(s,'Проверка на зачувување');return true;})()")),"Persistence failed");
            for(String v:new String[]{"history","leave","reports","settings","home"})check("true".equals(js(a,"WFApp.setView('"+v+"');document.getElementById('app').textContent.length>100")),"Missing screen: "+v);
            check("true".equals(js(a,"JSON.parse(Android.loadState()).settings.name==='Тест Вработен'")),"Native state not retained");
            check("true".equals(js(a,"(()=>{editDay('2026-10-06','sick',true);document.getElementById('editTo').value='2026-10-07';document.getElementById('dayForm').dispatchEvent(new Event('submit',{cancelable:true}));return WFApp.getState().records['2026-10-07'].type==='sick';})()")),"Absence range form failed");
            String encoded=js(a,"JSON.stringify(reportJson(Core.period(WFApp.getState(),'2026-10-01','2026-10-31',new Date('2026-11-01T12:00:00Z'))))");
            JSONObject report=new JSONObject(new JSONArray("["+encoded+"]").getString(0));
            byte[] pdf=MainActivity.PdfReport.create(report);check(pdf.length>2000,"PDF is empty");
            check(new String(pdf,0,4,"US-ASCII").equals("%PDF"),"Invalid PDF");
            File file=new File(getTargetContext().getExternalFilesDir(null),"smoke-report.pdf");try(FileOutputStream out=new FileOutputStream(file)){out.write(pdf);}
            js(a,"WFApp.setView('home')");
            result.putString("stream","WF_SMOKE_OK: native startup, five screens, persistence, invalid-state rejection, Cyrillic monthly PDF\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable t){result.putString("stream","WF_SMOKE_FAILED: "+t.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
