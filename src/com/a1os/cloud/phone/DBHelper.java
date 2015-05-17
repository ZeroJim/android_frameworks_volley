package com.a1os.cloud.phone;
 
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
 
public class DBHelper extends SQLiteOpenHelper {

    private static final String TAG = "DBHelper";

    public DBHelper(Context context, String name, CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    public void onCreate(SQLiteDatabase db) {
        String sql = "create table cloud_table(phone varchar(20),location varchar(20))";
        db.execSQL(sql);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
}  
