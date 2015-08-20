package org.zywx.wbpalmstar.plugin.uexmultiHttp;


import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import android.util.Log;

public class HX509TrustManager implements X509TrustManager {

	private X509TrustManager mTrustManager; 
	
	public HX509TrustManager(KeyStore ksP12) throws Exception{

		TrustManagerFactory tfactory = TrustManagerFactory.getInstance(Http.algorithm);  
		tfactory.init(ksP12);
        TrustManager[] trustMgrs = tfactory.getTrustManagers();  
        if (trustMgrs.length == 0) {  
            throw new NoSuchAlgorithmException("no trust manager found");  
        }  
        mTrustManager = (X509TrustManager)trustMgrs[0]; 
        
		Log.d("TrustManager", "HX509TrustManager");
		X509Certificate[] certs = mTrustManager.getAcceptedIssuers();
		for (X509Certificate cert : certs) {
			String certStr = "S:" + cert.getSubjectDN().getName() + "\nI:"
					+ cert.getIssuerDN().getName();
			Log.d("TrustManager", certStr);
		}

	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		if (Http.isCheckTrustCert()) {
			mTrustManager.checkClientTrusted(chain, authType);
		}
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType){
		if (Http.isCheckTrustCert()) {
			try {
				if ((chain != null) && (chain.length == 1)) {
					chain[0].checkValidity();
				} else {
					mTrustManager.checkServerTrusted(chain, authType);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		X509Certificate[] certs = mTrustManager.getAcceptedIssuers();
		Log.i("TrustManager", "getAcceptedIssuers");
		for (X509Certificate cert : certs) {
			String certStr = "S:" + cert.getSubjectDN().getName() + "\nI:"
					+ cert.getIssuerDN().getName();
			Log.i("TrustManager", certStr);
		}
		return certs;
	}
	
}
