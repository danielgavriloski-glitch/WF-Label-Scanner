package mk.td.archive.document;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.*;
import java.util.*;

/** Non-exported provider; Android checks per-URI grants for camera/viewer access. */
public class ArchiveProvider extends ContentProvider {
    public static Uri uri(Context c,File f){
        String folder=f.getParentFile().getName();
        return new Uri.Builder().scheme("content").authority(c.getPackageName()+".files").appendPath(folder).appendPath(f.getName()).build();
    }
    File resolve(Uri u) throws FileNotFoundException {
        List<String> p=u.getPathSegments();
        if(p.size()!=2||!(p.get(0).equals("media")||p.get(0).equals("exports")))throw new FileNotFoundException("Invalid URI");
        try {
            File root=new File(getContext().getFilesDir(),p.get(0)).getCanonicalFile();
            File file=new File(root,p.get(1)).getCanonicalFile();
            if(!root.equals(file.getParentFile()))throw new FileNotFoundException("Invalid path");
            return file;
        } catch(IOException e){throw new FileNotFoundException(e.getMessage());}
    }
    @Override public boolean onCreate(){return true;}
    @Override public ParcelFileDescriptor openFile(Uri u,String mode) throws FileNotFoundException {
        File f=resolve(u);
        int flags=ParcelFileDescriptor.parseMode(mode);
        if(!mode.equals("r")&&!f.getParentFile().getName().equals("media"))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(f,flags);
    }
    @Override public String getType(Uri u){String ext=MimeTypeMap.getFileExtensionFromUrl(u.toString()).toLowerCase(Locale.ROOT);String type=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);return type==null?"application/octet-stream":type;}
    @Override public Cursor query(Uri u,String[] projection,String selection,String[] args,String sort){
        try{File f=resolve(u);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor c=new MatrixCursor(columns);Object[] row=new Object[columns.length];
            for(int i=0;i<columns.length;i++)row[i]=columns[i].equals(OpenableColumns.DISPLAY_NAME)?f.getName():columns[i].equals(OpenableColumns.SIZE)?f.length():null;
            c.addRow(row);return c;
        }catch(FileNotFoundException e){return null;}
    }
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
