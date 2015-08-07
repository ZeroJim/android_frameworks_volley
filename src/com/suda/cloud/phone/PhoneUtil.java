/*
 * Copyright (C) 2015 The SudaMod Project  
 * Copyright (C) 2015 The AmmoniaOS Project  
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.suda.cloud.phone;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;

import android.database.Cursor;

import android.net.Uri;
import android.suda.location.PhoneLocation;
import android.text.TextUtils;
import com.suda.cloud.request.APIRequest;

import com.android.volley.AuthFailureError;
import com.android.volley.Request.Method;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import android.os.SystemProperties;

import android.net.wifi.WifiManager;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;


public final class PhoneUtil {
    static StringBuilder MARK_API = new StringBuilder();
    static String AUTHORITY = "content://com.suda.provider.PhoneLocation/phonelocation";
    static ContentResolver cr;
    static Context ct;
    static Uri uri;
    static RequestQueue mQueue;
    static List<String> queue;
    static Map<String,PhoneLocationBean> tmpPhoneMap;

    static{
        System.loadLibrary("markapi");
    }

    static native String getMarkApi();

    public PhoneUtil(Context ct) {
        this.ct = ct.getApplicationContext();
        cr = ct.getContentResolver();
        uri = Uri.parse(AUTHORITY);
        mQueue = Volley.newRequestQueue(ct);
        queue = new ArrayList<>();
        tmpPhoneMap = new HashMap<String, PhoneLocationBean>();
        initData();
    }

    public static String getLocalNumberInfo(final String phoneNumber) {
        final String PHONENUMBER_COMPLETE = phoneNumber.replaceAll("(?:-| )", "");
        MARK_API.setLength(0);
        MARK_API.append(getMarkApi())
                                        .append(PHONENUMBER_COMPLETE)
                                        .append("&type=json&callback=show")
                                        .toString();

        //第一步查内存
        if (tmpPhoneMap.get(PHONENUMBER_COMPLETE) != null) {
            if(isNeedToUpdate(PHONENUMBER_COMPLETE)) {
                update(PHONENUMBER_COMPLETE,MARK_API.toString());
            }
            return tmpPhoneMap.get(PHONENUMBER_COMPLETE).getLocation();
        }

        //第二步查本地，防止一个应用插入本地后，另一个应用再次插入
        if (getLocation(PHONENUMBER_COMPLETE)){
            return tmpPhoneMap.get(PHONENUMBER_COMPLETE).getLocation();
        }

        //防止多次查询
        if(queue.contains(PHONENUMBER_COMPLETE)){
            return PhoneLocation.getCityFromPhone(PHONENUMBER_COMPLETE);
        }

        //第三步，查询网络，缓存本地和内存
        queue.add(PHONENUMBER_COMPLETE);
        APIRequest stringRequest = new APIRequest(MARK_API.toString(),
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        //callBack.execute(response);
                        if ("e".equals(getMark(response))) {
                            queue.remove(PHONENUMBER_COMPLETE);
                            return;
                        } else if (!TextUtils.isEmpty(getMark(response))) {
                            insertDb(PHONENUMBER_COMPLETE, getMark(response));
                        } else if (!TextUtils.isEmpty(
                                    PhoneLocation.getCityFromPhone(
                                        PHONENUMBER_COMPLETE))) {
                            insertDb(PHONENUMBER_COMPLETE, PhoneLocation.getCityFromPhone(
                                    PHONENUMBER_COMPLETE));
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        queue.remove(PHONENUMBER_COMPLETE);
                    }
                });

        mQueue.add(stringRequest);
        return PhoneLocation.getCityFromPhone(PHONENUMBER_COMPLETE);
    }

    public static void getNumberInfoOnline(final String phoneNumber,
        final CallBack callBack) {
        final String PHONENUMBER_COMPLETE = phoneNumber.replaceAll("(?:-| )", "");
        MARK_API.setLength(0);
        MARK_API.append(getMarkApi())
                                        .append(PHONENUMBER_COMPLETE)
                                        .append("&type=json&callback=show")
                                        .toString();

        //第一步查内存
        if (tmpPhoneMap.get(PHONENUMBER_COMPLETE) != null) {
            callBack.execute(tmpPhoneMap.get(PHONENUMBER_COMPLETE).getLocation());
            if(isNeedToUpdate(PHONENUMBER_COMPLETE)) {
                update(PHONENUMBER_COMPLETE,MARK_API.toString());
            }
            return;
        }

        //第二步查本地，防止一个应用插入本地后，另一个应用再次插入
        if (getLocation(PHONENUMBER_COMPLETE, callBack)){
            return;
        }

        //wifi状态才查询网络
        if(!isWifi()) {
            if (!TextUtils.isEmpty(PhoneLocation.getCityFromPhone(
                                        PHONENUMBER_COMPLETE))) {
                callBack.execute(PhoneLocation.getCityFromPhone(phoneNumber));
            } else {
                callBack.execute("");
            }
            return;
        }

        //防止多次查询
        if (queue.contains(PHONENUMBER_COMPLETE)){
            if (!TextUtils.isEmpty(PhoneLocation.getCityFromPhone(
                                        PHONENUMBER_COMPLETE))) {
                callBack.execute(PhoneLocation.getCityFromPhone(phoneNumber));
            } else {
                callBack.execute("");
            }
            return;
        }

        //第三步，查询网络，缓存本地和内存
        queue.add(PHONENUMBER_COMPLETE);
        APIRequest stringRequest = new APIRequest(MARK_API.toString(),
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        if ("e".equals(getMark(response))) {
                            callBack.execute("");
                            queue.remove(PHONENUMBER_COMPLETE);
                            return;
                        } else if (!TextUtils.isEmpty(getMark(response))) {
                            insertDb(PHONENUMBER_COMPLETE, getMark(response));
                            callBack.execute(getMark(response));
                            return;
                        } else if (!TextUtils.isEmpty(
                                    PhoneLocation.getCityFromPhone(
                                        PHONENUMBER_COMPLETE))) {
                            insertDb(PHONENUMBER_COMPLETE, PhoneLocation.getCityFromPhone(
                                    PHONENUMBER_COMPLETE));
                            callBack.execute(PhoneLocation.getCityFromPhone(
                                    phoneNumber));
                            return;
                        } else {
                            callBack.execute("");
                            return;
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        queue.remove(PHONENUMBER_COMPLETE);
                        if (tmpPhoneMap.get(PHONENUMBER_COMPLETE)!=null) {
                            callBack.execute(tmpPhoneMap.get(PHONENUMBER_COMPLETE).getLocation());
                            return;
                        } else {
                            callBack.execute(PhoneLocation.getCityFromPhone(
                                    PHONENUMBER_COMPLETE));
                            return;
                        }
                    }
                });

        mQueue.add(stringRequest);
    }

    public static boolean isNeedToUpdate(String phoneNumber) {
        return tmpPhoneMap.get(phoneNumber).getLast_update() + 86400000 * 3 < System.currentTimeMillis();
    }

    public static void update(final String phoneNumber, String url) {
        APIRequest stringRequest = new APIRequest(url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        //callBack.execute(response);
                        if ("e".equals(getMark(response))) {
                            return;
                        } else if (!TextUtils.isEmpty(getMark(response))) {
                            updateDb(phoneNumber, getMark(response));
                        } else if (!TextUtils.isEmpty(
                                    PhoneLocation.getCityFromPhone(
                                        phoneNumber))) {
                            updateDb(phoneNumber, PhoneLocation.getCityFromPhone(
                                    phoneNumber));
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {

                    }
                });

        mQueue.add(stringRequest);
    }

    public static boolean getLocation(String phoneNumber, CallBack callBack) {
        Cursor c = cr.query(uri, null, "phone_number=?",
                new String[] { phoneNumber }, null);
        boolean have = c.moveToFirst();
        try {
             tmpPhoneMap.put(c.getString(1), new PhoneLocationBean(c.getString(1), c.getString(2), c.getLong(3)));
             callBack.execute(c.getString(2));
        } catch (Exception e) {
 
        } finally {
            c.close();
        }
        return have;
    }

    public static boolean getLocation(String phoneNumber) {
        Cursor c = cr.query(uri, null, "phone_number=?",
                new String[] { phoneNumber }, null);
        boolean have = c.moveToFirst();
        try {
             tmpPhoneMap.put(c.getString(1), new PhoneLocationBean(c.getString(1), c.getString(2), c.getLong(3)));
        } catch (Exception e) {
        } finally {
            c.close();
        }
        return have;
    }

    public static String getMark(String response) {
        try {
            JSONObject jo = new JSONObject(response.subSequence(5,
                        response.length() - 1).toString());
            if (jo.getString("NumInfo").equals("该号码暂无标记")) {
                return "";
            } else {
                String[] result = jo.getString("NumInfo").split("：");
                return result[1];
            }
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return "e";
        }

    }

    public static void initData() {
        Cursor c = cr.query(uri, null, null,
                null, null);
        try {
         while(c.moveToNext()) {
             tmpPhoneMap.put(c.getString(1), new PhoneLocationBean(c.getString(1), c.getString(2), c.getLong(3)));
         }
        } catch (Exception e) {
 
        } finally {
            c.close();
        }
    }

    public static void insertDb (String phoneNumber, String location) {
        Long last_time = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("phone_number", phoneNumber);
        values.put("phone_location", location);
        values.put("last_update", last_time);
        cr.insert(uri, values);
        tmpPhoneMap.put(phoneNumber, new PhoneLocationBean(phoneNumber, location, last_time));
    }

    public static void updateDb (String phoneNumber, String location) {
        Long last_time = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("phone_number", phoneNumber);
        values.put("phone_location", location);
        values.put("last_update", last_time);
        cr.update(uri, values, "phone_number=?", new String[] { phoneNumber });
        tmpPhoneMap.put(phoneNumber, new PhoneLocationBean(phoneNumber, location, last_time));
    }

    public static boolean isWifi() {
        WifiManager mWifiManager = (WifiManager) ct
                .getSystemService(Context.WIFI_SERVICE);
        return mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED;
    }

    public interface CallBack {
        void execute(String response);
    }
}

