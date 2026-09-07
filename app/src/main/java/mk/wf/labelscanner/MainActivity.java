package mk.wf.labelscanner;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int CAMERA=10, SAVE=11, PERM=12;
    private EditText orderNo, size, quantity, boxes, rawText;
    private TextView status, total;
    private ImageView preview;
    private final ArrayList<Row> rows=new ArrayList<>();
    private String pendingXls="";

    static class Row {
        String order,size,qty,boxes,text;
        Row(String o,String s,String q,String b,String t){order=o;size=s;qty=q;boxes=b;text=t;}
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28,28,28,40);
        root.setBackgroundColor(Color.rgb(245,247,250)); scroll.addView(root);

        TextView title=new TextView(this); title.setText("WF LABEL SCANNER"); title.setTextSize(25);
        title.setTextColor(Color.rgb(20,55,85)); title.setPadding(0,0,0,20); root.addView(title);

        orderNo=field("Број на налог"); root.addView(orderNo);
        status=label("Нема активен налог"); root.addView(status);
        Button start=button("ЗАПОЧНИ НАЛОГ"); root.addView(start);
        start.setOnClickListener(v->{
            if(orderNo.getText().toString().trim().isEmpty()){toast("Внеси број на налог");return;}
            status.setText("Активен налог: "+orderNo.getText()); rows.clear(); updateTotal();
        });

        preview=new ImageView(this); preview.setAdjustViewBounds(true); preview.setMinimumHeight(220); root.addView(preview);
        Button camera=button("СЛИКАЈ ЕТИКЕТА"); root.addView(camera);
        camera.setOnClickListener(v->openCamera());

        rawText=field("Прочитан текст од етикета"); rawText.setMinLines(4); root.addView(rawText);
        size=field("Големина"); root.addView(size);
        quantity=field("Количина / пара"); quantity.setInputType(2); root.addView(quantity);
        boxes=field("Број на кутии"); boxes.setInputType(2); root.addView(boxes);

        Button add=button("ПОТВРДИ ПАКЕТ"); root.addView(add);
        add.setOnClickListener(v->addRow());
        total=label("Скенирани пакети: 0"); total.setTextSize(18); root.addView(total);

        Button review=button("ПРЕГЛЕД"); root.addView(review);
        review.setOnClickListener(v->showReview());
        Button export=button("КРЕИРАЈ EXCEL"); root.addView(export);
        export.setOnClickListener(v->exportXls());
        setContentView(scroll);
    }

    private EditText field(String hint){
        EditText e=new EditText(this); e.setHint(hint); e.setTextSize(17); e.setPadding(16,18,16,18);
        e.setBackgroundColor(Color.WHITE); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,0,0,14); e.setLayoutParams(p); return e;
    }
    private TextView label(String s){ TextView t=new TextView(this); t.setText(s); t.setTextSize(16); t.setPadding(4,12,4,12); return t; }
    private Button button(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    private void openCamera(){
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},PERM); return;
        }
        startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE),CAMERA);
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(r==PERM && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) openCamera();
    }
    @Override protected void onActivityResult(int r,int c,Intent data){
        super.onActivityResult(r,c,data);
        if(r==CAMERA && c==RESULT_OK && data!=null){
            Bitmap bm=(Bitmap)data.getExtras().get("data"); preview.setImageBitmap(bm);
            rawText.setText("Се чита...");
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(InputImage.fromBitmap(bm,0))
                .addOnSuccessListener(x->{rawText.setText(x.getText()); guessFields(x.getText());})
                .addOnFailureListener(x->rawText.setText("Не успеа читањето. Внеси рачно."));
        } else if(r==SAVE && c==RESULT_OK && data!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                out.write(pendingXls.getBytes(StandardCharsets.UTF_8)); toast("Excel документот е зачуван");
            }catch(Exception e){toast("Грешка при зачувување");}
        }
    }
    private void guessFields(String text){
        String upper=text.toUpperCase(Locale.ROOT);
        String[] sizes={"XS","S","M","L","XL","2XL","3XL","4XL","5XL","42","44","46","48","50","52","54","56","58","60"};
        for(String s:sizes) if(upper.matches("(?s).*\\b"+s+"\\b.*")){size.setText(s);break;}
    }
    private void addRow(){
        String o=orderNo.getText().toString().trim();
        if(o.isEmpty()){toast("Прво започни налог");return;}
        if(size.getText().toString().trim().isEmpty()||quantity.getText().toString().trim().isEmpty()){
            toast("Внеси големина и количина");return;
        }
        rows.add(new Row(o,size.getText().toString().trim(),quantity.getText().toString().trim(),
                boxes.getText().toString().trim(),rawText.getText().toString().trim()));
        size.setText(""); quantity.setText(""); boxes.setText(""); rawText.setText(""); preview.setImageDrawable(null);
        updateTotal(); toast("Пакетот е додаден");
    }
    private void updateTotal(){total.setText("Скенирани пакети: "+rows.size());}
    private void showReview(){
        if(rows.isEmpty()){toast("Нема внесени пакети");return;}
        StringBuilder s=new StringBuilder();
        for(int i=0;i<rows.size();i++){Row x=rows.get(i);s.append(i+1).append(". Големина ").append(x.size)
            .append(" — ").append(x.qty).append(" пара"); if(!x.boxes.isEmpty())s.append(" — ").append(x.boxes).append(" кутии");s.append("\n");}
        new android.app.AlertDialog.Builder(this).setTitle("Налог "+orderNo.getText()).setMessage(s.toString())
            .setPositiveButton("Во ред",null).show();
    }
    private void exportXls(){
        if(rows.isEmpty()){toast("Нема внесени пакети");return;}
        StringBuilder x=new StringBuilder("<?xml version=\"1.0\"?><?mso-application progid=\"Excel.Sheet\"?>");
        x.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"><Worksheet ss:Name=\"Paketi\"><Table>");
        x.append(rowXml("Налог","Големина","Количина","Кутии","Текст од етикета"));
        for(Row r:rows)x.append(rowXml(r.order,r.size,r.qty,r.boxes,r.text));
        x.append("</Table></Worksheet></Workbook>"); pendingXls=x.toString();
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.setType("application/vnd.ms-excel");
        i.putExtra(Intent.EXTRA_TITLE,"WF_Nalog_"+orderNo.getText()+".xls"); startActivityForResult(i,SAVE);
    }
    private String rowXml(String... cells){
        StringBuilder s=new StringBuilder("<Row>");
        for(String c:cells)s.append("<Cell><Data ss:Type=\"String\">").append(esc(c)).append("</Data></Cell>");
        return s.append("</Row>").toString();
    }
    private String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
