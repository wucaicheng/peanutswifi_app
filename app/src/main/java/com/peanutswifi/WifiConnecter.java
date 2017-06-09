package com.peanutswifi;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.net.wifi.WifiInfo;

import java.util.List;

/**
 * Created by Jac on 2015/4/7.
 */
public class WifiConnecter{

    // Combo scans can take 5-6s to complete
    private static final int WIFI_RESCAN_INTERVAL_MS = 5 * 1000;

    private static final String TAG = WifiConnecter.class.getSimpleName();
    public static final int MAX_TRY_COUNT = 3;

    private Context mContext;
    private WifiManager mWifiManager;

    private final IntentFilter mFilter;
    private final BroadcastReceiver mReceiver;
    private final ScannerHandler mScanner;
    private ActionListener mListener;
    private String mSsid;
    private String mPasswd;
    private String mEncryp;

    private boolean isRegistered;
    private boolean isActiveScan;

    public WifiConnecter(Context context){  //实现广播接收器，接收并处理wifiManager广播
        this.mContext = context;
        mWifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);

        mFilter = new IntentFilter();
        mFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);  //扫描是异步处理，扫描完成后系统发送广播，intent.action是这个
        mFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleEvent(context, intent);
            }
        };

        context.registerReceiver(mReceiver, mFilter);
        isRegistered = true;
        mScanner = new ScannerHandler();

    }

    public void connect(String ssid, String encryption, String password, ActionListener listener){
        this.mListener = listener;
        this.mSsid = ssid;
        this.mPasswd = password;
        this.mEncryp = encryption;

        if(listener != null){
            listener.onStarted(ssid);
        }

        if (!mWifiManager.isWifiEnabled()){
            mWifiManager.setWifiEnabled(true);
        }

        if(!Wifi.connectToNewNetwork(mWifiManager, mSsid, mEncryp, mPasswd,true)){
            if(mListener != null){
                mListener.onFailure("Connect to AP is failed!");
                mListener.onFinished(false);
            }
            onPause();
        }
    }

    public void scanSpecifiedSSID(String ssid, ActionListener listener){
        this.mListener = listener;
        this.mSsid = ssid;
        if(listener != null) {
            listener.onScan(ssid);
        }
        mScanner.forceScan();
    }

    public void clearConnect(ActionListener listener){
        //   clear config and shutdown wifi
        if (listener != null){
            this.mListener = listener;

            onResume();

            listener.onClearConfig();
            final List<WifiConfiguration> configurations = mWifiManager.getConfiguredNetworks();
            if (configurations != null) {
                for (final WifiConfiguration configTmp : configurations) {
                    mWifiManager.removeNetwork(configTmp.networkId);
                }
                mWifiManager.saveConfiguration();
            }
            listener.onShutDownWifi();
            if (mWifiManager.isWifiEnabled()){
                mWifiManager.setWifiEnabled(false);
            }

        }
    }

    public void clearConnect2(){
//   clear without toast text
        onResume();

        final List<WifiConfiguration> configurations = mWifiManager.getConfiguredNetworks();//wifi保存的配置
        if (configurations != null) {
            for (final WifiConfiguration configTmp : configurations) {
                if(!mWifiManager.removeNetwork(configTmp.networkId)) { //android6.0之前remove可以，6.0之后只能删除自己添加的网络
                    mWifiManager.disableNetwork(configTmp.networkId);
                }
            }
            mWifiManager.saveConfiguration();//保存网络
        }
        /*
        WifiInfo info = mWifiManager.getConnectionInfo();
        String curSsid = info.getSSID();
        if (curSsid != "0x") {
            mWifiManager.setWifiEnabled(false);
        }
        */
    }

    public void clearConnect3(ActionListener listener) {
//   clear config and disconnect with ap
        if (listener != null) {
            this.mListener = listener;

            final List<WifiConfiguration> configurations = mWifiManager.getConfiguredNetworks();
            if (configurations != null) {
                for (final WifiConfiguration configTmp : configurations) {
                    if(!mWifiManager.removeNetwork(configTmp.networkId)) {  //android6.0变更，只能remove自己添加的配置，这里做了更改
                        mWifiManager.disableNetwork(configTmp.networkId);
                    }
                }
                mWifiManager.saveConfiguration();
            }
            listener.onClearConfig();

            onResume();
            /*
            WifiInfo info = mWifiManager.getConnectionInfo();
            String curSsid = info.getSSID();
            if (curSsid != "0x") {
                mWifiManager.disconnect();
            }
            listener.onShutDownWifi();
            */

        }
    }

    public void shutDownWifi(){
//   shutdown wifi
        onResume();

        while (mWifiManager.isWifiEnabled()){
            mWifiManager.setWifiEnabled(false);

        }

    }

    public void turnOnWifi(){
//        turn on wifi
        onResume();

        while (!mWifiManager.isWifiEnabled()){
            mWifiManager.setWifiEnabled(true);
        }
    }

    private void handleEvent(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action) && isActiveScan){
            boolean flag = false;
            List<ScanResult> results = mWifiManager.getScanResults();//得到扫描结果
            for (ScanResult result: results){
                boolean ssidEquals = this.mSsid.equals(result.SSID);  //扫描结果中是否有app上配置的ssid
                if (ssidEquals){   //如果有，结束扫描
                    flag = true;
                    mScanner.pause();
                    break;
                }
            }
            if(mListener != null && flag == false) {
                mListener.onFailure("Cannot find specified SSID!");  //如果没有匹配，给个吐司，继续扫描
            }
            if (mListener != null && flag == true) {
                mListener.onScanSuccess(this.mSsid);
                mListener.onFinished(true);  //关闭扫描对话框
            }
        /*
        String NETWORK_STATE_CHANGED_ACTION
        Broadcast intent action indicating that the state of Wi-Fi connectivity has changed.
        One extra provides the new state in the form of a NetworkInfo object.
        If the new state is CONNECTED, additional extras may provide the BSSID and WifiInfo of the access point. as a String.
        */
        }else if(WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
            NetworkInfo mInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO); //得到Parcelable类型的数据；可见android wifi管理类中一定调用了intent.putExtra(WifiManager.EXTRA_NETWORK_INFO,Parcelable)
            if (mInfo.isConnected()){
                WifiInfo mWifiInfo = mWifiManager.getConnectionInfo();//返回当前活动的wifi信息
                String getSSID = mWifiInfo.getSSID();
                if (mWifiInfo != null && mInfo.isConnected() && getSSID != null) {
                    String quotedString = StringUtils.convertToQuotedString(mSsid);
                    boolean ssidEquals = quotedString.equals(getSSID);//getSSID得到的ssid带双引号
                    if (ssidEquals) {
                        if (mListener != null) {
                            mListener.onSuccess(mWifiInfo);
                            mListener.onFinished(true);
                        }
                        onPause();//如果连接成功，则撤销注册广播接收器
                    }
                }
            }
        }
    }

    public void onPause(){
        if(isRegistered){
            mContext.unregisterReceiver(mReceiver);
            isRegistered = false;
        }
        mScanner.pause();
    }

    public void onResume(){
        if(!isRegistered){
            mContext.registerReceiver(mReceiver, mFilter);
            isRegistered = true;
        }
//        mScanner.resume();
    }

    @SuppressLint("HandlerLeak")//规避检查规则：确保类内部的handler不含有对外部类的隐式引用
    private class ScannerHandler extends Handler {//这个handler的发送和处理消息是在同一个进程中
        private int mRetry = 0;

        void resume(){
            if(!hasMessages(0)){
                sendEmptyMessage(0);
            }
        }

        void forceScan(){        //触发扫描
            removeMessages(0);
            sendEmptyMessage(0);
        }

        void pause(){         //结束扫描
            mRetry = 0;
            isActiveScan = false;
            removeMessages(0);
        }

        @Override
        public void handleMessage(Message message){   //只有sendMessage才能触发这个函数
            if(mRetry < MAX_TRY_COUNT){   //if语句，不是for
                mRetry++;
                isActiveScan = true;
                if (!mWifiManager.isWifiEnabled()){
                    mWifiManager.setWifiEnabled(true);
                }

                boolean startScan = mWifiManager.startScan();//立刻返回,返回扫描是否启动,扫描异步交给底层去做,耗时操作，扫描完成后发送ScanResults的广播
                Log.d(TAG, "StarScan:" + startScan);

                if (!startScan) {
                    if(mListener != null) {
                        mListener.onFailure("Scan failed, try later!");//扫描启动失败
                        mListener.onFinished(false);  //关闭扫描对话框
                    }
                    onPause();
                    return;
                }
            }else{
                mRetry = 0;
                isActiveScan = false;
                if(mListener != null){
                    mListener.onFailure("Cannot find specified SSID, scan countdown is over!");//扫描次数达到最大
                    mListener.onFinished(false);   //关闭扫描对话框
                }
                onPause();
                return;
            }
            sendEmptyMessageDelayed(0, WIFI_RESCAN_INTERVAL_MS);  //延迟5s发送消息，再次触发一次handleMessage函数。app前台和后台wifiServer的任务是异步的。
        }  //后台wifiServer如果有了结果，就会广播SCAN_RESULTS_AVAILABLE_ACTION，被handleEvent捕捉并处理，如果扫描结果中有app中用户输入的ssid，扫描结束。
    }      //扫描结果中如果没有app上用户配置的ssid，会继续扫描，直到前台handleMessage执行3次，也会停止扫描。

    public interface ActionListener {    //由MainActivity实现

        /**
         * The operation started
         *
         * @param ssid
         */
        public void onStarted(String ssid);

        /**
         * The operation succeeded
         *
         * @param ssid
         */

        public void onScan(String ssid);
        /**
         * The operation succeeded
         *
         * @param
         */

        public void onScanSuccess(String ssid);

        public void onSuccess(WifiInfo info);

        /**
         * The operation failed
         */
        public void onFailure(String reason);

        /**
         * The operation finished
         *
         * @param isSuccessed
         */
        public void onFinished(boolean isSuccessed);

        public void onClearConfig();

        public void onShutDownWifi();
    }
}
