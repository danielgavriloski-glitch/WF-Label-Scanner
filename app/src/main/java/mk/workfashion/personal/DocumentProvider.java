package mk.workfashion.personal;
import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

public class DocumentProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 private File resolve(Uri uri)throws FileNotFoundException{try{java.util.List<String> p=uri.getPathSegments();if(p.size()==2&&"documents".equals(p.get(0)))return Documents.file(getContext(),p.get(1));if(p.size()==2&&"capture".equals(p.get(0))&&"camera.jpg".equals(p.get(1)))return new File(getContext().getCacheDir(),"wfag-camera.jpg");}catch(Exception ignored){}throw new FileNotFoundException("Невалиден документ.");}
 @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{File f=resolve(uri);boolean capture=uri.getPath().equals("/capture/camera.jpg");if(!capture&&!"r".equals(mode))throw new FileNotFoundException("Само читање.");return ParcelFileDescriptor.open(f,capture&&!"r".equals(mode)?ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_READ_WRITE:ParcelFileDescriptor.MODE_READ_ONLY);}
 @Override public String getType(Uri uri){return Documents.mime(uri.getLastPathSegment()==null?"":uri.getLastPathSegment());}
 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){try{File f=resolve(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(cols[i])?f.getName():OpenableColumns.SIZE.equals(cols[i])?f.length():null;c.addRow(row);return c;}catch(Exception e){return null;}}
 @Override public Uri insert(Uri uri,ContentValues v){throw new UnsupportedOperationException();}
 @Override public int update(Uri uri,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
 @Override public int delete(Uri uri,String s,String[] a){throw new UnsupportedOperationException();}
}
