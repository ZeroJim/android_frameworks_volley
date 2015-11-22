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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.suda.location.PhoneLocation;
import android.text.TextUtils;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.suda.cloud.request.APIRequest;

public final class PhoneUtil {

	private final static String AUTHORITY = "content://com.suda.provider.PhoneLocation/phonelocation";
	private static ContentResolver mCr;
	private static Context mContext;
	private static Uri mUri;
	private static RequestQueue mQueue;
	private static List<String> mQueueList;
	private static Map<String, PhoneInfoBean> mMapCache;
	private static PhoneUtil mPu;

	public static synchronized PhoneUtil getPhoneUtil(Context ct) {
		if (mPu == null) {
			mPu = new PhoneUtil(ct);
		}
		return mPu;
	}

	private PhoneUtil(Context ct) {
		mContext = ct.getApplicationContext();
		mCr = mContext.getContentResolver();
		mUri = Uri.parse(AUTHORITY);
		mQueue = Volley.newRequestQueue(mContext);
		mQueueList = new ArrayList<>();
		mMapCache = new HashMap<String, PhoneInfoBean>();
		initData();
	}

	public static synchronized String getLocalNumberInfo(
			final String phoneNumber) {
		return getLocalNumberInfo(phoneNumber, true);
	}

	public static synchronized String getLocalNumberInfo(
			final String phoneNumber, boolean useMapCache) {
		final String PHONENUMBER_COMPLETE = phoneNumber.replaceAll("(?:-| )",
				"").replace("+86", "");

		// 第一步查内存
		if (mMapCache.get(PHONENUMBER_COMPLETE) != null && useMapCache) {
			if (isNeedToUpdate(PHONENUMBER_COMPLETE)) {
				insertOrUpdate(PHONENUMBER_COMPLETE,
						getRequestUrl(PHONENUMBER_COMPLETE), false);
			}
			return mMapCache.get(PHONENUMBER_COMPLETE).getPhoneMark();
		}

		// 第二步查本地，防止一个应用插入本地后，另一个应用再次插入
		if (getLocalData(PHONENUMBER_COMPLETE)) {
			return mMapCache.get(PHONENUMBER_COMPLETE).getPhoneMark();
		}

		// 防止多次查询
		if (mQueueList.contains(PHONENUMBER_COMPLETE)) {
			return PhoneLocation.getCityFromPhone(PHONENUMBER_COMPLETE);
		}

		// 第三步，查询网络，缓存本地和内存
		mQueueList.add(PHONENUMBER_COMPLETE);
		insertOrUpdate(PHONENUMBER_COMPLETE,
				getRequestUrl(PHONENUMBER_COMPLETE), true);
		return PhoneLocation.getCityFromPhone(PHONENUMBER_COMPLETE);
	}

