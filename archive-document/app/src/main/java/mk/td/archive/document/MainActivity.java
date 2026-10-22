package mk.td.archive.document;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
    static final int BG=0xff151a21,CARD=0xff202832,INPUT=0xff29333f,TEXT=0xffedf2f7,MUTED=0xffaebdcb,ACCENT=0xff59d1bd;
    static final int CAMERA=10,PICK=11,SAVE_FILE=12,RESTORE=13;
    Store store; Store.Record current;
    LinearLayout root,body,list;
    EditText title,reference,category,notes,date,search;
    Spinner filter;
    String page="home";
    final Handler handler=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final Runnable saveText=()->saveFields(false);
    int generation=0;
    boolean busy=false;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new Store(this);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        String id=state==null?prefs().getString("activeRecord",null):state.getString("record");
        String dest=state==null?prefs().getString("page","home"):state.getString("page","home");
        if(id!=null)current=store.get(id);
        if(current!=null&&!dest.equals("home")){if(dest.equals("files"))showFiles();else showEdit();}else showHome();
    }
    SharedPreferences prefs(){return getSharedPreferences("session",MODE_PRIVATE);}
    void remember(){prefs().edit().putString("activeRecord",current==null?null:current.id).putString("page",page).commit();}
    @Override public void onSaveInstanceState(Bundle b){saveFields(false);b.putString("record",current==null?null:current.id);b.putString("page",page);super.onSaveInstanceState(b);}
    @Override public void onPause(){saveFields(false);super.onPause();}
    @Override public void onDestroy(){handler.removeCallbacks(saveText);worker.shutdown();super.onDestroy();}
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    GradientDrawable bg(int color,int radius){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(radius));return b;}
    TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(3),0,dp(3));return v;}
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    void gap(LinearLayout parent,int height){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    Button button(String label,boolean primary,Runnable click){
        Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?BG:TEXT);
        b.setBackground(bg(primary?ACCENT:INPUT,12));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(12),dp(8),dp(12),dp(8));
        b.setOnClickListener(v->{if(!busy)click.run();});return b;
    }
    void fullButton(LinearLayout parent,String label,boolean primary,Runnable click){Button b=button(label,primary,click);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);parent.addView(b,p);}
    void pair(LinearLayout parent,String left,Runnable lc,String right,Runnable rc){LinearLayout r=row();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(0,dp(8),dp(4),0);r.addView(button(left,false,lc),p);LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(0,-2,1);q.setMargins(dp(4),dp(8),0,0);r.addView(button(right,false,rc),q);parent.addView(r);}
    void screen(String heading,String subtitle,boolean back){
        generation++;handler.removeCallbacks(saveText);title=reference=category=notes=date=null;
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(18),0,dp(18),0);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(18)+insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),dp(18)+insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout header=row();header.setPadding(0,dp(10),0,dp(12));
        if(back){Button b=button("‹",false,()->goBack());b.setContentDescription("Назад");header.addView(b,new LinearLayout.LayoutParams(dp(48),dp(48)));}
        LinearLayout words=column();words.setPadding(back?dp(12):0,0,0,0);TextView h=text(heading,24,TEXT);h.setTypeface(null,Typeface.BOLD);words.addView(h);if(!subtitle.isEmpty())words.addView(text(subtitle,12,MUTED));header.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        if(!back){Button menu=button("⋮",false,()->showMenu());menu.setContentDescription("Мени и резервна копија");header.addView(menu,new LinearLayout.LayoutParams(dp(46),dp(46)));}
        root.addView(header);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();body.setPadding(0,0,0,dp(22));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);root.requestApplyInsets();remember();
    }
    void showHome(){
        saveFields(false);current=null;page="home";screen("Архива","Документи и фотографии · v1.1",false);
        fullButton(body,"+ Нов запис",true,()->{try{current=store.create();showEdit();}catch(Exception e){error(e);}});
        gap(body,14);search=new EditText(this);search.setSingleLine(true);search.setHint("Пребарај наслов, број, опис…");search.setTextColor(TEXT);search.setHintTextColor(MUTED);search.setTextSize(15);search.setPadding(dp(14),dp(12),dp(14),dp(12));search.setBackground(bg(INPUT,12));body.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));
        filter=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Сите записи","За подоцна","Завршени"});filter.setAdapter(adapter);body.addView(filter,new LinearLayout.LayoutParams(-1,dp(52)));
        list=column();body.addView(list);
        search.addTextChangedListener(watch(()->refreshList()));
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int n,long id){refreshList();}public void onNothingSelected(AdapterView<?> p){}});
        refreshList();
    }
    void refreshList(){
        if(!page.equals("home")||list==null)return;list.removeAllViews();
        String status=filter.getSelectedItemPosition()==1?"draft":filter.getSelectedItemPosition()==2?"done":"";
        List<Store.Record> all=store.records(search.getText().toString(),status);
        if(all.isEmpty()){gap(list,30);list.addView(text("Нема записи",22,TEXT));list.addView(text("Со „Нов запис“ додај информации, фотографии и документи. Можеш да зачуваш и да продолжиш подоцна.",15,MUTED));return;}
        for(Store.Record r:all){
            LinearLayout card=column();card.setPadding(dp(15),dp(13),dp(15),dp(14));card.setBackground(bg(CARD,16));
            TextView h=text(r.title.isEmpty()?"Запис без наслов":r.title,19,TEXT);h.setTypeface(null,Typeface.BOLD);card.addView(h);
            card.addView(text((r.status.equals("done")?"ЗАВРШЕН":"ЗА ПОДОЦНА")+"  ·  "+r.date,12,r.status.equals("done")?ACCENT:0xffffcf80));
            if(!r.reference.isEmpty())card.addView(text("Број: "+r.reference,14,MUTED));
            List<Store.Asset> assets=store.assets(r.id);int photos=0;for(Store.Asset a:assets)if(a.kind.equals("photo"))photos++;
            card.addView(text(photos+" фотографии  ·  "+(assets.size()-photos)+" документи",13,MUTED));
            if(!r.notes.isEmpty()){TextView n=text(r.notes,14,MUTED);n.setMaxLines(2);n.setEllipsize(TextUtils.TruncateAt.END);card.addView(n);}
            fullButton(card,r.status.equals("done")?"Отвори запис":"Продолжи",false,()->{current=store.get(r.id);showFiles();});
            card.setOnLongClickListener(v->{confirmDeleteRecord(r);return true;});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);list.addView(card,p);
        }
    }
    TextWatcher watch(Runnable fn){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){fn.run();}public void afterTextChanged(Editable e){}};}
    EditText field(String label,String initial,int lines){
        gap(body,10);body.addView(text(label,13,MUTED));EditText e=new EditText(this);e.setText(initial);e.setTextColor(TEXT);e.setTextSize(16);e.setBackground(bg(INPUT,10));e.setPadding(dp(14),dp(12),dp(14),dp(12));
        if(lines==1)e.setSingleLine(true);else {e.setMinLines(lines);e.setGravity(Gravity.TOP);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);}
        body.addView(e,new LinearLayout.LayoutParams(-1,-2));e.addTextChangedListener(watch(()->{handler.removeCallbacks(saveText);handler.postDelayed(saveText,350);}));return e;
    }
    void showEdit(){
        page="edit";screen("Информации","Чекор 1 од 2",true);
        title=field("Наслов / име на записот",current.title,1);title.setTag("record-title");
        reference=field("Број / ознака (по избор)",current.reference,1);
        category=field("Категорија (по избор)",current.category,1);
        date=field("Датум",current.date,1);date.setFocusable(false);date.setOnClickListener(v->pickDate());
        notes=field("Опис и информации",current.notes,4);notes.setTag("record-notes");
        gap(body,14);fullButton(body,"Продолжи →",true,()->{if(saveFields(true)){hideKeyboard();showFiles();}});
        fullButton(body,"Зачувај за подоцна",false,()->saveLater());
    }
    void pickDate(){
        Calendar c=Calendar.getInstance();try{c.setTime(new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).parse(date.getText().toString()));}catch(Exception ignored){}
        new DatePickerDialog(this,(view,y,m,d)->{date.setText(String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d));},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }
    boolean saveFields(boolean alert){
        handler.removeCallbacks(saveText);if(current==null||title==null)return true;
        current.title=title.getText().toString();current.reference=reference.getText().toString();current.category=category.getText().toString();current.date=date.getText().toString();current.notes=notes.getText().toString();
        try{store.save(current);return true;}catch(Exception e){if(alert)error(e);return false;}
    }
    void saveLater(){
        if(!saveFields(true))return;try{current.status="draft";store.save(current);hideKeyboard();showHome();toast("Зачувано за подоцна");}catch(Exception e){error(e);}
    }
    void showFiles(){
        saveFields(false);current=store.get(current.id);page="files";screen(current.title.isEmpty()?"Запис без наслов":current.title,"Чекор 2 од 2 · "+current.date,true);
        body.addView(text(current.status.equals("done")?"Завршен запис":"Запис за подоцна",13,current.status.equals("done")?ACCENT:0xffffcf80));
        if(!current.reference.isEmpty())body.addView(text("Број: "+current.reference,14,MUTED));
        if(!current.category.isEmpty())body.addView(text(current.category,14,MUTED));
        if(!current.notes.isEmpty())body.addView(text(current.notes,15,TEXT));
        fullButton(body,"Уреди информации",false,()->showEdit());
        gap(body,14);body.addView(text("Фотографии и документи",20,TEXT));
        pair(body,"Фотографирај",()->takePhoto(),"Додај датотека",()->pickFiles());
        List<Store.Asset> all=store.assets(current.id);
        if(all.isEmpty()){gap(body,16);body.addView(text("Фотографирај или додај фотографии, Word, Excel и PDF. За секоја датотека можеш да внесеш посебен опис.",14,MUTED));}
        int gen=generation;
        for(Store.Asset a:all){
            gap(body,12);LinearLayout card=column();card.setBackground(bg(CARD,14));card.setPadding(dp(12),dp(12),dp(12),dp(12));
            if(a.kind.equals("photo")){
                ImageView image=new ImageView(this);image.setContentDescription(a.caption.trim().isEmpty()?"Фотографија":a.caption);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackground(bg(BG,8));card.addView(image,new LinearLayout.LayoutParams(-1,dp(180)));
                worker.execute(()->{try{Bitmap b=loadImage(store.file(a),400);handler.post(()->{if(generation==gen&&!isDestroyed())image.setImageBitmap(b);else if(b!=null)b.recycle();});}catch(Exception ignored){}});
                image.setOnClickListener(v->photoPreview(a));
            }
            TextView n=text(a.kind.equals("photo")?(a.caption.trim().isEmpty()?"Фотографија":a.caption):a.name,16,TEXT);n.setMaxLines(3);card.addView(n);
            if(!a.kind.equals("photo"))card.addView(text(a.caption.isEmpty()?"Без опис · допри „Опис“ за да додадеш":a.caption,14,MUTED));
            for(CardFields.Field f:CardFields.list(current))card.addView(text(f.label+": "+CardFields.value(a,f),14,MUTED));
            pair(card,"Опис",()->editCaption(a),a.kind.equals("photo")?"Преглед":"Отвори",()->{if(a.kind.equals("photo"))photoPreview(a);else openAsset(a);});
            TextView more=text("Повеќе  ⋮",14,ACCENT);more.setGravity(Gravity.END);more.setPadding(0,dp(14),0,dp(8));more.setMinHeight(dp(48));more.setOnClickListener(v->assetMenu(a));card.addView(more);body.addView(card);
        }
        gap(body,16);fullButton(body,"Архивска картичка · PDF / JPG",false,()->exportCard());
        fullButton(body,"Полиња за картичката",false,()->fieldMenu());
        fullButton(body,"Создај документ · PDF / Word / Excel",false,()->exportMenu());
        LinearLayout footer=row();footer.setPadding(0,dp(10),0,dp(8));
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.setMargins(0,0,dp(4),0);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-2,1);right.setMargins(dp(4),0,0,0);
        footer.addView(button("Зачувај за подоцна",false,()->saveLater()),left);
        footer.addView(button("Заврши и зачувај запис",true,()->{try{current.status="done";store.save(current);showHome();toast("Записот е зачуван");}catch(Exception e){error(e);}}),right);
        root.addView(footer);
    }
    EditText descriptionInput(LinearLayout box,String label,String value){
        TextView title=new TextView(this);title.setText(label);title.setTextSize(14);title.setTextColor(Color.DKGRAY);box.addView(title);
        EditText input=new EditText(this);input.setText(value);input.setTextColor(Color.BLACK);input.setMinLines(2);input.setGravity(Gravity.TOP);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);box.addView(input,new LinearLayout.LayoutParams(-1,-2));return input;
    }
    void saveDescription(Store.Asset asset,EditText caption,Map<String,EditText> fields) throws JSONException {
        JSONObject values=new JSONObject(asset.fields);for(Map.Entry<String,EditText> e:fields.entrySet())values.put(e.getKey(),e.getValue().getText().toString());
        store.describe(asset,caption.getText().toString(),values.toString());
    }
    void editCaption(Store.Asset a){
        current=store.get(a.recordId);LinearLayout box=column();box.setPadding(dp(20),dp(12),dp(20),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(box);
        EditText input=descriptionInput(box,"Опис",a.caption);input.setTag("asset-caption");
        Map<String,EditText> values=new LinkedHashMap<>();
        for(CardFields.Field f:CardFields.list(current))values.put(f.id,descriptionInput(box,f.label,CardFields.value(a,f)));
        Button add=new Button(this);add.setText("+ Додај поле");add.setAllCaps(false);box.addView(add);
        add.setOnClickListener(v->{EditText label=new EditText(this);label.setHint("Пример: Шифра, Назив, Големина");
            new AlertDialog.Builder(this).setTitle("Ново поле").setView(label).setNegativeButton("Откажи",null).setPositiveButton("Додај",(d,w)->{try{
                current=store.get(a.recordId);CardFields.add(store,current,label.getText().toString());List<CardFields.Field> all=CardFields.list(current);CardFields.Field f=all.get(all.size()-1);
                box.removeView(add);values.put(f.id,descriptionInput(box,f.label,""));box.addView(add);
            }catch(Exception e){error(e);}}).show();});
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("Опис и полиња").setView(scroll).setNegativeButton("Откажи",null).setPositiveButton("Зачувај",(d,w)->{try{saveDescription(a,input,values);showFiles();}catch(Exception e){error(e);}});
        if(a.kind.equals("photo"))dialog.setNeutralButton("Следна слика",(d,w)->{try{saveDescription(a,input,values);showFiles();takePhoto();}catch(Exception e){error(e);}});
        dialog.show();
    }
    void fieldMenu(){
        current=store.get(current.id);List<CardFields.Field> fields=CardFields.list(current);String[] labels=new String[fields.size()+1];labels[0]="+ Додај поле";for(int i=0;i<fields.size();i++)labels[i+1]=fields.get(i).label;
        new AlertDialog.Builder(this).setTitle("Полиња за сите слики во записот").setItems(labels,(d,n)->{
            CardFields.Field field=n==0?null:fields.get(n-1);EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Име на поле");if(field!=null)name.setText(field.label);
            new AlertDialog.Builder(this).setTitle(field==null?"Додај поле":"Преименувај поле").setView(name).setNegativeButton("Откажи",null).setPositiveButton("Зачувај",(x,w)->{try{current=store.get(current.id);if(field==null)CardFields.add(store,current,name.getText().toString());else CardFields.rename(store,current,field,name.getText().toString());showFiles();}catch(Exception e){error(e);}}).show();
        }).setNegativeButton("Затвори",null).show();
    }
    void exportCard(){
        new AlertDialog.Builder(this).setTitle("Архивска картичка").setItems(new String[]{"PDF за печатење","JPG слики од картичката"},(d,n)->exportCard(n==1)).show();
    }
    void exportCard(boolean images){
        Store.Record record=store.get(current.id);
        runJob("Ја создавам архивската картичка…",()->{
            File file=Exports.create(store,record,"card");
            if(!images){try{store.attach(record.id,file,Exports.label(record)+"_картичка.pdf","application/pdf","Архивска картичка");}catch(Exception e){file.delete();throw e;}}
            else{
                List<File> pages=new ArrayList<>();List<Store.Asset> attached=new ArrayList<>();boolean success=false;
                try{pages=ArchiveCards.images(store,record,file);for(int i=0;i<pages.size();i++)attached.add(store.attachCardImage(record.id,pages.get(i),Exports.label(record)+"_картичка_"+(i+1)+".jpg","Архивска картичка "+(i+1)+" / "+pages.size()));success=true;}
                finally{file.delete();if(!success){for(Store.Asset asset:attached)store.deleteAsset(asset);for(File page:pages)page.delete();}}
            }
            return images?"Сликите од картичката се додадени на записот":"Картичката е додадена на записот";
        },()->showFiles());
    }
    void takePhoto(){
        try{
            store.save(current);File f=store.mediaFile(current.id,"jpg");Uri uri=ArchiveProvider.uri(this,f);
            prefs().edit().putString("cameraPath",store.relative(f)).putString("cameraRecord",current.id).commit();
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,uri);i.setClipData(ClipData.newRawUri("Фотографија",uri));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(i,CAMERA);
        }catch(ActivityNotFoundException e){prefs().edit().remove("cameraPath").remove("cameraRecord").commit();toast("Нема достапна апликација за камера.");}catch(Exception e){error(e);}
    }
    void pickFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","application/pdf","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"});try{startActivityForResult(i,PICK);}catch(Exception e){error(e);}}
    @Override public void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==CAMERA){
            String name=prefs().getString("cameraPath",null),record=prefs().getString("cameraRecord",null);
            prefs().edit().remove("cameraPath").remove("cameraRecord").commit();
            if(name==null||record==null)return;
            File f;
            try{
                f=Store.safeFile(this,name.startsWith("media/")?name:"media/"+name);
                revokeUriPermission(ArchiveProvider.uri(this,f),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if(result==RESULT_OK&&f.length()>0){
                    Store.Asset a=store.attach(record,f,"Фото_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date())+".jpg","image/jpeg","");
                    current=store.get(record);showFiles();editCaption(a);
                }else{f.delete();if(result==RESULT_OK)toast("Камерата не зачува фотографија. Обиди се повторно.");}
            }catch(Exception e){error(e);}return;
        }
        if(result!=RESULT_OK||data==null||data.getData()==null&&data.getClipData()==null)return;
        if(request==PICK){
            List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int n=0;n<data.getClipData().getItemCount();n++)uris.add(data.getClipData().getItemAt(n).getUri());}else uris.add(data.getData());
            final String record=current.id;runJob("Ги зачувувам датотеките…",()->{
                int added=0;StringBuilder failures=new StringBuilder();
                for(Uri uri:uris){try{
                    String name=fileName(uri),mime=getContentResolver().getType(uri);String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
                    if(mime==null||mime.equals("application/octet-stream")){String detected=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);if(detected!=null)mime=detected;}
                    boolean image=mime!=null&&mime.startsWith("image/");boolean document=Arrays.asList("pdf","doc","docx","xls","xlsx").contains(ext);
                    if(!image&&!document)throw new IOException("Поддржани се фотографии, PDF, Word и Excel.");
                    try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Не може да се отвори.");store.importStream(record,in,name,mime);}added++;
                }catch(Exception e){failures.append("\n").append(e.getMessage());}}
                return "Додадени: "+added+(failures.length()==0?"":failures.toString());
            },()->showFiles());
        }else if(request==SAVE_FILE){
            String source=prefs().getString("savePath",null);if(source==null)return;Uri uri=data.getData();
            runJob("Зачувувам…",()->{File f=ownedFile(source);try(InputStream in=new FileInputStream(f);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Не може да се зачува.");Store.copy(in,out,Long.MAX_VALUE);}prefs().edit().remove("savePath").commit();if(source.startsWith("exports/"))f.delete();return "Датотеката е зачувана";},null);
        }else if(request==RESTORE){Uri uri=data.getData();runJob("Ја враќам резервната копија…",()->{try(InputStream in=getContentResolver().openInputStream(uri)){return ArchiveBackup.restore(store,in)+" записи се вратени";}},()->showHome());}
    }
    String fileName(Uri uri){String name="Датотека";try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}catch(Exception ignored){}return name==null?"Датотека":name;}
    void openAsset(Store.Asset a){try{File f=store.file(a);Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(ArchiveProvider.uri(this,f),a.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(ActivityNotFoundException e){toast("За овој формат инсталирај соодветен прегледувач (PDF / Word / Excel).");}catch(Exception e){error(e);}}
    void assetMenu(Store.Asset a){new AlertDialog.Builder(this).setTitle(a.kind.equals("photo")?(a.caption.trim().isEmpty()?"Фотографија":a.caption):a.name).setItems(new String[]{"Сподели","Зачувај во телефон","Отстрани од записот"},(d,n)->{try{if(n==0)share(store.file(a),a.mime);else if(n==1)saveExternal(store.file(a),a.name,a.mime);else new AlertDialog.Builder(this).setMessage("Да се отстрани оваа копија од записот?").setNegativeButton("Откажи",null).setPositiveButton("Отстрани",(x,w)->{try{store.deleteAsset(a);showFiles();}catch(Exception e){error(e);}}).show();}catch(Exception e){error(e);}}).show();}
    void photoPreview(Store.Asset a){
        runJob("Ја отворам фотографијата…",()->{
            Bitmap bitmap=loadImage(store.file(a),1800);if(bitmap==null)throw new IOException("Форматот не може да се прикаже. Користи надворешен прегледувач.");
            handler.post(()->{ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);ScrollView scroll=new ScrollView(this);scroll.addView(image);
                new AlertDialog.Builder(this).setTitle(a.caption.trim().isEmpty()?"Фотографија":a.caption).setView(scroll).setMessage(a.caption.isEmpty()?null:a.caption).setPositiveButton("Затвори",null).setNeutralButton("Отвори надворешно",(d,w)->openAsset(a)).show();});return "";
        },null);
    }
    static Bitmap loadImage(File file,int max) throws IOException {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),bounds);if(bounds.outWidth<=0)return null;
        BitmapFactory.Options o=new BitmapFactory.Options();if(max<=800)o.inPreferredConfig=Bitmap.Config.RGB_565;o.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/o.inSampleSize>max*2)o.inSampleSize*=2;
        Bitmap b=BitmapFactory.decodeFile(file.getPath(),o);if(b==null)return null;
        Matrix m=new Matrix();try{int orientation=new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;default:break;}
        }catch(IOException ignored){}
        if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(rotated!=b)b.recycle();b=rotated;}return b;
    }
    void exportMenu(){new AlertDialog.Builder(this).setTitle("Создај документ од записот").setItems(new String[]{"PDF · информации и фотографии","Word (.docx) · информации и фотографии","Excel (.xlsx) · информации и список"},(d,n)->{
        Store.Record r=store.get(current.id);String ext=n==0?"pdf":n==1?"docx":"xlsx";
        runJob("Го создавам документот…",()->{File f=Exports.create(store,r,ext);store.attach(r.id,f,(r.title.isEmpty()?"Запис":r.title)+"."+ext,Exports.mime(ext),"Создаден од записот");return "Документот е додаден на записот";},()->showFiles());
    }).show();}
    void share(File file,String mime) throws IOException {Uri uri=ArchiveProvider.uri(this,file);Intent i=new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("Документ",uri));startActivity(Intent.createChooser(i,"Сподели"));}
    void saveExternal(File f,String name,String mime) throws IOException {prefs().edit().putString("savePath",store.relative(f)).commit();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,SAVE_FILE);}
    File ownedFile(String path) throws IOException {File f=new File(getFilesDir(),path).getCanonicalFile();File root=getFilesDir().getCanonicalFile();if(path.startsWith("media/"))return Store.safeFile(this,path);if(!f.getPath().startsWith(root.getPath()+File.separator)||!path.matches("exports/[a-zA-Z0-9-]+\\.[a-zA-Z0-9]{1,10}"))throw new IOException("Недозволена патека.");return f;}
    void showMenu(){new AlertDialog.Builder(this).setTitle("Архива – документи").setItems(new String[]{"Зачувај резервна копија (сите записи)","Врати резервна копија","За апликацијата"},(d,n)->{
        if(n==0){final File[] f=new File[1];runJob("Создавам резервна копија…",()->{f[0]=ArchiveBackup.create(store);return "";},()->{try{saveExternal(f[0],"Arhiva_backup_"+new SimpleDateFormat("yyyy-MM-dd_HHmmss",Locale.ROOT).format(new Date())+".zip","application/zip");}catch(Exception e){error(e);}});}
        else if(n==1){new AlertDialog.Builder(this).setMessage("Постоечките записи ќе останат. Ќе се додадат записите од копијата што недостигаат.").setNegativeButton("Откажи",null).setPositiveButton("Избери копија",(x,w)->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(i,RESTORE);}).show();}
        else new AlertDialog.Builder(this).setTitle("Верзија 1.1").setMessage("Локална архива со фотографии, описи и Word, Excel и PDF документи.\n\n„Зачувај за подоцна“ го остава записот отворен за продолжување.\n\nРедовно зачувувај резервна копија за пренос на друг телефон.").setPositiveButton("Во ред",null).show();
    }).show();}
    void confirmDeleteRecord(Store.Record r){new AlertDialog.Builder(this).setTitle("Избриши запис?").setMessage("Ќе се избрише овој запис со копиите на фотографиите и документите во него.").setNegativeButton("Откажи",null).setPositiveButton("Избриши",(d,w)->{try{store.deleteRecord(r.id);refreshList();}catch(Exception e){error(e);}}).show();}
    interface Job {String run() throws Exception;}
    void runJob(String message,Job job,Runnable done){
        if(busy)return;busy=true;ProgressDialog progress=new ProgressDialog(this);progress.setMessage(message);progress.setCancelable(false);progress.show();
        worker.execute(()->{String result="";Exception failure=null;try{result=job.run();}catch(Exception e){failure=e;}
            final String output=result;final Exception error=failure;handler.post(()->{busy=false;if(!isDestroyed()){progress.dismiss();if(error!=null)error(error);else{if(done!=null)done.run();if(!output.isEmpty())toast(output);}}});});
    }
    void hideKeyboard(){View v=getCurrentFocus();if(v!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);}
    void goBack(){if(busy)return;if(page.equals("edit")){if(saveFields(true)){hideKeyboard();showHome();}}else if(page.equals("files"))showHome();else super.onBackPressed();}
    @Override public void onBackPressed(){goBack();}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    void error(Exception e){new AlertDialog.Builder(this).setTitle("Не е зачувано").setMessage(e.getMessage()==null?"Обиди се повторно.":e.getMessage()).setPositiveButton("Во ред",null).show();}
}
