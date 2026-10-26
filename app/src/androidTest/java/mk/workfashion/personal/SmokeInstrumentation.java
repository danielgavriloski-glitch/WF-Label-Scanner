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
                if("true".equals(js(a,"typeof WFApp!=='undefined'&&!!WFApp.getState()&&typeof WFApp.travelReportJson==='function'&&typeof WFVehicle==='object'"))){loaded=true;break;}
            }
            check(loaded,"WFAG v1.2 UI did not load");
            check("true".equals(js(a,"document.querySelector('.logo').textContent==='WFAG'")),"WFAG brand missing");
            check("true".equals(js(a,"WFApp.getState().settings.start==='08:00'&&WFApp.getState().settings.end==='16:00'")),"Incorrect default schedule");
            check("false".equals(js(a,"Android.saveState('malformed')")),"Invalid JSON accepted");

            check("true".equals(js(a,
                "(()=>{let s=Core.fresh(new Date('2026-10-01T08:00:00Z'));s.settings.trackStart='2026-10-01';s.settings.name='Тест Вработен';s.budgets['2026']={annual:20,carry:2,previous:1};if(!Android.saveState(JSON.stringify(s))||!load())return false;render();return state.trips.length===0&&state.settings.name==='Тест Вработен';})()"
            )),"Known state setup failed");

            for(String v:new String[]{"routes","history","leave","reports","settings","home"}){
                check("true".equals(js(a,"WFApp.setView('"+v+"');document.getElementById('app').textContent.length>100")),"Missing screen: "+v);
            }

            check("true".equals(js(a,
                "(()=>{WFApp.setView('home');const p=document.getElementById('gpsDayPicker');if(!p||!document.getElementById('gpsDayCard'))return false;const date=p.value;document.querySelector('[data-gps-offset=\\\"-1\\\"]').click();const before=document.getElementById('gpsDayPicker').value;if(before===date)return false;WFApp.setView('routes');return document.getElementById('gpsAllHistory')!==null&&document.getElementById('gpsArchiveImport')!==null&&document.querySelector('[data-view=\\\"routes\\\"]')!==null;})()"
            )),"Daily GPS browsing, history tab or import button not visible");

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

            check("true".equals(js(a,
                "(()=>{let s=WFApp.getState();s.records['2026-10-12']={type:'holidayWork',start:'08:00',end:'16:00',pause:0,pausePaid:true,overnight:false,note:'Работа на празник'};commit(s,'Holiday upgrade regression');const saved=Android.loadState();if(!load())return false;const p=Core.period(state,'2026-10-12','2026-10-12',new Date('2026-10-13T12:00:00Z'));const b=WFApp.overtimeReportJson(p),rows=WFApp.exportRows(p);editDay('2026-10-12');const hint=!document.getElementById('holidayWorkHint').hidden;closeModal();render();return p.worked===480&&p.regular===0&&p.overtime===480&&p.holidayWork===480&&b.rows[0][4].includes('Работа на празник')&&rows[1][6]===8&&hint&&Android.loadState()===saved&&JSON.stringify(state)===saved&&state.trips.length===2&&state.budgets['2026'].annual===20&&state.settings.name==='Тест Вработен';})()"
            )),"Holiday overtime, report labels or existing saved data failed");

            String holidayEncoded=js(a,
                "JSON.stringify(WFApp.reportJson(Core.period(state,'2026-10-12','2026-10-12',new Date('2026-10-13T12:00:00Z'))))"
            );
            String overtimeEncoded=js(a,
                "JSON.stringify(WFApp.overtimeReportJson(Core.period(state,'2026-10-12','2026-10-12',new Date('2026-10-13T12:00:00Z'))))"
            );
            JSONObject holidayReport=new JSONObject(new JSONArray("["+holidayEncoded+"]").getString(0));
            JSONObject overtimeReport=new JSONObject(new JSONArray("["+overtimeEncoded+"]").getString(0));
            byte[] holidayPdf=MainActivity.PdfReport.create(holidayReport);
            byte[] overtimePdf=MainActivity.PdfReport.create(overtimeReport);
            check(holidayPdf.length>2000&&overtimePdf.length>2000,"Holiday PDF generation failed");
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"smoke-holiday-report.pdf"))){out.write(holidayPdf);}
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"smoke-overtime-report.pdf"))){out.write(overtimePdf);}

            JSONObject longReport=new JSONObject(overtimeReport.toString());
            StringBuilder longReason=new StringBuilder("Работа на празник\n");
            for(int i=0;i<160;i++)longReason.append("Белешка\n");
            longReport.getJSONArray("rows").getJSONArray(0).put(4,longReason.toString());
            byte[] longPdf=MainActivity.PdfReport.create(longReport);
            check(longPdf.length>overtimePdf.length,"Long overtime reason did not paginate");
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"smoke-overtime-long-report.pdf"))){out.write(longPdf);}

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


            // Vehicle module: real WebView forms, native persistence and legacy backup.
            check("true".equals(js(a,"(()=>{WFApp.setView('vehicle');WFVehicle.setup();document.getElementById('vanAt').value='2026-10-01T08:00';document.getElementById('vanOdo').value='10000';document.getElementById('vanL100').value='10';document.getElementById('vanForm').dispatchEvent(new Event('submit',{cancelable:true}));return state.vehicleLog&&Vehicle.latest(state.vehicleLog).odo===10000&&state.settings.name==='Тест Вработен';})()")), "Crafter setup form and persistence");
            check("true".equals(js(a,"(()=>{for(const r of [{at:'2026-10-01T09:00',odo:10000,L:60,full:true},{at:'2026-10-02T09:00',odo:10300,L:30,full:false},{at:'2026-10-03T09:00',odo:10500,L:20,full:true}]){WFVehicle.edit('fills');document.getElementById('vanAt').value=r.at;document.getElementById('vanOdo').value=String(r.odo);document.getElementById('vanLitres').value=String(r.L);document.getElementById('vanFull').checked=r.full;document.getElementById('vanForm').dispatchEvent(new Event('submit',{cancelable:true}));}return state.vehicleLog.fills.length===3&&Vehicle.summary(state.vehicleLog).measuredL100===10;})()")), "Full and partial fuel forms");
            check("true".equals(js(a,"(()=>{WFVehicle.edit('fills',state.vehicleLog.fills[1].id);document.getElementById('vanLitres').value='20';document.getElementById('vanForm').dispatchEvent(new Event('submit',{cancelable:true}));return Vehicle.summary(state.vehicleLog).measuredL100===8&&state.vehicleLog.audit.some(a=>a.action==='edit'&&a.before.litres===30);})()")), "Correction of earlier fill recomputes average");
            check("true".equals(js(a,"(()=>{for(const r of [{at:'2026-09-01T08:00',odo:9000,set:'summer'},{at:'2026-10-01T09:00',odo:10000,set:'winter'},{at:'2026-10-04T08:00',odo:10600,set:'summer'}]){WFVehicle.edit('tireChanges');document.getElementById('vanAt').value=r.at;document.getElementById('vanOdo').value=String(r.odo);document.getElementById('vanSet').value=r.set;document.getElementById('vanForm').dispatchEvent(new Event('submit',{cancelable:true}));}WFVehicle.edit('readings');document.getElementById('vanAt').value='2026-10-05T08:00';document.getElementById('vanOdo').value='10800';document.getElementById('vanForm').dispatchEvent(new Event('submit',{cancelable:true}));const tires=Vehicle.tires(state.vehicleLog);return tires.find(t=>t.id==='summer').totalKm===1200&&tires.find(t=>t.id==='winter').totalKm===600;})()")), "Tire history across seasons through real forms");
            check("true".equals(js(a,"(()=>{const before=JSON.stringify(state.trips);WFVehicle.edit('trips');document.getElementById('vanAt').value='2026-10-03T10:00';document.getElementById('vanEndAt').value='2026-10-03T12:00';document.getElementById('vanStartOdo').value='10500';document.getElementById('vanEndOdo').value='10600';document.getElementById('vanFrom').value='Охрид';document.getElementById('vanTo').value='Скопје';document.getElementById('vanForm').dispatchEvent(new Event('submit',{cancelable:true}));const source=WFVehicle.linkCandidates()[0];if(!source)return false;WFVehicle.edit('trips',null,source);document.getElementById('vanKm').value='250';document.getElementById('vanForm').dispatchEvent(new Event('submit',{cancelable:true}));return Vehicle.summary(state.vehicleLog).tripKm===350&&state.vehicleLog.trips.length===2&&!WFVehicle.linkCandidates().some(t=>t.sourceId===source.sourceId)&&JSON.stringify(state.trips)===before;})()")), "Manual and linked trips without changing attendance");
            check("true".equals(js(a,"(()=>{for(const tab of ['overview','fuel','tires','trips','analysis','history']){WFVehicle.setTab(tab);if(document.getElementById('app').textContent.length<150)return false;}WFVehicle.setTab('analysis');document.getElementById('vanFromDate').value='2026-10-01';document.getElementById('vanToDate').value='2026-10-31';document.getElementById('vanApplyRange').click();return WFVehicle.vehicleReport().rows.length===5&&document.documentElement.scrollWidth<=innerWidth+1;})()")), "All vehicle screens and analysis period");
            check("true".equals(js(a,"(()=>{const old=WFApp.getState(),before=JSON.stringify(old.vehicleLog);delete old.vehicleLog;const answer=window.confirm;window.confirm=()=>true;try{handleImport(JSON.stringify(old));}finally{window.confirm=answer;}if(JSON.stringify(state.vehicleLog)!==before)return false;const saved=Android.loadState();if(!load())return false;WFVehicle.setTab('analysis');return JSON.stringify(state)===saved&&state.trips.length===2&&Vehicle.summary(state.vehicleLog).measuredL100===8;})()")), "Old backup retains van records and saved reload");

            // Warehouse forms and real, private attachment bytes, with a complete backup round trip.
            check("true".equals(js(a,"(()=>{WFApp.setView('documents');WFDocuments.openVisit(null,{date:'2024-01-01'});document.getElementById('warehouseNewName').value='Магацин Охрид';document.getElementById('warehouseAction').value='pickup';document.getElementById('warehouseNote').value='Зедов испратница';document.getElementById('warehouseForm').dispatchEvent(new Event('submit',{cancelable:true}));return state.warehouseLog.visits.length===1&&state.warehouseLog.visits[0].date==='2024-01-01'&&state.warehouseLog.warehouses.length===1;})()")),"Manual warehouse entry before attendance start");
            android.graphics.Bitmap page=android.graphics.Bitmap.createBitmap(1400,2000,android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas pageCanvas=new android.graphics.Canvas(page);pageCanvas.drawColor(android.graphics.Color.WHITE);android.graphics.Paint ink=new android.graphics.Paint(3);ink.setColor(android.graphics.Color.BLACK);ink.setTextSize(52);pageCanvas.drawText("WFAG TEST DOCUMENT 12345",80,180,ink);
            File document=Documents.file(getTargetContext(),Documents.unique("jpg"));Documents.writeJpeg(page,document);page.recycle();
            JSONObject attachment=Documents.metadata(document,"Испратница 12345.jpg");
            String visitId=new JSONObject(a.new LocalBridge().loadState()).getJSONObject("warehouseLog").getJSONArray("visits").getJSONObject(0).getString("id");
            a.appendDocuments(visitId,new JSONArray().put(attachment));
            check("true".equals(js(a,"(()=>{if(!load())return false;WFDocuments.openVisit(state.warehouseLog.visits[0].id);return document.querySelectorAll('[data-open-document]').length===1&&document.querySelector('.warehouse-doc img')!==null;})()")),"Native attachment persists and opens in the WebView");
            String beforeBackup=a.new LocalBridge().loadState();java.io.ByteArrayOutputStream archiveBytes=new java.io.ByteArrayOutputStream();Documents.exportArchive(getTargetContext(),archiveBytes,beforeBackup,RouteTrackingService.stateJson(getTargetContext()));
            File stage=new File(getTargetContext().getCacheDir(),"test-documents-restore");String restored=Documents.stageArchive(getTargetContext(),new java.io.ByteArrayInputStream(archiveBytes.toByteArray()),stage);check(restored.equals(beforeBackup),"Archive did not preserve exact working state");
            File stagedFile=new File(stage,"documents/"+document.getName());check(stagedFile.isFile()&&Documents.hash(stagedFile).equals(attachment.getString("sha256")),"Archive lost attachment bytes");
            check(document.delete(),"Test attachment removal failed");check(Documents.available(getTargetContext(),new JSONObject(restored).getJSONObject("warehouseLog"),stage),"Staged backup was not readable");Documents.installArchive(getTargetContext(),stage);check(document.isFile()&&Documents.hash(document).equals(attachment.getString("sha256")),"Attachment failed to restore");Documents.removeTree(stage);
            check("true".equals(js(a,"(()=>{closeModal();WFDocuments.setFilters({query:'испратница',from:'2024-01-01',to:'2024-12-31'});return document.getElementById('app').textContent.includes('Магацин Охрид')&&state.trips.length===2&&state.vehicleLog.fills.length===3;})()")),"Warehouse search or existing data preservation");
            Thread.sleep(500);android.graphics.Bitmap docsScreenshot=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"wf-documents.png"))){docsScreenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}docsScreenshot.recycle();
            js(a,"WFVehicle.setTab('analysis')");

            String vehicleEncoded=js(a,"JSON.stringify(WFVehicle.vehicleReport())");
            JSONObject vehicleReport=new JSONObject(new JSONArray("["+vehicleEncoded+"]").getString(0));
            byte[] vehiclePdf=MainActivity.PdfReport.create(vehicleReport);
            check(vehiclePdf.length>2000,"Vehicle PDF generation failed");
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"smoke-vehicle-report.pdf"))){out.write(vehiclePdf);}
            js(a,"WFVehicle.setTab('overview');window.scrollTo(0,0)");
            Thread.sleep(400);
            android.graphics.Bitmap vehicleScreenshot=getUiAutomation().takeScreenshot();
            check(vehicleScreenshot!=null,"Vehicle screenshot failed");
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"wf-vehicle.png"))){vehicleScreenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
            vehicleScreenshot.recycle();

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

            result.putString("stream","WF_SMOKE_OK: WFAG v3.1 warehouse documents, native attachment and complete ZIP restore; Crafter vehicle forms, fuel calculations, seasonal tires, backup preservation, vehicle PDF, holiday overtime, saved-data reload, holiday/overtime PDFs, 08:00/custom work start, travel extends workday, free-day deduction\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable t){
            java.io.StringWriter trace=new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream","WF_SMOKE_FAILED: "+trace.toString()+"\n");
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
