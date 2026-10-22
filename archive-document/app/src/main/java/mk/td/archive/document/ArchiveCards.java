package mk.td.archive.document;

import android.graphics.*;
import android.text.*;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.*;

/** A4 identification sheets: description/field table followed by matching real photos. */
public final class ArchiveCards {
    static final float LEFT=42,WIDTH=511,BOTTOM=790,LINE=13;
    static final int INK=0xff24201b,CREAM=0xfff7f0e5,RUST=0xffa34f26;
    public static List<File> images(Store store,Store.Record record,File pdf) throws IOException {
        List<File> output=new ArrayList<>();boolean success=false;
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer document=new PdfRenderer(fd)){
            for(int i=0;i<document.getPageCount();i++)try(PdfRenderer.Page page=document.openPage(i)){
                Bitmap image=Bitmap.createBitmap(page.getWidth()*3,page.getHeight()*3,Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);File file=store.mediaFile(record.id,"jpg");output.add(file);
                try{page.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);try(FileOutputStream out=new FileOutputStream(file)){if(!image.compress(Bitmap.CompressFormat.JPEG,94,out))throw new IOException("Не може да се создаде сликата.");out.getFD().sync();}}
                finally{image.recycle();}
            }success=true;return output;
        }finally{if(!success)for(File file:output)file.delete();}
    }
    static TextPaint paint(float size,boolean bold){TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(size);p.setColor(INK);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return p;}
    static List<String> lines(String value,TextPaint paint,int width){
        String s=value==null?"":value;StaticLayout layout=StaticLayout.Builder.obtain(s,0,s.length(),paint,width).setIncludePad(false).build();List<String> out=new ArrayList<>();
        for(int i=0;i<layout.getLineCount();i++)out.add(s.substring(layout.getLineStart(i),layout.getLineEnd(i)).replace("\n",""));if(out.isEmpty())out.add("");return out;
    }
    static void heading(Exports.PdfPager p,Store.Record r,int first,int last){
        p.paragraph(Exports.label(r),19,true);String sub="Фотографии "+String.format(Locale.ROOT,"%02d-%02d",first,last);
        if(!r.reference.isEmpty())sub+=" | "+r.reference;if(!r.date.isEmpty())sub+=" | "+r.date;p.paragraph(sub,10,false);
    }
    static void next(Exports.PdfPager p,Store.Record r,int first,int last){p.newPage();heading(p,r,first,last);}
    static void row(Exports.PdfPager pager,List<List<String>> cells,float[] widths,int start,int count,boolean header){
        Canvas c=pager.canvas;float h=12+count*LINE,x=LEFT;Paint background=new Paint();background.setColor(header?0xffebe2d4:Color.WHITE);c.drawRect(LEFT,pager.y,LEFT+WIDTH,pager.y+h,background);
        TextPaint text=paint(9.5f,header);Paint border=new Paint(Paint.ANTI_ALIAS_FLAG);border.setColor(INK);border.setStyle(Paint.Style.STROKE);border.setStrokeWidth(0.5f);
        for(int i=0;i<cells.size();i++){
            c.drawRect(x,pager.y,x+widths[i],pager.y+h,border);List<String> lines=cells.get(i);
            for(int j=0;j<count&&start+j<lines.size();j++)c.drawText(lines.get(start+j),x+6,pager.y+6-text.ascent()+j*LINE,text);x+=widths[i];
        }pager.y+=h;
    }
    static List<List<String>> wrap(List<String> cells,float[] widths,boolean bold){List<List<String>> out=new ArrayList<>();for(int i=0;i<cells.size();i++)out.add(lines(cells.get(i),paint(9.5f,bold),(int)widths[i]-12));return out;}
    static int height(List<List<String>> row){int n=1;for(List<String> cell:row)n=Math.max(n,cell.size());return n;}
    static void table(Exports.PdfPager p,Store.Record r,List<Store.Asset> photos,List<CardFields.Field> fields,int first,int last){
        int columns=fields.size();float caption=columns==0?477:columns==1?245:columns==2?195:165;
        float[] widths=new float[columns+2];widths[0]=34;widths[1]=caption;for(int i=2;i<widths.length;i++)widths[i]=(WIDTH-34-caption)/columns;
        List<String> headers=new ArrayList<>(Arrays.asList("Бр.","Опис"));for(CardFields.Field f:fields)headers.add(f.label);
        List<List<String>> head=wrap(headers,widths,true);int headLines=height(head);float hh=12+headLines*LINE;
        if(p.y+hh+25>BOTTOM)next(p,r,first,last);row(p,head,widths,0,headLines,true);
        for(int n=0;n<photos.size();n++){
            Store.Asset a=photos.get(n);List<String> values=new ArrayList<>(Arrays.asList(String.format(Locale.ROOT,"%02d",first+n),a.caption));for(CardFields.Field f:fields)values.add(CardFields.value(a,f));
            List<List<String>> wrapped=wrap(values,widths,false);int offset=0,total=height(wrapped);
            while(offset<total){
                int available=(int)((BOTTOM-p.y-12)/LINE);if(available<1){next(p,r,first,last);row(p,head,widths,0,headLines,true);available=(int)((BOTTOM-p.y-12)/LINE);}
                int take=Math.min(available,total-offset);row(p,wrapped,widths,offset,take,false);offset+=take;
            }
        }p.y+=12;
    }
    static void photos(Exports.PdfPager p,Store store,Store.Record r,List<Store.Asset> photos,int first,int last) throws IOException {
        int rows=(photos.size()+1)/2;float ph=208,total=rows*ph;
        if(p.y+total>BOTTOM)next(p,r,first,last);float top=p.y,pw=WIDTH/2;
        TextPaint number=paint(10,true),caption=paint(10,false);Paint border=new Paint(Paint.ANTI_ALIAS_FLAG);border.setStyle(Paint.Style.STROKE);border.setStrokeWidth(0.6f);border.setColor(INK);
        for(int i=0;i<photos.size();i++){
            Store.Asset a=photos.get(i);float x=LEFT+(i%2)*pw,y=top+(i/2)*ph;Paint fill=new Paint();fill.setColor(CREAM);p.canvas.drawRect(x,y,x+pw,y+34,fill);
            p.canvas.drawText(String.format(Locale.ROOT,"%02d",first+i),x+8,y+20,number);
            CharSequence shortCaption=TextUtils.ellipsize(a.caption.replace('\n',' '),caption,pw-44,TextUtils.TruncateAt.END);p.canvas.drawText(shortCaption.toString(),x+35,y+20,caption);
            Bitmap b=MainActivity.loadImage(store.file(a),1400);
            if(b!=null){float w=pw-12,h=ph-46,scale=Math.min(w/b.getWidth(),h/b.getHeight()),bw=b.getWidth()*scale,bh=b.getHeight()*scale;
                p.canvas.drawBitmap(b,null,new RectF(x+6+(w-bw)/2,y+40+(h-bh)/2,x+6+(w+bw)/2,y+40+(h+bh)/2),new Paint(Paint.FILTER_BITMAP_FLAG));b.recycle();
            }else{p.canvas.drawText("Не може да се прикаже фотографијата",x+8,y+80,paint(10,false));}
            p.canvas.drawRect(x,y,x+pw,y+ph,border);
        }p.y=top+total+12;
    }
    public static void write(Store store,Store.Record record,List<Store.Asset> all,File dest) throws Exception {
        List<Store.Asset> images=new ArrayList<>();for(Store.Asset a:all)if(a.kind.equals("photo"))images.add(a);
        if(images.isEmpty())throw new IOException("Додај фотографија за да создадеш архивска картичка.");
        Exports.PdfPager p=new Exports.PdfPager();
        try{
            List<CardFields.Field> fields=CardFields.list(record);
            for(int offset=0;offset<images.size();offset+=4){
                List<Store.Asset> group=images.subList(offset,Math.min(offset+4,images.size()));int first=offset+1,last=offset+group.size();
                if(offset>0)p.newPage();heading(p,record,first,last);if(!record.notes.isEmpty())p.paragraph(record.notes,11,false);
                if(fields.isEmpty())table(p,record,group,fields,first,last);
                else for(int f=0;f<fields.size();f+=3)table(p,record,group,fields.subList(f,Math.min(f+3,fields.size())),first,last);
                photos(p,store,record,group,first,last);
            }
            p.finish();try(FileOutputStream out=new FileOutputStream(dest)){p.doc.writeTo(out);out.getFD().sync();}
        }finally{p.close();}
    }
}
