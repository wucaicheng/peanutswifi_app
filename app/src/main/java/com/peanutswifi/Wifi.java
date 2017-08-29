package com.peanutswifi;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.text.TextUtils;

import java.util.List;


/**
 * Created by Jac on 2015/4/8.
 * fix bug by fengjiang 2017/8/29
 */
public class Wifi {

    private static final String TAG = Wifi.class.getSimpleName();
    private static boolean wifiConnnecting;

    public static boolean connectToNewNetwork(final WifiManager wifiMgr, final String ssid, final String encryp, final String passwd){

//        final List<WifiConfiguration> configurations = wifiMgr.getConfiguredNetworks();
//        for(final WifiConfiguration configTmp : configurations) {
//            wifiMgr.removeNetwork(configTmp.networkId);
//        }
//        wifiMgr.saveConfiguration();

        WifiConfiguration config = new WifiConfiguration();
        config.SSID = StringUtils.convertToQuotedString(ssid);
        // This is a network that does not broadcast its SSID, so an SSID-specific probe request must be used for scans


        ///////////////////////
        WifiConfiguration existConfig = IsExists(wifiMgr, config.SSID);
        if (existConfig == null) {
            //create new WifiConfiguration ,add to network
            config.hiddenSSID = true;
//        config.BSSID = bssid;
            setupSecurity(config, encryp, passwd);
            int id = wifiMgr.addNetwork(config);
            wifiConnnecting = wifiMgr.enableNetwork(id, true);
            if(!wifiConnnecting){
                return false;
            }
        } else {
            //specified ssid configration exists, use it
            config = existConfig;
            wifiConnnecting = wifiMgr.enableNetwork(config.networkId, true);
            if(!wifiConnnecting){
                return false;
            }
        }
        ////////////////////////



//        if(!wifiMgr.saveConfiguration()){
//            return false;
//        }

//        final List<WifiConfiguration> configurations2 = wifiMgr.getConfiguredNetworks();



//        final boolean connect = reassociate ? wifiMgr.reassociate() : wifiMgr.reconnect();

//        if(!connect) {
//            return false;
//        }

        return true;

    }

    static private void setupSecurity(WifiConfiguration config, String encryp, final String passwd){
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();

        if (encryp.equals("NONE")){
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);

        } else if (encryp.equals("WPA-AES-PSK")){
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);

            if (!TextUtils.isEmpty(passwd)){
                config.preSharedKey = StringUtils.convertToQuotedString(passwd);
            }

        } else if (encryp.equals("WPA-TKIP-PSK")){
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);

            if (!TextUtils.isEmpty(passwd)){
                config.preSharedKey = StringUtils.convertToQuotedString(passwd);
            }
        } else if (encryp.equals("WPA2-AES-PSK")){
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);

            if (!TextUtils.isEmpty(passwd)){
                config.preSharedKey = StringUtils.convertToQuotedString(passwd);
            }
        } else if (encryp.equals("WPA2-TKIP-PSK")){
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);

            if (!TextUtils.isEmpty(passwd)){
                config.preSharedKey = StringUtils.convertToQuotedString(passwd);
            }
        }
    }

    //Analyze whether WifiConfiguration for the specified SSID exists in WiFi
    static private WifiConfiguration IsExists(final WifiManager wifiMgr, String SSID) {

        List<WifiConfiguration> existingConfigs = wifiMgr.getConfiguredNetworks();
        for (WifiConfiguration existingConfig : existingConfigs) {
            if (existingConfig.SSID.equals(SSID)) {
                return existingConfig;
            }
        }
        return null;
    }

}
