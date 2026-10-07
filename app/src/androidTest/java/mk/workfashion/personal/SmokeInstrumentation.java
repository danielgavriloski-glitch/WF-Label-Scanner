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
            check("true".equals(js(a,"(()=>{let s=WFApp.getState();s.settings.trackStart='2026-10-01';s.settings.name='Тест Вработен';s.budgets['2026']={annual:20,carry:2,previous:1};s.records['2026-10-05']={type:'vacation',pause:0,note:'Тест на годишен одмор'};delete s.trips;if(!Android.saveState(JSON.stringify(s))||!load())return false;render();return state.trips.length===0&&state.budgets['2026'].annual===20&&state.records['2026-10-05'].type==='vacation';})()")),"Persistence failed");
            for(String v:new String[]{"history","leave","reports","settings","home"})check("true".equals(js(a,"WFApp.setView('"+v+"');document.getElementById('app').textContent.length>100")),"Missing screen: "+v);
            check("true".equals(js(a,"JSON.parse(Android.loadState()).settings.name==='Тест Вработен'")),"Native state not retained");
            check("true".equals(js(a,"(()=>{editDay('2026-10-06','sick',true);document.getElementById('editTo').value='2026-10-07';document.getElementById('dayForm').dispatchEvent(new Event('submit',{cancelable:true}));return WFApp.getState().records['2026-10-07'].type==='sick';})()")),"Absence range form failed");
            check("true".equals(js(a,"(()=>{document.getElementById('goOnTrip').click();document.getElementById('tripDate').value='2026-10-01';document.getElementById('tripDeparture').value='06:00';document.getElementById('tripNote').value='Охрид – Скопје';document.getElementById('tripForm').dispatchEvent(new Event('submit',{cancelable:true}));return state.trips.length===1&&state.trips[0].arrival===''&&document.querySelector('[data-trip-arrive]')!==null;})()")),"Departure form failed");
            check("true".equals(js(a,"(()=>{if(!load())return false;render();return state.trips.length===1&&JSON.parse(Android.loadState()).trips[0].departure==='06:00'&&state.budgets['2026'].carry===2&&state.records['2026-10-05'].note==='Тест на годишен одмор';})()")),"Pending departure was not retained");
            check("true".equals(js(a,"(()=>{document.querySelector('[data-trip-arrive]').click();document.getElementById('tripNowArrival').click();if(document.getElementById('tripArrival').value!==Core.parts().hour+':'+Core.parts().minute)return false;document.getElementById('tripArrivalDate').value='2026-10-01';document.getElementById('tripArrival').value='06:45';document.getElementById('tripForm').dispatchEvent(new Event('submit',{cancelable:true}));return state.trips.length===1&&state.trips[0].arrival==='06:45';})()")),"Arrival form failed");
            check("true".equals(js(a,"(()=>{WFApp.editTrip(null,'2026-10-03');document.getElementById('tripDeparture').value='22:30';document.getElementById('tripArrivalDate').value='2026-10-04';document.getElementById('tripArrival').value='01:15';document.getElementById('tripNote').value='Подолго патување преку полноќ: Охрид – Скопје. '.repeat(8);document.getElementById('tripForm').dispatchEvent(new Event('submit',{cancelable:true}));return state.trips.length===2&&state.trips[1].arrivalDate==='2026-10-04';})()")),"Overnight travel form failed");
            check("true".equals(js(a,"(()=>{WFApp.editTrip(null,'2026-10-01');document.getElementById('tripDeparture').value='06:20';document.getElementById('tripArrivalDate').value='2026-10-01';document.getElementById('tripArrival').value='06:40';document.getElementById('tripForm').dispatchEvent(new Event('submit',{cancelable:true}));const ok=state.trips.length===2&&document.getElementById('tripError').textContent.includes('преклопува');closeModal();return ok;})()")),"Overlapping travel accepted");
            check("true".equals(js(a,"(()=>{const s=WFApp.getState(),p=Core.period(s,'2026-10-01','2026-10-31',new Date('2026-11-01T12:00:00Z')),before=Core.period({...s,trips:[]},p.from,p.to,new Date('2026-11-01T12:00:00Z')),rows=WFApp.exportRows(p),pdf=WFApp.reportJson(p);return p.travel===210&&rows[0].length===17&&rows[rows.length-1][15]===3.5&&pdf.tripRows.length===2&&p.worked===before.worked&&p.credit===before.credit&&p.overtime===before.overtime&&s.budgets['2026'].annual===20;})()")),"Travel export values or existing work totals changed");
            String encoded=js(a,"JSON.stringify(reportJson(Core.period(WFApp.getState(),'2026-10-01','2026-10-31',new Date('2026-11-01T12:00:00Z'))))");
            String workbook=js(a,"bytes64(WFXlsx.create(exportRows(Core.period(state,'2026-10-01','2026-10-31',new Date('2026-11-01T12:00:00Z')))))");
            byte[] xlsx=android.util.Base64.decode(new JSONArray("["+workbook+"]").getString(0),android.util.Base64.DEFAULT);
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"smoke-report.xlsx"))){out.write(xlsx);}
            JSONObject report=new JSONObject(new JSONArray("["+encoded+"]").getString(0));
            byte[] pdf=MainActivity.PdfReport.create(report);check(pdf.length>2000,"PDF is empty");
            check(new String(pdf,0,4,"US-ASCII").equals("%PDF"),"Invalid PDF");
            File file=new File(getTargetContext().getExternalFilesDir(null),"smoke-report.pdf");try(FileOutputStream out=new FileOutputStream(file)){out.write(pdf);}
            JSONObject stress=new JSONObject(report.toString());JSONArray stressRows=new JSONArray();
            for(int i=0;i<16;i++)stressRows.put(new JSONArray().put("Пат "+(i+1)).put("06:00").put("07:00").put("1:00").put("Целосна долга белешка за проверка на преломот: "+new String(new char[440]).replace('\0','Ж')+" КРАЈ-"+(i+1)));
            stress.put("tripRows",stressRows);byte[] stressPdf=MainActivity.PdfReport.create(stress);check(stressPdf.length>2000,"Multi-page trip PDF failed");
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"smoke-trip-pages.pdf"))){out.write(stressPdf);}
            js(a,"(()=>{const n=new Date(),d=Core.parts(new Date(n.getTime()-30*60000));const result=Core.putTrip(state,{date:d.date,departure:d.hour+':'+d.minute,arrival:'',arrivalDate:d.date,note:'Охрид – Скопје'});commit(result.state,'Пример за патување');WFApp.setView('home');window.scrollTo(0,0);})()");
            result.putString("stream","WF_SMOKE_OK: native startup, five screens, version 1 migration, departure/arrival forms, overnight travel, persistence, overlap rejection, unchanged work totals, CSV/Excel data, Cyrillic multi-page PDF\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable t){result.putString("stream","WF_SMOKE_FAILED: "+t.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
