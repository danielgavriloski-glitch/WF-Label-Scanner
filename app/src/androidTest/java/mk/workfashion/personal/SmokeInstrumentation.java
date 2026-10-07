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
    void check(boolean ok,String message)throws Exception{if(!ok)throw new Exception(message);}

    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Intent intent=new Intent(getTargetContext(),MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            MainActivity a=(MainActivity)startActivitySync(intent);

            boolean loaded=false;
            for(int i=0;i<80;i++){
                Thread.sleep(250);
                if("true".equals(js(a,"typeof WFApp!=='undefined'&&!!WFApp.getState()&&typeof WFApp.travelReportJson==='function'"))){loaded=true;break;}
            }
            check(loaded,"WFAG v1.2 UI did not load");
            check("true".equals(js(a,"document.querySelector('.logo').textContent==='WFAG'")),"WFAG brand missing");
            check("true".equals(js(a,"WFApp.getState().settings.start==='08:00'&&WFApp.getState().settings.end==='16:00'")),"Incorrect default schedule");
            check("false".equals(js(a,"Android.saveState('malformed')")),"Invalid JSON accepted");

            check("true".equals(js(a,
                "(()=>{let s=Core.fresh(new Date('2026-10-01T08:00:00Z'));s.settings.trackStart='2026-10-01';s.settings.name='Тест Вработен';s.budgets['2026']={annual:20,carry:2,previous:1};if(!Android.saveState(JSON.stringify(s))||!load())return false;render();return state.trips.length===0&&state.settings.name==='Тест Вработен';})()"
            )),"Known state setup failed");

            for(String v:new String[]{"history","leave","reports","settings","home"}){
                check("true".equals(js(a,"WFApp.setView('"+v+"');document.getElementById('app').textContent.length>100")),"Missing screen: "+v);
            }

            check("true".equals(js(a,
                "(()=>{WFApp.editTrip(null,'2026-10-07');if(!document.getElementById('workStartStandard').checked)return false;document.getElementById('tripDeparture').value='10:00';document.getElementById('tripArrivalDate').value='2026-10-07';document.getElementById('tripArrival').value='19:00';document.getElementById('tripNote').value='Велес – Скопје';document.getElementById('tripForm').dispatchEvent(new Event('submit',{cancelable:true}));return state.trips.length===1&&state.trips[0].workStart==='08:00'&&state.trips[0].arrival==='19:00';})()"
            )),"Standard 08:00 trip form failed");

            check("true".equals(js(a,
                "Core.day(state,'2026-10-07',new Date('2026-10-08T12:00:00Z')).worked===660"
            )),"Travel did not extend workday to 11 hours");

            check("true".equals(js(a,
                "(()=>{let s=WFApp.getState();s=Core.putTrip(s,{date:'2026-10-08',workStart:'08:00',departure:'10:00',arrivalDate:'2026-10-08',arrival:'22:00',note:'Подолг пат'}).state;s.records['2026-10-09']={type:'off',pause:0,note:'Слободен ден од прекувремени'};commit(s,'Smoke v1.2');const p=Core.period(state,'2026-10-05','2026-10-11',new Date('2026-10-12T12:00:00Z'));return p.grossOvertime===540&&p.compDays===1&&p.compTime===480&&p.overtime===60;})()"
            )),"Free day overtime deduction failed");

            check("true".equals(js(a,
                "(()=>{let s=WFApp.getState();s.records['2026-10-12']={type:'holiday',pause:0,note:'Празник'};Core.validate(s);const p=Core.period(s,'2026-10-12','2026-10-12',new Date('2026-10-13T12:00:00Z'));return p.counts.holiday===1&&p.worked===0&&Core.TYPES.holiday.indexOf('празник')>=0;})()"
            )),"Holiday reporting changed worked hours");

            String workEncoded=js(a,
                "JSON.stringify(WFApp.reportJson(Core.period(state,'2026-10-05','2026-10-11',new Date('2026-10-12T12:00:00Z'))))"
            );
            String travelEncoded=js(a,
                "JSON.stringify(WFApp.travelReportJson(Core.period(state,'2026-10-05','2026-10-11',new Date('2026-10-12T12:00:00Z'))))"
            );
            JSONObject workReport=new JSONObject(new JSONArray("["+workEncoded+"]").getString(0));
            JSONObject travelReport=new JSONObject(new JSONArray("["+travelEncoded+"]").getString(0));
            check(workReport.getString("title").contains("Работно време"),"Work-time PDF title missing");
            check(travelReport.getString("title").contains("службен пат"),"Travel PDF title missing");
            check(travelReport.getJSONArray("tripRows").length()==2,"Travel report trip rows missing");

            byte[] workPdf=MainActivity.PdfReport.create(workReport);
            byte[] travelPdf=MainActivity.PdfReport.create(travelReport);
            check(workPdf.length>2000&&travelPdf.length>2000,"PDF generation failed");
            check(new String(workPdf,0,4,"US-ASCII").equals("%PDF"),"Invalid work PDF");
            check(new String(travelPdf,0,4,"US-ASCII").equals("%PDF"),"Invalid travel PDF");
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"smoke-report.pdf"))){out.write(workPdf);}
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"smoke-travel-report.pdf"))){out.write(travelPdf);}

            js(a,"WFApp.setView('home');window.scrollTo(0,0)");
            Thread.sleep(500);
            android.graphics.Bitmap screenshot=getUiAutomation().takeScreenshot();
            check(screenshot!=null,"App screenshot failed");
            int colored=0;
            for(int y=35;y<screenshot.getHeight()-35;y+=2)for(int x=0;x<screenshot.getWidth();x+=2){
                int c=screenshot.getPixel(x,y);
                if(android.graphics.Color.red(c)<170||android.graphics.Color.green(c)<170||android.graphics.Color.blue(c)<170)colored++;
            }
            check(colored>200,"App surface remained blank");
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"wf-smoke.png"))){
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
            }
            screenshot.recycle();

            result.putString("stream","WF_SMOKE_OK: WFAG v1.2, 08:00/custom work start, travel extends workday, overtime, free-day deduction, holiday, two PDF reports\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable t){
            result.putString("stream","WF_SMOKE_FAILED: "+t.toString()+"\n");
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}