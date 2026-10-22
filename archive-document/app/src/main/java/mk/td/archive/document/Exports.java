package mk.td.archive.document;

import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.text.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Real OOXML files (not renamed HTML/CSV), plus Unicode PDF using Android fonts. */
public final class Exports {
    static final String DOCX="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final String XLSX="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static String mime(String ext){return ext.equals("pdf")?"application/pdf":ext.equals("docx")?DOCX:XLSX;}
    public static File create(Store store,Store.Record r,String ext) throws Exception {
        File f=store.mediaFile(ext);
        try{List<Store.Asset> assets=store.assets(r.id);if(ext.equals("pdf"))pdf(store,r,assets,f);else if(ext.equals("docx"))docx(store,r,assets,f);else if(ext.equals("xlsx"))xlsx(r,assets,f);else throw new IOException("Неподдржан формат.");return f;}
        catch(Exception e){f.delete();throw e;}
    }
    static String label(Store.Record r){return r.title.isEmpty()?"Запис без наслов":r.title;}
    static List<String[]> details(Store.Record r){return Arrays.asList(new String[]{"Наслов",label(r)},new String[]{"Број / ознака",r.reference},new String[]{"Категорија",r.category},new String[]{"Датум",r.date},new String[]{"Статус",r.status.equals("done")?"Завршен":"За подоцна"},new String[]{"Опис",r.notes});}
    static String xml(String s){if(s==null)return "";return s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]","").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    static void entry(ZipOutputStream z,String name,String text) throws IOException {z.putNextEntry(new ZipEntry(name));z.write(text.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    static final String CT="<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">";
    static final String REL="<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">";
    static String rel(String id,String type,String target){return "<Relationship Id=\""+id+"\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/"+type+"\" Target=\""+target+"\"/>";}
    static String para(String s,boolean bold){StringBuilder b=new StringBuilder();for(String line:s.split("\n",-1))b.append("<w:p><w:r>").append(bold?"<w:rPr><w:b/><w:sz w:val=\"28\"/></w:rPr>":"").append("<w:t xml:space=\"preserve\">").append(xml(line)).append("</w:t></w:r></w:p>");return b.toString();}
    static void docx(Store store,Store.Record r,List<Store.Asset> assets,File file) throws Exception {
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(file))){
            StringBuilder doc=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><w:body>");
            doc.append(para("АРХИВА · "+label(r),true));for(String[] row:details(r))if(!row[1].isEmpty())doc.append(para(row[0]+": "+row[1],false));
            doc.append(para("Прилози",true));for(Store.Asset a:assets){doc.append(para(a.name+(a.caption.isEmpty()?"":" — "+a.caption),false));}
            StringBuilder rels=new StringBuilder(REL);int n=0;
            for(Store.Asset a:assets)if(a.kind.equals("photo")){
                Bitmap image=MainActivity.loadImage(store.file(a),1400);if(image==null)continue;n++;
                long width=5200000,height=Math.round(width*(double)image.getHeight()/image.getWidth());if(height>6500000){width=Math.round(width*6500000.0/height);height=6500000;}
                z.putNextEntry(new ZipEntry("word/media/photo"+n+".jpg"));image.compress(Bitmap.CompressFormat.JPEG,88,z);z.closeEntry();image.recycle();
                rels.append(rel("photo"+n,"image","media/photo"+n+".jpg"));
                doc.append("<w:p><w:pPr><w:pageBreakBefore/></w:pPr></w:p>").append(para(a.name,true)).append(para(a.caption,false));
                doc.append("<w:p><w:r><w:drawing><wp:inline><wp:extent cx=\"").append(width).append("\" cy=\"").append(height).append("\"/><wp:docPr id=\"").append(n).append("\" name=\"Photo ").append(n).append("\"/><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic><pic:nvPicPr><pic:cNvPr id=\"0\" name=\"Photo\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"photo").append(n).append("\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(width).append("\" cy=\"").append(height).append("\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>");
            }
            doc.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"900\" w:right=\"900\" w:bottom=\"900\" w:left=\"900\"/></w:sectPr></w:body></w:document>");
            entry(z,"[Content_Types].xml",CT+"<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"jpg\" ContentType=\"image/jpeg\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            entry(z,"_rels/.rels",REL+rel("root","officeDocument","word/document.xml")+"</Relationships>");
            entry(z,"word/document.xml",doc.toString());entry(z,"word/_rels/document.xml.rels",rels+"</Relationships>");
        }
    }
    static String cell(String col,int row,String value,int style){return "<c r=\""+col+row+"\" s=\""+style+"\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"+xml(value)+"</t></is></c>";}
    static void xlsx(Store.Record r,List<Store.Asset> assets,File file) throws IOException {
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(file))){
            entry(z,"[Content_Types].xml",CT+"<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
            entry(z,"_rels/.rels",REL+rel("root","officeDocument","xl/workbook.xml")+"</Relationships>");
            entry(z,"xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Архива\" sheetId=\"1\" r:id=\"sheet1\"/></sheets></workbook>");
            entry(z,"xl/_rels/workbook.xml.rels",REL+rel("sheet1","worksheet","worksheets/sheet1.xml")+rel("styles","styles","styles.xml")+"</Relationships>");
            entry(z,"xl/styles.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment wrapText=\"1\" vertical=\"top\"/></xf><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
            StringBuilder sheet=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><cols><col min=\"1\" max=\"1\" width=\"32\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"70\" customWidth=\"1\"/><col min=\"3\" max=\"3\" width=\"25\" customWidth=\"1\"/></cols><sheetData>");
            int n=1;for(String[] d:details(r)){sheet.append("<row r=\"").append(n).append("\">").append(cell("A",n,d[0],1)).append(cell("B",n,d[1],0)).append("</row>");n++;}
            n++;sheet.append("<row r=\"").append(n).append("\">").append(cell("A",n,"Датотека",1)).append(cell("B",n,"Опис",1)).append(cell("C",n,"Тип",1)).append("</row>");
            for(Store.Asset a:assets){n++;sheet.append("<row r=\"").append(n).append("\">").append(cell("A",n,a.name,0)).append(cell("B",n,a.caption,0)).append(cell("C",n,a.kind.equals("photo")?"Фотографија":"Документ",0)).append("</row>");}
            sheet.append("</sheetData></worksheet>");entry(z,"xl/worksheets/sheet1.xml",sheet.toString());
        }
    }
    static void pdf(Store store,Store.Record r,List<Store.Asset> assets,File file) throws Exception {
        PdfPager p=new PdfPager();
        try{
            p.paragraph("АРХИВА",12,true);p.paragraph(label(r),24,true);
            for(String[] d:details(r))if(!d[1].isEmpty())p.paragraph(d[0]+": "+d[1],12,false);
            if(!assets.isEmpty())p.paragraph("Прилози",16,true);
            for(Store.Asset a:assets)p.paragraph(a.name+(a.caption.isEmpty()?"":" — "+a.caption),11,false);
            for(Store.Asset a:assets)if(a.kind.equals("photo")){
                Bitmap b=MainActivity.loadImage(store.file(a),1400);if(b==null){p.paragraph("Не може да се прикаже: "+a.name,12,false);continue;}
                p.newPage();p.paragraph(a.name,16,true);if(!a.caption.isEmpty())p.paragraph(a.caption,12,false);
                if(790-p.y<120)p.newPage();float width=511,height=790-p.y;float scale=Math.min(width/b.getWidth(),height/b.getHeight());
                float w=b.getWidth()*scale,h=b.getHeight()*scale;p.canvas.drawBitmap(b,null,new RectF(42+(width-w)/2,p.y,42+(width+w)/2,p.y+h),new Paint(Paint.FILTER_BITMAP_FLAG));p.y+=h+14;b.recycle();
            }
            p.finish();try(FileOutputStream out=new FileOutputStream(file)){p.doc.writeTo(out);out.getFD().sync();}
        }finally{p.close();}
    }
    static class PdfPager {
        final PdfDocument doc=new PdfDocument();PdfDocument.Page page;Canvas canvas;float y;int number=0;
        PdfPager(){newPage();}
        void finish(){if(page!=null){Paint footer=new Paint(Paint.ANTI_ALIAS_FLAG);footer.setTextSize(9);footer.setColor(Color.GRAY);canvas.drawText("Архива · "+number,42,820,footer);doc.finishPage(page);page=null;}}
        void newPage(){finish();page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,++number).create());canvas=page.getCanvas();y=42;}
        void paragraph(String s,int size,boolean bold){
            TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setTextSize(size);paint.setColor(Color.rgb(30,40,50));paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
            StaticLayout layout=StaticLayout.Builder.obtain(s,0,s.length(),paint,511).setIncludePad(false).setLineSpacing(3,1).build();
            for(int i=0;i<layout.getLineCount();i++){
                int height=layout.getLineBottom(i)-layout.getLineTop(i);if(y+height>790)newPage();
                canvas.save();canvas.clipRect(42,y,553,y+height);canvas.translate(42,y-layout.getLineTop(i));layout.draw(canvas);canvas.restore();y+=height;
            }y+=10;
        }
        void close(){finish();doc.close();}
    }
}
