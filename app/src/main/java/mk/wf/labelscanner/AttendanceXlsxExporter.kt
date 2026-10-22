package mk.wf.labelscanner

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AttendanceXlsxExporter {
    fun write(out: OutputStream, daily: List<List<String>>, weekly: List<List<String>>) {
        ZipOutputStream(out).use { z ->
            put(z,"[Content_Types].xml",ct()); put(z,"_rels/.rels",rels())
            put(z,"xl/workbook.xml",wb()); put(z,"xl/_rels/workbook.xml.rels",wbr())
            put(z,"xl/styles.xml",styles()); put(z,"xl/worksheets/sheet1.xml",sheet(daily))
            put(z,"xl/worksheets/sheet2.xml",sheet(weekly))
        }
    }
    private fun put(z:ZipOutputStream,p:String,s:String){z.putNextEntry(ZipEntry(p));z.write(s.toByteArray());z.closeEntry()}
    private fun ct()="""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
    private fun rels()="""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private fun wb()="""<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Dnevno" sheetId="1" r:id="rId1"/><sheet name="Nedelno" sheetId="2" r:id="rId2"/></sheets></workbook>"""
    private fun wbr()="""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
    private fun styles()="""<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs></styleSheet>"""
    private fun sheet(rows:List<List<String>>):String{
        val b=StringBuilder("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed{ri,row->
            val rn=ri+1; b.append("<row r=\"").append(rn).append("\">")
            row.forEachIndexed{ci,v->
                val st=if(ri==0)" s=\"1\"" else ""
                b.append("<c r=\"").append(col(ci+1)).append(rn).append("\" t=\"inlineStr\"").append(st)
                    .append("><is><t xml:space=\"preserve\">").append(esc(v)).append("</t></is></c>")
            }
            b.append("</row>")
        }
        return b.append("</sheetData></worksheet>").toString()
    }
    private fun col(i:Int):String{var n=i;val s=StringBuilder();while(n>0){n--;s.append(('A'.code+n%26).toChar());n/=26};return s.reverse().toString()}
    private fun esc(v:String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
}
