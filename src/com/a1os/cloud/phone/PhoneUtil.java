package com.a1os.cloud.phone;

import java.util.HashMap;

import com.a1os.cloud.request.APIRequest;
import com.a1os.cloud.util.L2Cache;
import com.a1os.cloud.util.StringCache;

import com.android.volley.AuthFailureError;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.Request.Method;
import com.android.volley.toolbox.Volley;

import android.content.Context;
import android.util.Log;

public final class PhoneUtil {

    static String A1OS_API = "http://aoidone.com/api/cloud_data.php?number=";
    static String UPLOAD_API = "http://aoidone.com/api/upload.php";

    static StringCache mL2Cache;

    public static void getNumberInfo(Context ctx, final String phoneNumber, final CallBack callBack) {

        final String PHONENUMBER_COMPLETE = phoneNumber.replaceAll("(?:-| )", "");
        final String A1OS_URL = A1OS_API + PHONENUMBER_COMPLETE;

        mL2Cache = new L2Cache(ctx, 10 * 1024 * 1024);

        if (mL2Cache.get(PHONENUMBER_COMPLETE) != null) {
            callBack.execute(mL2Cache.get(PHONENUMBER_COMPLETE));
            return;
        }

        RequestQueue mQueue = Volley.newRequestQueue(ctx);

        APIRequest stringRequest = new APIRequest(A1OS_URL,
           new Response.Listener<String>() {
              @Override
              public void onResponse(String response) {
                  callBack.execute(response);
            	  mL2Cache.put(PHONENUMBER_COMPLETE, response);
              }
        }, new Response.ErrorListener() {
              @Override
              public void onErrorResponse(VolleyError error) {
                  if (mL2Cache.get(PHONENUMBER_COMPLETE) != null) {
                      callBack.execute(mL2Cache.get(PHONENUMBER_COMPLETE));
                  }
              }
        });
        mQueue.add(stringRequest);
    }

    public static void putNumberInfo(Context ctx, final String phoneNumber, final String tag) {

        final String PHONENUMBER_COMPLETE = phoneNumber.replaceAll("(?:-| )", "");

        RequestQueue mQueue = Volley.newRequestQueue(ctx);

        APIRequest PostRequest = new APIRequest(Method.POST, UPLOAD_API,
           new Response.Listener<String>() {
              @Override
              public void onResponse(String response) {
                  Log.e("TAG", response);
              }
        }, new Response.ErrorListener() {
              @Override
              public void onErrorResponse(VolleyError error) {
                  Log.e("TAG", error.getMessage(), error);
              }
        }) {
              @Override
              protected HashMap<String, String> getParams()throws AuthFailureError {
                  HashMap<String, String> hashMap = new HashMap<String, String>();
                  hashMap.put("phone", PHONENUMBER_COMPLETE);
                  hashMap.put("tag", tag);
                  return hashMap;
              }
        };
        mQueue.add(PostRequest);
    }

    public interface CallBack {
        void execute(String response);
    }
}
