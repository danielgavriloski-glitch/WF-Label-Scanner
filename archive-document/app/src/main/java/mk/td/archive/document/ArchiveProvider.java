package mk.td.archive.document;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.*;
import java.util.*;

/** Private per-record files with temporary camera/viewer grants. */
public class ArchiveProvider extends ContentProvider {
    public static Uri uri(Context c,File f) throws IOException {
        String root=c.getFilesDir().getCanonicalPath()+File.separator,path=f.getCanonicalPath();
        if(!path.startsWith(root))throw new IOException("Недозволена датотека.");
        Uri.Builder b=new Uri.Builder().scheme("content").authority(c.getPackageName()+".files");
        for(String part:path.substring(root.length()).split("/"))b.appendPath(part);return b.build();
    }
    File resolve(Uri u) throws FileNotFoundException {
        List<String> p=u.getPathSegments();
        try {
            String path=android.text.TextUtils.join("/",p);
            if(!p.isEmpty()&&p.get(0).equals("media"))return Store.safeFile(getContext(),path);
            if(p.size()==2&&path.matches("exports/[a-zA-Z0-9-]+\\.[a-zA-Z0-9]{1,10}")){
                File root=new File(getContext().getFilesDir(),"exports").getCanonicalFile(),f=new File(root,p.get(1)).getCanonicalFile();
                if(root.equals(f.getParentFile()))return f;
            }
        }catch(IOException e){throw new FileNotFoundException(e.getMessage());}
        throw new FileNotFoundException("Invalid URI");
    }
    @Override public boolean onCreate(){return true;}
    @Override public ParcelFileDescriptor openFile(Uri u,String mode) throws FileNotFoundException {
        File f=resolve(u);
        if(!mode.equals("r")&&!u.getPathSegments().get(0).equals("media"))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(f,ParcelFileDescriptor.parseMode(mode));
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
