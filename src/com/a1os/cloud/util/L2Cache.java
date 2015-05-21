package com.a1os.cloud.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.util.Log;

public class L2Cache implements StringCache {

    private static final String TAG = L2Cache.class.getSimpleName();

    private DiskCache mDiskCache;
 
    private static int IO_BUFFER_SIZE = 8 * 1024;

    private static final int APP_VERSION = 1;
    private static final int VALUE_COUNT = 1;

    public L2Cache(Context ctx, long diskCacheSize) {
        try {
             File diskCacheDir = getDiskCacheDir(ctx);
             if (!diskCacheDir.exists()) {
                 diskCacheDir.mkdirs();
             }
             mDiskCache = DiskCache.open(diskCacheDir, APP_VERSION, VALUE_COUNT, diskCacheSize);
        } catch (IOException e) {
             e.printStackTrace();
        }
    }

    private boolean writeStringToFile(String str, DiskCache.Editor editor)
        throws IOException, FileNotFoundException {
        OutputStream out = null;
        try {
             out = new BufferedOutputStream(editor.newOutputStream(0), IO_BUFFER_SIZE);
             out.write(str.getBytes("UTF-8"));
             return true;
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    private File getDiskCacheDir(Context ctx) {
        return new File(ctx.getExternalCacheDir().getPath() + File.separator + "cloud");
    }

    @Override
    public void put(String key, String value) {
        DiskCache.Editor editor = null;
        try {
             editor = mDiskCache.edit(key);
             if (editor == null) {
                 return;
             }
 
             if (writeStringToFile(value, editor)) {               
                 mDiskCache.flush();
                 editor.commit();
             } else {
                 editor.abort();
             }   
        } catch (IOException e) {
            try {
                if (editor != null) {
                    editor.abort();
                }
            } catch (IOException ignored) {
            }           
        }
    }
 
    @Override
    public String get(String key) {
        String value = null;
        DiskCache.Snapshot snapshot = null;
        try {
             snapshot = mDiskCache.get(key);
             if (snapshot == null) {
                 return null;
             }
             final InputStream in = snapshot.getInputStream(0);
             if (in != null) {
                 final BufferedInputStream buffIn = new BufferedInputStream(in, IO_BUFFER_SIZE);
                 byte[] contents = new byte[1024];

                 int bytesRead = 0;
                 StringBuffer strFileContents=new StringBuffer(); 
                 while((bytesRead = buffIn.read(contents)) != -1){ 
                        strFileContents.append(new String(contents, 0, bytesRead));
                 } 
                 value=strFileContents.toString();
             }   
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (snapshot != null) {
                snapshot.close();
            }
        }
        return value;
    }
 
    public boolean containsKey(String key) {
        boolean contained = false;
        DiskCache.Snapshot snapshot = null;
        try {
             snapshot = mDiskCache.get(key);
             contained = snapshot != null;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (snapshot != null) {
                snapshot.close();
            }
        }
        return contained;
    }

    public void clearCache() {
        try {
             mDiskCache.delete();
        } catch (IOException e) {
             e.printStackTrace();
        }
    }
 
    public File getCacheFolder() {
        return mDiskCache.getDirectory();
    }
}
