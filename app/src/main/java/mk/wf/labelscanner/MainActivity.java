package mk.wf.labelscanner;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    private static final int PERM=20,SAVE=21;
    private PreviewView camera;
    private TextView state,count,list;
    private EditText activeOrder;
    private final ArrayList<Row> rows=new ArrayList<>();
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private boolean processing=false;
    private String lastCandidate="",lastAccepted="";
    private int stable=0;
    private long lastTime=0;
    private String pendingXls="";

    static class Row {
        String nalog,paket,artikal,boja,golemina,kolicina,kutii,raw;
        Row(String n,String p,String a,String b,String g,String k,String ku,String r){
            nalog=n;paket=p;artikal=a;boja=b;golemina=g;kolicina=k;kutii=ku;raw=r;
        }
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20,20,20,24); root.setBackgroundColor(Color.rgb(244,247,250));

        TextView title=tv("WF AUTO SCAN",25); title.setTextColor(Color.rgb(18,67,96)); root.addView(title);
        state=tv("Насочи ја камерата кон една етикета",17); root.addView(state);

        activeOrder=new EditText(this); activeOrder.setHint("Налог (само ако го нема на етикетата)");
        activeOrder.setSingleLine(); activeOrder.setBackgroundColor(Color.WHITE); root.addView(activeOrder);

        camera=new PreviewView(this); camera.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,0,1.05f); cp.setMargins(0,14,0,10);
        camera.setLayoutParams(cp); root.addView(camera);

        count=tv("Скенирани пакети: 0",18); root.addView(count);
        list=tv("Сè уште нема скенирано.",15);
        ScrollView sv=new ScrollView(this); sv.addView(list);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1,0,.75f)); root.addView(sv);

        LinearLayout buttons=new LinearLayout(this);
        Button undo=btn("Врати последен"); Button excel=btn("Креирај Excel");
        buttons.addView(undo,new LinearLayout.LayoutParams(0,-2,1)); buttons.addView(excel,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(buttons);
        undo.setOnClickListener(v->{if(!rows.isEmpty()){rows.remove(rows.size()-1);lastAccepted="";refresh();}});
        excel.setOnClickListener(v->exportXls());
        setContentView(root);

        if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA},PERM);
    }

    private void startCamera(){
        ListenableFuture<ProcessCameraProvider> f=ProcessCameraProvider.getInstance(this);
        f.addListener(()->{
            try{
                ProcessCameraProvider provider=f.get();
                Preview preview=new Preview.Builder().build(); preview.setSurfaceProvider(camera.getSurfaceProvider());
                ImageAnalysis analysis=new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analysis.setAnalyzer(executor,this::analyze);
                provider.unbindAll();
                provider.bindToLifecycle(androidx.lifecycle.ProcessLifecycleOwner.get(),
                    CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis);
            }catch(Exception e){runOnUiThread(()->state.setText("Камерата не може да стартува")); }
        },ContextCompat.getMainExecutor(this));
    }

    @androidx.camera.core.ExperimentalGetImage
    private void analyze(ImageProxy proxy){
        if(processing||proxy.getImage()==null){proxy.close();return;}
        processing=true;
        InputImage img=InputImage.fromMediaImage(proxy.getImage(),proxy.getImageInfo().getRotationDegrees());
        recognizer.process(img).addOnSuccessListener(this::consider)
            .addOnCompleteListener(x->{processing=false;proxy.close();});
    }

    private void consider(com.google.mlkit.vision.text.Text result){
        String raw=result.getText().trim();
        if(raw.length()<12){runOnUiThread(()->state.setText("Барам јасна етикета..."));return;}
        String normalized=raw.replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);
        if(normalized.equals(lastCandidate)) stable++; else {lastCandidate=normalized;stable=1;}
        if(stable<2){runOnUiThread(()->state.setText("Препознавам... држи мирно"));return;}
        if(normalized.equals(lastAccepted)||System.currentTimeMillis()-lastTime<1800)return;

        Row row=parse(raw);
        if(row.golemina.isEmpty()&&row.kolicina.isEmpty()){
            runOnUiThread(()->state.setText("Етикета најдена, но нема големина/количина"));return;
        }
        lastAccepted=normalized; lastTime=System.currentTimeMillis(); stable=0;
        runOnUiThread(()->{
            rows.add(row);
            new ToneGenerator(AudioManager.STREAM_NOTIFICATION,90).startTone(ToneGenerator.TONE_PROP_BEEP,180);
            state.setText("✓ Додаден пакет "+row.paket+" — тргни ја етикетата");
            refresh();
        });
    }

    private Row parse(String raw){
        String flat=raw.replace('\n',' ');
        String nalog=find(flat,"(?:НАЛОГ|NALOG|ORDER|AUFTRAG|ORD)\\s*[:#.-]?\\s*([A-Z0-9/-]{2,})");
        if(nalog.isEmpty())nalog=activeOrder.getText().toString().trim();
        String paket=find(flat,"(?:ПАКЕТ|PAKET|PACKAGE|PACK)\\s*[:#.-]?\\s*([A-Z0-9/-]+)");
        String artikal=find(flat,"(?:АРТИКАЛ|ARTIKAL|ARTICLE|ARTIKEL|ITEM)\\s*[:#.-]?\\s*([A-Z0-9._/-]+)");
        String boja=find(flat,"(?:БОЈА|BOJA|COLOR|COLOUR|FARBE)\\s*[:#.-]?\\s*([A-ZÄÖÜa-zäöü]+)");
        String golemina=find(flat,"(?:ГОЛЕМИНА|GOLEMINA|SIZE|GRÖSSE|GROESSE)\\s*[:#.-]?\\s*(XS|S|M|L|XL|[2-9]XL|[2-6][0-9])");
        if(golemina.isEmpty())golemina=find(flat,"\\b(XS|XL|[2-9]XL|[2-6][0-9])\\b");
        String kolicina=find(flat,"(?:КОЛИЧИНА|KOLICINA|QTY|QUANTITY|MENGE|PAIR|PAIRS|PARA)\\s*[:#.-]?\\s*(\\d+)");
        String kutii=find(flat,"(?:КУТИИ|KUTII|BOX|BOXES|KARTON|KARTONS)\\s*[:#.-]?\\s*(\\d+)");
        return new Row(nalog,paket,artikal,boja,golemina,kolicina,kutii,raw);
    }

    private String find(String s,String regex){
        Matcher m=Pattern.compile(regex,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(s);
        return m.find()?m.group(1).trim():"";
    }

    private void refresh(){
        count.setText("Скенирани пакети: "+rows.size());
        StringBuilder s=new StringBuilder();
        for(int i=rows.size()-1;i>=0;i--){
            Row r=rows.get(i);
            s.append("✓ #").append(i+1);
            if(!r.nalog.isEmpty())s.append("  Налог: ").append(r.nalog);
            if(!r.paket.isEmpty())s.append("\nПакет: ").append(r.paket);
            if(!r.artikal.isEmpty())s.append("   Артикал: ").append(r.artikal);
            if(!r.boja.isEmpty())s.append("\nБоја: ").append(r.boja);
            if(!r.golemina.isEmpty())s.append("   Големина: ").append(r.golemina);
            if(!r.kolicina.isEmpty())s.append("   Количина: ").append(r.kolicina);
            if(!r.kutii.isEmpty())s.append("   Кутии: ").append(r.kutii);
            s.append("\n────────────────\n");
        }
        list.setText(s.length()==0?"Сè уште нема скенирано.":s.toString());
    }

    private void exportXls(){
        if(rows.isEmpty()){Toast.makeText(this,"Нема скенирани пакети",Toast.LENGTH_SHORT).show();return;}
        StringBuilder x=new StringBuilder("<?xml version=\"1.0\"?><?mso-application progid=\"Excel.Sheet\"?>");
        x.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"><Worksheet ss:Name=\"Paketi\"><Table>");
        x.append(xrow("Налог","Пакет","Артикал","Боја","Големина","Количина","Кутии"));
        for(Row r:rows)x.append(xrow(r.nalog,r.paket,r.artikal,r.boja,r.golemina,r.kolicina,r.kutii));
        pendingXls=x.append("</Table></Worksheet></Workbook>").toString();
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/vnd.ms-excel");
        i.putExtra(Intent.EXTRA_TITLE,"WF_Skenirani_Paketi.xls");startActivityForResult(i,SAVE);
    }

    private String xrow(String... cs){StringBuilder s=new StringBuilder("<Row>");for(String c:cs)s.append("<Cell><Data ss:Type=\"String\">").append(esc(c)).append("</Data></Cell>");return s.append("</Row>").toString();}
    private String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==SAVE&&c==RESULT_OK&&d!=null)try(OutputStream o=getContentResolver().openOutputStream(d.getData())){o.write(pendingXls.getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"Excel е зачуван",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Грешка при зачувување",Toast.LENGTH_LONG).show();}}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==PERM&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startCamera();}
    private TextView tv(String s,int z){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setPadding(4,8,4,8);return t;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
}