	public static void getOnlineNumberInfo(final String phoneNumber,
			final CallBack callBack) {
		final String PHONENUMBER_COMPLETE = phoneNumber.replaceAll("(?:-| )",
				"").replace("+86", "");

		boolean useCloudMark = true;
		// boolean useCloudMark = Settings.System.getInt(
		// mCr, Settings.System.USE_CLOUD_MARK, 0) == 1;

		if (!useCloudMark) {
			callBack.execute(PhoneLocation
					.getCityFromPhone(PHONENUMBER_COMPLETE));
			return;
		}

		// 第一步查内存
		if (mMapCache.get(PHONENUMBER_COMPLETE) != null) {
			callBack.execute(mMapCache.get(PHONENUMBER_COMPLETE).getPhoneMark());
			return;
		}

		// 第二步查本地，防止一个应用插入本地后，另一个应用再次插入
		if (getLocalData(PHONENUMBER_COMPLETE)) {
			callBack.execute(mMapCache.get(PHONENUMBER_COMPLETE).getPhoneMark());
		}

		// wifi状态并且可以访问网络才查询网络
		if (!isWiFiActive()) {
			callBack.execute(PhoneLocation
					.getCityFromPhone(PHONENUMBER_COMPLETE));
			return;
		}

		// 防止多次查询
		if (mQueueList.contains(PHONENUMBER_COMPLETE)) {
			callBack.execute(PhoneLocation
					.getCityFromPhone(PHONENUMBER_COMPLETE));
			return;
		}

		// 第三步，查询网络，缓存本地和内存
		mQueueList.add(PHONENUMBER_COMPLETE);
		APIRequest stringRequest = new APIRequest(
				getRequestUrl(PHONENUMBER_COMPLETE),
				new Response.Listener<String>() {
					@Override
					public void onResponse(String response) {
						if ("e".equals(getMark(response))) {
							callBack.execute("");
							mQueueList.remove(PHONENUMBER_COMPLETE);
							return;
						} else {
							if (!TextUtils.isEmpty(getMark(response))) {
								callBack.execute(getMark(response));
								insertOrUpdateDb(PHONENUMBER_COMPLETE,
										getMark(response),
										getMarkType(getMark(response)), true);
								return;
							} else {
								callBack.execute(PhoneLocation
										.getCityFromPhone(phoneNumber));
								insertOrUpdateDb(
										PHONENUMBER_COMPLETE,
										PhoneLocation
												.getCityFromPhone(PHONENUMBER_COMPLETE),
										MARK_TYPE_NONE, true);
								return;
							}
						}
					}
				}, new Response.ErrorListener() {
					@Override
					public void onErrorResponse(VolleyError error) {
						callBack.execute(PhoneLocation
								.getCityFromPhone(PHONENUMBER_COMPLETE));
						mQueueList.remove(PHONENUMBER_COMPLETE);
						return;
					}
				});
		stringRequest.setRetryPolicy(new DefaultRetryPolicy(1500,
				DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
				DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
		mQueue.add(stringRequest);
	}

	public static void customMark(String phoneNumber, String mark, int markType) {
		final String PHONENUMBER_COMPLETE = phoneNumber.replaceAll("(?:-| )",
				"").replace("+86", "");
		boolean isMarkEmpty = TextUtils.isEmpty(mark);
		insertOrUpdateDb(
				PHONENUMBER_COMPLETE,
				isMarkEmpty ? PhoneLocation
						.getCityFromPhone(PHONENUMBER_COMPLETE) : mark,
				isMarkEmpty ? MARK_TYPE_CUSTOM_EMPTY : MARK_TYPE_CUSTOM,
				!getLocalData(PHONENUMBER_COMPLETE));

	}

	private static void insertOrUpdate(final String phoneNumber, String url,
			final boolean isInsert) {
		APIRequest stringRequest = new APIRequest(url,
				new Response.Listener<String>() {
					@Override
					public void onResponse(String response) {
						if ("e".equals(getMark(response))) {
							if (isInsert) {
								mQueueList.remove(phoneNumber);
							}
						} else {
							if (!TextUtils.isEmpty(getMark(response))) {
								insertOrUpdateDb(phoneNumber,
										getMark(response),
										getMarkType(getMark(response)),
										isInsert);
							} else {
								insertOrUpdateDb(phoneNumber,
										PhoneLocation
												.getCityFromPhone(phoneNumber),
										MARK_TYPE_NONE, isInsert);
							}
						}
					}
				}, new Response.ErrorListener() {
					@Override
					public void onErrorResponse(VolleyError error) {
						if (isInsert) {
							mQueueList.remove(phoneNumber);
						}
					}
				});

		mQueue.add(stringRequest);
	}

	private static boolean getLocalData(String phoneNumber) {
		Cursor c = null;
		boolean have = false;
		try {
			c = mCr.query(mUri, null, "phone_number=?",
					new String[] { phoneNumber }, null);
			have = c.moveToFirst();
			mMapCache.put(
					c.getString(1),
					new PhoneInfoBean(c.getString(1), c.getString(2), c
							.getLong(3), c.getInt(4)));
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (c != null) {
				c.close();
			}
		}
		return have;
	}

	private static String getMark(String response) {
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
			e.printStackTrace();
			return "e";
		}

	}

	private static void initData() {
		Cursor c = null;
		try {
			// 长时间未使用的数据初始化的时候不加入缓存 (>3天)
			c = mCr.query(mUri, null,
					"last_update > "
							+ (System.currentTimeMillis() - 86400000 * 3),
					null, null);
			while (c.moveToNext()) {
				mMapCache.put(c.getString(1), new PhoneInfoBean(c.getString(1),
						c.getString(2), c.getLong(3), c.getInt(4)));
			}
			Log.d("INIT:locationdata.size:", mMapCache.size() + "");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}

	private static void insertOrUpdateDb(String phoneNumber, String location,
			int markType, boolean isInsert) {
		Long last_time = System.currentTimeMillis();
		ContentValues values = new ContentValues();
		values.put("phone_number", phoneNumber);
		values.put("phone_location", location);
		values.put("last_update", last_time);
		values.put("mark_type", markType);

		if (isInsert) {
			mCr.insert(mUri, values);
		} else {
			mCr.update(mUri, values, "phone_number=?",
					new String[] { phoneNumber });
		}
		mMapCache.put(phoneNumber, new PhoneInfoBean(phoneNumber, location,
				last_time, markType));

	}

	public static boolean isWiFiActive() {
		ConnectivityManager connectivity = (ConnectivityManager) mContext
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivity != null) {
			NetworkInfo[] info = connectivity.getAllNetworkInfo();
			if (info != null) {
				for (int i = 0; i < info.length; i++) {
					if (info[i].getType() == ConnectivityManager.TYPE_WIFI
							&& info[i].isConnected()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean isNeedToUpdate(String phoneNumber) {
		return ((mMapCache.get(phoneNumber).getLastUpdateAt() + 86400000 * 3 < System
				.currentTimeMillis()) && (mMapCache.get(phoneNumber)
				.getMarkType() != MARK_TYPE_CUSTOM))
				|| (mMapCache.get(phoneNumber).getMarkType() == MARK_TYPE_CUSTOM_EMPTY);
	}

	private static int getMarkType(String mark) {
		if ("骚扰电话".equals(mark))
			return MARK_TYPE_CRANK_CALL;
		if ("诈骗电话".equals(mark) || "诈骗".equals(mark))
			return MARK_TYPE_FRAUD_CALL;
		if ("广告推销".equals(mark))
			return MARK_TYPE_ADV_CALL;
		return MARK_TYPE_COMMON;
	}

	private static String getRequestUrl(String number) {
		StringBuilder builder = new StringBuilder();
		builder.append(API).append(number).append("&type=json&callback=show");
		return builder.toString();
	}

	private final static int MARK_TYPE_CUSTOM_EMPTY = -1; // 自定义为空
	private final static int MARK_TYPE_NONE = 0; // 无标记
	private final static int MARK_TYPE_CUSTOM = 1; // 自定义
	private final static int MARK_TYPE_COMMON = 2; // 普通，未分类
	private final static int MARK_TYPE_CRANK_CALL = 3; // 骚扰电话
	private final static int MARK_TYPE_FRAUD_CALL = 4; // 诈骗电话
	private final static int MARK_TYPE_ADV_CALL = 5; // 广告推销

	private final static String API = "http://data.haoma.sogou.com/vrapi/query_number.php?number=";

	public interface CallBack {
		void execute(String response);
	}
}
