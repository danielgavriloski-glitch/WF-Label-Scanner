package mk.workfashion.personal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.OpenableColumns;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.zip.*;

/** Private, persistent document bytes; only metadata goes into the small JSON state. */
final class Documents {
 static final long MAX_FILE=25L*1024*1024,MAX_ARCHIVE=1024L*1024*1024;
 static File directory(Context c){File d=new File(c.getFilesDir(),"documents");if(!d.exists()&&!d.mkdirs())throw new IllegalStateException("Не може да се отвори папката за документите.");return d;}
 static File file(Context c,String id)throws IOException{if(id==null||!id.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}")||id.contains(".."))throw new IOException("Невалиден документ.");return new File(directory(c),id);}
 static String unique(String extension){return UUID.randomUUID().toString().replace("-","")+"."+extension;}
 static String mime(String name){String s=name.toLowerCase(java.util.Locale.ROOT);if(s.endsWith(".jpg")||s.endsWith(".jpeg"))return "image/jpeg";if(s.endsWith(".png"))return "image/png";if(s.endsWith(".webp"))return "image/webp";if(s.endsWith(".pdf"))return "application/pdf";if(s.endsWith(".docx"))return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";if(s.endsWith(".xlsx"))return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";if(s.endsWith(".doc"))return "application/msword";if(s.endsWith(".xls"))return "application/vnd.ms-excel";if(s.endsWith(".txt"))return "text/plain";return "application/octet-stream";}
 static String hash(File f)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
 static void transfer(InputStream in,OutputStream out,long max)throws Exception{byte[] b=new byte[32768];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>max)throw new IOException("Датотеката е поголема од дозволениот лимит.");out.write(b,0,n);}}
 static JSONObject metadata(File f,String name)throws Exception{if(f.length()<1||f.length()>MAX_FILE)throw new IOException("Документот е празен или преголем.");JSONObject d=new JSONObject();d.put("id","doc_"+UUID.randomUUID().toString());d.put("fileId",f.getName());d.put("name",name.length()>250?name.substring(0,250):name);d.put("mime",mime(f.getName()));d.put("bytes",f.length());d.put("sha256",hash(f));return d;}
 static Bitmap image(Context c,Uri uri)throws Exception{
  BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;try(InputStream in=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,o);}if(o.outWidth<1||o.outHeight<1)throw new IOException("Сликата не се прочита.");o.inJustDecodeBounds=false;o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>3200)o.inSampleSize*=2;
  Bitmap b;try(InputStream in=c.getContentResolver().openInputStream(uri)){b=BitmapFactory.decodeStream(in,null,o);}if(b==null)throw new IOException("Сликата не се прочита.");
  try(InputStream in=c.getContentResolver().openInputStream(uri)){int orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);Matrix m=new Matrix();if(orientation==6)m.postRotate(90);else if(orientation==3)m.postRotate(180);else if(orientation==8)m.postRotate(270);else if(orientation==2)m.postScale(-1,1);else if(orientation==4)m.postScale(1,-1);else if(orientation==5){m.postRotate(90);m.postScale(-1,1);}else if(orientation==7){m.postRotate(270);m.postScale(-1,1);}if(!m.isIdentity()){Bitmap r=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(r!=b)b.recycle();b=r;}}catch(IOException ignored){}
  return b;
 }
 static void writeJpeg(Bitmap source,File output)throws Exception{Bitmap b=source;int longest=Math.max(b.getWidth(),b.getHeight());if(longest>2600)b=Bitmap.createScaledBitmap(b,Math.max(1,b.getWidth()*2600/longest),Math.max(1,b.getHeight()*2600/longest),true);try(FileOutputStream out=new FileOutputStream(output)){if(!b.compress(Bitmap.CompressFormat.JPEG,86,out))throw new IOException("Скенот не се зачува.");out.getFD().sync();}finally{if(b!=source)b.recycle();}}
 static JSONObject importFile(Context c,Uri uri)throws Exception{
  String name="Документ";try(android.database.Cursor cur=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cur!=null&&cur.moveToFirst())name=cur.getString(0);}if(name==null||name.trim().isEmpty())name="Документ";String type=c.getContentResolver().getType(uri);String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(java.util.Locale.ROOT):"";
  boolean image=(type!=null&&type.startsWith("image/"))||ext.matches("jpg|jpeg|png|webp|heic|heif");if(image)ext="jpg";else if(!ext.matches("pdf|docx|xlsx|doc|xls|txt"))throw new IOException("Избери слика, PDF, Word, Excel или текстуален документ.");
  File f=file(c,unique(ext));try{if(image){Bitmap b=image(c,uri);try{writeJpeg(b,f);}finally{b.recycle();}if(!name.toLowerCase(java.util.Locale.ROOT).endsWith(".jpg"))name=name+".jpg";}else try(InputStream in=c.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(f)){if(in==null)throw new IOException("Нема пристап до документот.");transfer(in,out,MAX_FILE);out.getFD().sync();}return metadata(f,name);}catch(Exception e){f.delete();throw e;}
 }
 static JSONArray documentList(JSONObject state)throws Exception{JSONArray result=new JSONArray();collectDocuments(state,result,0);return result;}
 private static void collectDocuments(Object value,JSONArray result,int depth)throws Exception{
  if(depth>100)throw new IOException("Бекапот има премногу вгнездени податоци.");
  if(value instanceof JSONObject){JSONObject obj=(JSONObject)value;
   if(obj.has("fileId")&&obj.has("bytes")&&obj.has("sha256")){result.put(obj);return;}
   java.util.Iterator<String> keys=obj.keys();while(keys.hasNext())collectDocuments(obj.opt(keys.next()),result,depth+1);
  }else if(value instanceof JSONArray){JSONArray items=(JSONArray)value;for(int i=0;i<items.length();i++)collectDocuments(items.opt(i),result,depth+1);}
 }
 private static boolean matches(File f,JSONObject d)throws Exception{return f.isFile()&&f.length()==d.getLong("bytes")&&hash(f).equalsIgnoreCase(d.getString("sha256"));}
 static boolean availableState(Context c,JSONObject state,File stage){try{JSONArray docs=documentList(state);for(int i=0;i<docs.length();i++){JSONObject d=docs.getJSONObject(i);File local=file(c,d.getString("fileId"));if(matches(local,d))continue;File staged=stage==null?null:new File(stage,"documents/"+d.getString("fileId"));if(staged==null||!matches(staged,d))return false;}return true;}catch(Exception e){return false;}}
 static boolean archiveComplete(JSONObject state,File stage){try{JSONArray docs=documentList(state);for(int i=0;i<docs.length();i++){JSONObject d=docs.getJSONObject(i);if(!matches(new File(stage,"documents/"+d.getString("fileId")),d))return false;}return true;}catch(Exception e){return false;}}
 static boolean available(Context c,JSONObject log,File stage){try{return availableState(c,new JSONObject().put("warehouseLog",log),stage);}catch(Exception e){return false;}}
 static String normalizeState(String text)throws Exception{
  if(text==null)throw new IOException("Во бекапот нема записи.");String clean=text.trim();if(clean.startsWith("\uFEFF"))clean=clean.substring(1).trim();
  JSONObject state=new JSONObject(clean);boolean wrapped=false;
  if(!"workfashion-personal-time".equals(state.optString("appId"))){
   for(String key:new String[]{"state","data","workfashionState"}){
    Object nested=state.opt(key);JSONObject candidate=nested instanceof JSONObject?(JSONObject)nested:nested instanceof String?new JSONObject((String)nested):null;
    if(candidate!=null&&"workfashion-personal-time".equals(candidate.optString("appId"))){state=candidate;wrapped=true;break;}
   }
  }
  if(!"workfashion-personal-time".equals(state.optString("appId"))||state.optInt("schemaVersion",1)!=1)throw new IOException("Ова не е поддржан WFAG бекап.");
  if(!state.has("schemaVersion")){state.put("schemaVersion",1);wrapped=true;}
  if(state.has("notificationHistory"))NotificationHistory.normalize(state.get("notificationHistory"));
  return wrapped?state.toString():clean.equals(text)?text:clean;
 }
 static void exportArchive(Context c,OutputStream output,String state,String gps)throws Exception{
  String normalized=normalizeState(state);JSONObject s=new JSONObject(normalized);JSONArray docs=documentList(s);
  if(!availableState(c,s,null))throw new IOException("Недостасува или е оштетен приложен документ. Целосниот бекап не е завршен.");
  JSONObject history=NotificationHistory.forBackup(c);
  try(ZipOutputStream out=new ZipOutputStream(output)){
   putText(out,"state.json",normalized);putText(out,"gps.json",gps);putText(out,"notifications.json",history.toString());
   putText(out,"backup-info.json",new JSONObject().put("format","wfag-full-backup").put("version",2).put("createdAt",java.time.Instant.now().toString()).put("package",c.getPackageName()).toString());
   java.util.Set<String> seen=new java.util.HashSet<>();java.util.Collections.addAll(seen,"state.json","gps.json","notifications.json","backup-info.json");
   for(int i=0;i<docs.length();i++){String name=docs.getJSONObject(i).getString("fileId"),entry="documents/"+name;if(!seen.add(entry))continue;out.putNextEntry(new ZipEntry(entry));try(InputStream in=new FileInputStream(file(c,name))){transfer(in,out,MAX_FILE);}out.closeEntry();}
   exportExtras(out,new File(c.getFilesDir(),"wfag-backup-extras"),"",seen);
  }
 }
 private static void exportExtras(ZipOutputStream out,File folder,String prefix,java.util.Set<String> seen)throws Exception{
  File[] files=folder.listFiles();if(files==null)return;for(File f:files){String name=prefix+f.getName();if(f.isDirectory()){exportExtras(out,f,name+"/",seen);continue;}if(!seen.add(name))continue;out.putNextEntry(new ZipEntry(name));try(InputStream in=new FileInputStream(f)){transfer(in,out,MAX_FILE);}out.closeEntry();}
 }
 static void putText(ZipOutputStream out,String name,String text)throws Exception{out.putNextEntry(new ZipEntry(name));out.write(text.getBytes(StandardCharsets.UTF_8));out.closeEntry();}
 private static String safeEntry(String name)throws IOException{
  if(name==null||name.isEmpty()||name.length()>1000||name.startsWith("/")||name.contains("\\")||name.indexOf('\0')>=0)throw new IOException("Невалидна патека во бекапот.");
  for(String part:name.split("/"))if(part.equals("..")||part.equals("."))throw new IOException("Невалидна патека во бекапот.");return name;
 }
 static String stageArchive(Context c,InputStream input,File stage)throws Exception{
  removeTree(stage);if(!stage.mkdirs())throw new IOException("Не може да се подготви папката за враќање на бекапот.");
  long total=0;int count=0;java.util.Set<String> seen=new java.util.HashSet<>(),destinations=new java.util.HashSet<>();
  try(ZipInputStream in=new ZipInputStream(input)){ZipEntry entry;while((entry=in.getNextEntry())!=null){
   String name=safeEntry(entry.getName());if(entry.isDirectory())continue;
   if(!seen.add(name)||++count>50000)throw new IOException("Невалидна или предолга архива.");
   String base=name.substring(name.lastIndexOf('/')+1),target="extras/"+name;
   boolean json=base.toLowerCase(java.util.Locale.ROOT).endsWith(".json");
   if(base.equals("state.json")||base.equals("workfashion-state.json"))target="state.json";
   else if(base.equals("gps.json")||base.equals("wfag-routes.json"))target="gps.json";
   else if(base.equals("notifications.json")||base.equals("wfag-notifications.json"))target="notifications.json";
   else if(name.startsWith("documents/")||name.contains("/documents/")){file(c,base);target="documents/"+base;}
   if(!destinations.add(target))throw new IOException("Дупликат датотека во бекапот.");
   File f=new File(stage,target);File parent=f.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Не може да се подготви бекапот.");
   try(OutputStream out=new FileOutputStream(f)){transfer(in,out,json?16L*1024*1024:MAX_FILE);}
   total+=f.length();if(total>MAX_ARCHIVE)throw new IOException("Бекапот е поголем од 1 GB.");in.closeEntry();
   if(target.startsWith("extras/")&&json&&!new File(stage,"state.json").exists()&&(base.startsWith("Workfashion_backup_")||base.startsWith("WFAG_backup_"))){
    String normalized=normalizeState(new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));java.nio.file.Files.write(new File(stage,"state.json").toPath(),normalized.getBytes(StandardCharsets.UTF_8));
   }
  }}catch(Exception e){removeTree(stage);throw e;}
  try{
   File stateFile=new File(stage,"state.json");if(!stateFile.isFile())throw new IOException("Во бекапот нема записи.");
   String text=normalizeState(new String(java.nio.file.Files.readAllBytes(stateFile.toPath()),StandardCharsets.UTF_8));JSONObject s=new JSONObject(text);
   java.nio.file.Files.write(stateFile.toPath(),text.getBytes(StandardCharsets.UTF_8));
   if(!archiveComplete(s,stage))throw new IOException("ZIP бекапот содржи оштетен или недостасувачки документ.");
   File history=new File(stage,"notifications.json");if(history.isFile())NotificationHistory.normalize(new JSONObject(new String(java.nio.file.Files.readAllBytes(history.toPath()),StandardCharsets.UTF_8)));
   File gps=new File(stage,"gps.json");if(gps.isFile())new JSONObject(new String(java.nio.file.Files.readAllBytes(gps.toPath()),StandardCharsets.UTF_8));
   return text;
  }catch(Exception e){removeTree(stage);throw e;}
 }
 static void installArchive(Context c,File stage)throws Exception{
  File stateFile=new File(stage,"state.json");if(!stateFile.isFile())throw new IOException("Нема подготвен бекап за враќање.");
  JSONObject state=new JSONObject(normalizeState(new String(java.nio.file.Files.readAllBytes(stateFile.toPath()),StandardCharsets.UTF_8)));
  if(!availableState(c,state,stage))throw new IOException("Бекапот нема исправни приложени документи.");
  File dir=new File(stage,"documents");File[] files=dir.listFiles();
  // Validate every collision before copying even one document.
  if(files!=null)for(File f:files){File dest=file(c,f.getName());if(dest.exists()&&!hash(dest).equals(hash(f)))throw new IOException("Конфликт на документ. Сегашната датотека не е препишана.");}
  if(files!=null)for(File f:files){File dest=file(c,f.getName());if(dest.exists())continue;copyAtomic(f,dest);}
  File gps=new File(stage,"gps.json");if(gps.isFile()){
   JSONObject archive=new JSONObject(new String(java.nio.file.Files.readAllBytes(gps.toPath()),StandardCharsets.UTF_8));
   // Old backups may contain only savedPlaces (or an empty GPS object).
   if(!archive.has("sessions"))archive.put("sessions",new JSONArray());
   RouteTrackingService.importArchive(c,archive.toString());
  }
  File history=new File(stage,"notifications.json");if(history.isFile())NotificationHistory.merge(c,new JSONObject(new String(java.nio.file.Files.readAllBytes(history.toPath()),StandardCharsets.UTF_8)));
  if(state.has("notificationHistory"))NotificationHistory.merge(c,state.get("notificationHistory"));
  installExtras(new File(stage,"extras"),new File(c.getFilesDir(),"wfag-backup-extras"));
 }
 private static void copyAtomic(File source,File destination)throws Exception{
  File parent=destination.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Не може да се врати датотеката.");
  android.util.AtomicFile atomic=new android.util.AtomicFile(destination);FileOutputStream out=null;
  try{out=atomic.startWrite();try(InputStream in=new FileInputStream(source)){transfer(in,out,MAX_FILE);}atomic.finishWrite(out);}catch(Exception e){if(out!=null)atomic.failWrite(out);throw e;}
 }
 private static void installExtras(File source,File destination)throws Exception{
  File[] files=source.listFiles();if(files==null)return;
  for(File f:files){File target=new File(destination,f.getName());if(f.isDirectory()){installExtras(f,target);continue;}
   if(target.exists()){if(hash(target).equals(hash(f)))continue;target=new File(destination,"restored_"+hash(f).substring(0,12)+"_"+f.getName());}
   copyAtomic(f,target);
  }
 }
 static void removeTree(File f){File[] children=f.listFiles();if(children!=null)for(File c:children)removeTree(c);f.delete();}
}
