package com.a1os.cloud.phone;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public final class PhoneUtil {

    static String A1OS_API = "http://www.aoidone.com/api/cloud_data.php?number=";
    static int VERSION = 1;
    
    static DBHelper dbHelper;
    static SQLiteDatabase db;

    public static void getNumberInfo(Context ctx, final String phoneNumber, final CallBack callBack) {

        final String PHONENUMBER_COMPLETE = phoneNumber.replaceAll("(?:-| )", "");
        final String A1OS_URL = A1OS_API + PHONENUMBER_COMPLETE;
        
        dbHelper = new StuDBHelper(ctx, "cloud_db", null, VERSION);
        db = dbHelper.getReadableDatabase();
        
        if (query(PHONENUMBER_COMPLETE) != null) {
            callBack.execute(query(PHONENUMBER_COMPLETE));
            db.close();
            return;
        }

        RequestQueue mQueue = Volley.newRequestQueue(ctx);

        StringRequest stringRequest = new StringRequest(A1OS_URL,
           new Response.Listener<String>() {
              @Override
              public void onResponse(String response) {
                  callBack.execute(response);
                  ContentValues cv = new ContentValues();
                  cv.put("phone", PHONENUMBER_COMPLETE);
                  cv.put("location", response);
                  db.insert("cloud_table", null, cv);
                  db.close();
              }
        }, new Response.ErrorListener() {
              @Override
              public void onErrorResponse(VolleyError error) {
                  Log.e("TAG", error.getMessage(), error);
              }
        });
        mQueue.add(stringRequest);
    }

    public static String query(String Number) {
    	Cursor cursor = db.query("cloud_table", new String[]{"phone", "location"},
                 null, null, null, null, null);
    	while(cursor.moveToNext()){
              if (Number.equals(cursor.getString(cursor.getColumnIndex("phone")))) {
                  return cursor.getString(cursor.getColumnIndex("location"));
              }
        }
        return null;
    }

    public interface CallBack {
        void execute(String response);
    }
}
