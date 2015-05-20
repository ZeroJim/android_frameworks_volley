package com.a1os.cloud.phone;

import com.a1os.cloud.request.APIRequest;
import com.a1os.cloud.util.Cache;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;

import android.content.Context;
import android.util.Log;

public final class PhoneUtil {

    static String A1OS_API = "http://www.aoidone.com/api/cloud_data.php?number=";

    static Cache<String, String> mCache = new Cache<String, String>(10 * 1024 *104);

    public static void getNumberInfo(Context ctx, final String phoneNumber, final CallBack callBack) {

        final String PHONENUMBER_COMPLETE = phoneNumber.replaceAll("(?:-| )", "");
        final String A1OS_URL = A1OS_API + PHONENUMBER_COMPLETE;

        if (mCache.get(PHONENUMBER_COMPLETE) != null) {
            callBack.execute(mCache.get(PHONENUMBER_COMPLETE));
            return;
        }

        RequestQueue mQueue = Volley.newRequestQueue(ctx);

        APIRequest stringRequest = new APIRequest(A1OS_URL,
           new Response.Listener<String>() {
              @Override
              public void onResponse(String response) {
                  callBack.execute(response);
            	  mCache.put(PHONENUMBER_COMPLETE, response);
              }
        }, new Response.ErrorListener() {
              @Override
              public void onErrorResponse(VolleyError error) {
                  Log.e("TAG", error.getMessage(), error);
              }
        });
        mQueue.add(stringRequest);
    }

    public interface CallBack {
        void execute(String response);
    }
}
