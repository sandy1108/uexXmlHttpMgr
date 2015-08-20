package org.zywx.wbpalmstar.plugin.uexmultiHttp;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.cookie.SM;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONObject;
import org.zywx.wbpalmstar.base.BUtility;
import org.zywx.wbpalmstar.widgetone.dataservice.WWidgetData;

import android.os.Build;
import android.os.Process;
import android.util.Log;

public class ENetHttpPost extends Thread implements HttpTask,
		HttpClientListener {

	public static final String UA = "Mozilla/5.0 (Linux; U; Mobile; "
			+ "Android " + Build.VERSION.RELEASE + ";" + Build.MODEL
			+ " Build/FRF91 )";

	private int mTimeOut;
	private boolean mRunning;
	private boolean mCancelled;
	private String mUrl;
	private int mXmlHttpID;
	private EUExXmlHttpMgr mXmlHttpMgr;
	private HttpURLConnection mConnection;
	private URL mClient;
	private String mCertPassword;
	private String mCertPath;
	private InputStream mInStream;
	private boolean mHasLocalCert;
	private String mBody;
	private String mRedirects;
	private File mOnlyFile;
	private ArrayList<HPair> mMultiData;
	private boolean mFromRedirects;
	private Hashtable<String, String> mHttpHead;

	static final int BODY_TYPE_TEXT = 0;
	static final int BODY_TYPE_FILE = 1;

	private int responseCode = -1;
	private String responseMessage = "";
	private String responseError = "";
	private Map<String, List<String>> headers;
	private InputStream mErrorInStream;

	public ENetHttpPost(String inXmlHttpID, String url, int timeout,
			EUExXmlHttpMgr xmlHttpMgr) {
		setName("SoTowerMobile-HttpPost");
		mUrl = url;
		mTimeOut = timeout;
		mXmlHttpMgr = xmlHttpMgr;
		mXmlHttpID = Integer.parseInt(inXmlHttpID);
		initNecessaryHeader();
	}

	@Override
	public void setData(int inDataType, String inKey, String inValue) {
		if (null == inKey || inKey.length() == 0) {
			inKey = "";
		}
		if (null == inValue || inValue.length() == 0) {
			inValue = "";
		}
		if (null == mMultiData) {
			mMultiData = new ArrayList<HPair>();
		}
		try {
			if (BODY_TYPE_FILE == inDataType) {
				String wp = mXmlHttpMgr.getWidgetPath();
				int wtp = mXmlHttpMgr.getWidgetType();
				inValue = BUtility.makeRealPath(inValue, wp, wtp);
			}
			if (checkData(inKey, inValue)) {
				return;
			}
			HPair en = new HPair(inDataType, inKey, inValue);
			mMultiData.add(en);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setCertificate(String cPassWord, String cPath) {
		mHasLocalCert = true;
		mCertPassword = cPassWord;
		mCertPath = cPath;
	}

	private boolean checkData(String key, String value) {
		for (HPair pair : mMultiData) {
			if (key.equals(pair.key)) {
				pair.value = value;
				return true;
			}
		}
		return false;
	}

	@Override
	public void send() {
		if (mRunning || mCancelled) {
			return;
		}
		mRunning = true;
		start();
	}

	@Override
	public void run() {
		if (mCancelled) {
			return;
		}
		Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
		doInBackground();
	}

	protected void doInBackground() {
		if (mCancelled) {
			return;
		}
		String result = "";
		boolean isSuccess = false;
		final String curUrl;
		if (null == mUrl) {
			return;
		}
		if (mFromRedirects && null != mRedirects) {
			curUrl = mRedirects;
		} else {
			curUrl = mUrl;
		}
		try {
			mClient = new URL(curUrl);
			if (curUrl.startsWith("https")) {
				if (mHasLocalCert) {
					mConnection = Http.getHttpsURLConnectionWithCert(mClient,
							mCertPassword, mCertPath, mXmlHttpMgr.getContext());
				} else {
					mConnection = Http.getHttpsURLConnection(mClient);
				}
			} else {
				mConnection = (HttpURLConnection) mClient.openConnection();
			}
			mConnection.setRequestMethod("POST");
			mConnection.setRequestProperty("Accept-Encoding", "gzip, deflate");
			String cookie = null;
			cookie = mXmlHttpMgr.getCookie(curUrl);
			if (null != cookie) {
				mConnection.setRequestProperty(SM.COOKIE, cookie);
			}

			HttpEntity multiEn = null;
			if (null != mOnlyFile) {
				multiEn = createInputStemEntity();
			} else if (null != mMultiData) {
				if (!containOctet()) {
					multiEn = createFormEntity();
				} else {
					multiEn = createMultiEntity();
					if (null == multiEn) {
						// mXmlHttpMgr.callBack(mXmlHttpID,
						// "error:file not found!");
					}
				}
			} else if (null != mBody) {
				multiEn = createStringEntity();
			}

			addHeaders();
			mConnection.setUseCaches(false);
			mConnection.setReadTimeout(mTimeOut);
			mConnection.setConnectTimeout(mTimeOut);
			mConnection.setInstanceFollowRedirects(false);
			mXmlHttpMgr.printHeader(-1, mXmlHttpID, curUrl, true,
					mConnection.getRequestProperties());
			mConnection.connect();
			if (null != multiEn) {
				InputStream in = multiEn.getContent();
				OutputStream out = mConnection.getOutputStream();
				byte[] buffer = new byte[8 * 1024];
				int len = 0;
				while ((len = in.read(buffer)) >= 0) {
					out.write(buffer, 0, len);
					out.flush();
				}
				// in.close();
				// out.close();
			}
			responseCode = mConnection.getResponseCode();
			responseMessage = mConnection.getResponseMessage();
			headers = mConnection.getHeaderFields();
			mXmlHttpMgr.printHeader(responseCode, mXmlHttpID, curUrl, false,
					headers);
			switch (responseCode) {
			case HttpStatus.SC_OK:
				byte[] bResult = toByteArray(mConnection);
				result = new String(bResult, HTTP.UTF_8);
				break;
			case HttpStatus.SC_MOVED_PERMANENTLY:
			case HttpStatus.SC_MOVED_TEMPORARILY:
			case HttpStatus.SC_TEMPORARY_REDIRECT:
				List<String> urls = headers.get("Location");
				if (null != urls && urls.size() > 0) {
					mRedirects = urls.get(0);
					Log.i("xmlHttpMgr", "redirect url " + mRedirects);
					mFromRedirects = true;
					handleCookie(curUrl, headers);
					doInBackground();
					return;
				}
				break;
			default:
				byte[] bError = toErrorByteArray(mConnection);
				responseError = new String(bError, HTTP.UTF_8);
				break;
			}
			handleCookie(curUrl, headers);
			isSuccess = true;
		} catch (Exception e) {
			isSuccess = false;
			if (e instanceof SocketTimeoutException) {
				result = EUExXmlHttpMgr.CONNECT_FAIL_TIMEDOUT;
			} else {
				result = EUExXmlHttpMgr.CONNECT_FAIL_CONNECTION_FAILURE;
			}
			e.printStackTrace();
		} finally {
			try {
				if (null != mInStream) {
					mInStream.close();
				}
				if (null != mErrorInStream) {
					mErrorInStream.close();
				}
				if (null != mConnection) {
					mConnection.disconnect();
				}
			} catch (Exception e) {
			}
		}
		mXmlHttpMgr.onFinish(mXmlHttpID);
		if (mCancelled) {
			return;
		}
		mXmlHttpMgr.printResult(mXmlHttpID, curUrl, result);
		if (isSuccess) {
			JSONObject jsonObject = new JSONObject();
			try {
				if (headers != null && !headers.isEmpty()) {
					JSONObject jsonHeaders = XmlHttpUtil
							.getJSONHeaders(headers);
					jsonObject.put(EUExXmlHttpMgr.PARAMS_JSON_KEY_HEADERS,
							jsonHeaders);
				}
				jsonObject.put(EUExXmlHttpMgr.PARAMS_JSON_KEY_STATUSCODE,
						responseCode);
				jsonObject.put(EUExXmlHttpMgr.PARAMS_JSON_KEY_STATUSMESSAGE,
						responseMessage);
				jsonObject.put(EUExXmlHttpMgr.PARAMS_JSON_KEY_RESPONSEERROR,
						responseError);
			} catch (Exception e) {
			}
			mXmlHttpMgr.callBack(mXmlHttpID, result, responseCode,
					jsonObject.toString());
		} else {
			mXmlHttpMgr.errorCallBack(mXmlHttpID, result, responseCode, "");
		}
		return;
	}

	private byte[] toByteArray(HttpURLConnection conn) throws Exception {
		if (null == conn) {
			return new byte[] {};
		}
		mInStream = conn.getInputStream();
		if (mInStream == null) {
			return new byte[] {};
		}
		long len = conn.getContentLength();
		if (len > Integer.MAX_VALUE) {
			throw new Exception(
					"HTTP entity too large to be buffered in memory");
		}
		String contentEncoding = conn.getContentEncoding();
		boolean gzip = false;
		if (null != contentEncoding) {
			if ("gzip".equalsIgnoreCase(contentEncoding)) {
				mInStream = new GZIPInputStream(mInStream, 2048);
				gzip = true;
			}
		}
		ByteArrayBuffer buffer = XmlHttpUtil.getBuffer(gzip, mInStream);
		return buffer.toByteArray();
	}

	private byte[] toErrorByteArray(HttpURLConnection conn) throws Exception {
		if (null == conn) {
			return new byte[] {};
		}
		mErrorInStream = conn.getErrorStream();
		if (mErrorInStream == null) {
			return new byte[] {};
		}
		String contentEncoding = conn.getContentEncoding();
		boolean gzip = false;
		if (null != contentEncoding) {
			if ("gzip".equalsIgnoreCase(contentEncoding)) {
				mErrorInStream = new GZIPInputStream(mErrorInStream, 2048);
				gzip = true;
			}
		}
		ByteArrayBuffer buffer = XmlHttpUtil.getBuffer(gzip, mErrorInStream);
		return buffer.toByteArray();
	}

	private void handleCookie(String url, Map<String, List<String>> headers) {
		if (null == headers) {
			return;
		}
		List<String> setCookies = headers.get(SM.SET_COOKIE);
		if (null != setCookies) {
			for (String v : setCookies) {
				mXmlHttpMgr.setCookie(url, v);
			}
		} else {
			setCookies = headers.get("set-cookie");
			if (null != setCookies) {
				for (String v : setCookies) {
					mXmlHttpMgr.setCookie(url, v);
				}
			}
		}
		List<String> Cookie = headers.get(SM.COOKIE);
		if (null != Cookie) {
			for (String v : Cookie) {
				mXmlHttpMgr.setCookie(url, v);
			}
		} else {
			Cookie = headers.get("cookie");
			if (null != Cookie) {
				for (String v : Cookie) {
					mXmlHttpMgr.setCookie(url, v);
				}
			}
		}
		List<String> Cookie2 = headers.get(SM.COOKIE2);
		if (null != Cookie2) {
			for (String v : Cookie2) {
				mXmlHttpMgr.setCookie(url, v);
			}
		} else {
			Cookie2 = headers.get("cookie2");
			if (null != Cookie2) {
				for (String v : Cookie2) {
					mXmlHttpMgr.setCookie(url, v);
				}
			}
		}
	}

	private boolean containOctet() {
		for (HPair pair : mMultiData) {
			if (pair.type == BODY_TYPE_FILE) {
				return true;
			}
		}
		return false;
	}

	private HttpEntity createFormEntity() {
		HttpEntity entry = null;
		try {
			List<BasicNameValuePair> postData = new ArrayList<BasicNameValuePair>();
			for (HPair pair : mMultiData) {
				postData.add(new BasicNameValuePair(pair.key, pair.value));
			}
			entry = new UrlEncodedFormEntity(postData, HTTP.UTF_8);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return entry;
	}

	private HttpEntity createInputStemEntity() {
		HttpEntity entry = null;
		try {
			FileInputStream instream = new FileInputStream(mOnlyFile);
			entry = new InputStreamEntity(instream, mOnlyFile.length());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return entry;
	}

	private HttpEntity createMultiEntity() {
		EMultiEntity multiEn = null;
		try {
			multiEn = new EMultiEntity();
			multiEn.addHttpClientListener(this);
			for (HPair pair : mMultiData) {
				Body bd = null;
				if (BODY_TYPE_FILE == pair.type) {
					bd = new BodyFile(pair.key, new File(pair.value));
				} else if (BODY_TYPE_TEXT == pair.type) {
					bd = new BodyString(pair.key, pair.value);
				}
				multiEn.addBody(bd);
			}
		} catch (Exception e) {

			return null;
		}
		return multiEn;
	}

	private HttpEntity createStringEntity() {
		HttpEntity entry = null;
		try {
			entry = new StringEntity(mBody, HTTP.UTF_8);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return entry;
	}

	@Override
	public void onProgressChanged(float newProgress) {
		int progress = (int) newProgress;
		mXmlHttpMgr.progressCallBack(mXmlHttpID, progress);
	}

	@Override
	public void cancel() {
		mCancelled = true;
		try {
			if (null != mInStream) {
				mInStream.close();
			}
			if (null != mErrorInStream) {
				mErrorInStream.close();
			}
			interrupt();
		} catch (Exception e) {
			e.printStackTrace();
		}
		mTimeOut = 0;
		mUrl = null;
		mRunning = false;
		mConnection = null;
		mClient = null;
	}

	@Override
	public void setBody(String body) {
		mBody = body;
	}

	@Override
	public void setInputStream(File file) {
		mOnlyFile = file;
	}

	@Override
	public void setHeaders(String headJson) {
		try {
			JSONObject json = new JSONObject(headJson);
			Iterator<?> keys = json.keys();
			while (keys.hasNext()) {
				String key = (String) keys.next();
				String value = json.getString(key);
				mHttpHead.put(key, value);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void addHeaders() {
		if (null != mConnection) {
			Set<Entry<String, String>> entrys = mHttpHead.entrySet();
			for (Map.Entry<String, String> entry : entrys) {
				mConnection
						.setRequestProperty(entry.getKey(), entry.getValue());
			}
		}
	}

	private void initNecessaryHeader() {
		mHttpHead = new Hashtable<String, String>();
		mHttpHead.put("Accept", "*/*");
		mHttpHead.put("Charset", HTTP.UTF_8);
		mHttpHead.put("User-Agent", UA);
		mHttpHead.put("Connection", "Keep-Alive");
		mHttpHead.put("Accept-Encoding", "gzip, deflate");
	}

	@Override
	public void setAppVerifyHeader(WWidgetData curWData) {
		mHttpHead.put(
				XmlHttpUtil.KEY_APPVERIFY,
				XmlHttpUtil.getAppVerifyValue(curWData,
						System.currentTimeMillis()));
	}
}
