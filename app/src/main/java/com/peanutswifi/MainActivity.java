package com.peanutswifi;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.peanutswifi.WifiConnecter.ActionListener;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends ActionBarActivity implements ActionListener {
    private WifiManager mWifiManager;
    private EditText et_passwd;
    private EditText et_ssid;
    private Spinner sp_encryp;
    private Button btn_conn;
    private Button speedtest;
    private Button btn_browser;

    private TextView tv_cur_ssid;
    private TextView tv_cur_bssid;
    private TextView tv_cur_speed;
    private TextView tv_cur_ip;
    private TextView tv_cur_freq;
    private TextView version;

    private static final String[] m = {"NONE", "WPA-AES-PSK", "WPA-TKIP-PSK", "WPA2-AES-PSK", "WPA2-TKIP-PSK"};
    private ArrayAdapter<String> adapter;

    private WifiConnecter mWifiConnecter;

    private ProgressDialog mDialog;

    public Boolean pref_checkbox;
    public String pref_frequency;
    public String pref_count;

    public String ssid;
    public String passwd;
    public String encryp;

    IperfTask iperfTask = null;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init() {
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiConnecter = new WifiConnecter(this);
        mDialog = new ProgressDialog(this);

        tv_cur_ssid = (TextView) findViewById(R.id.cur_ssid);
        tv_cur_bssid = (TextView) findViewById(R.id.cur_bssid);
        tv_cur_speed = (TextView) findViewById(R.id.cur_speed);
        tv_cur_ip = (TextView) findViewById(R.id.cur_ip);
        tv_cur_freq = (TextView) findViewById(R.id.cur_freq);

        et_ssid = (EditText) findViewById(R.id.ssid);
        et_passwd = (EditText) findViewById(R.id.passwd);
        sp_encryp = (Spinner) findViewById(R.id.spinner_encryp);

        btn_conn = (Button) findViewById(R.id.button);

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, m);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp_encryp.setAdapter(adapter);
//        sp_encryp.setOnItemSelectedListener(new SpinnerSelectedListener());
        sp_encryp.setSelection(0);
        sp_encryp.setVisibility(View.VISIBLE);

        version = (TextView) findViewById(R.id.version);
        version.setText("v" + getVersionName());

        speedtest = (Button) findViewById(R.id.speedtest);
        speedtest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intentSpeedtest = new Intent(MainActivity.this, SpeedTestLauncher.class);
                MainActivity.this.startActivity(intentSpeedtest);
            }
        });

        btn_browser = (Button) findViewById(R.id.browserBtnMain);
        btn_browser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intentBrowser = new Intent(MainActivity.this, BrowserWebsiteLauncher.class);
                MainActivity.this.startActivity(intentBrowser);
            }
        });
        setCurrentSsid();
    }

    private void setCurrentSsid() {
        WifiInfo info = mWifiManager.getConnectionInfo();
        String s = (info == null) ? "null" : info.getSSID();
        String b = (info == null) ? "null" : info.getBSSID();
        b = StringUtils.convertToQuotedString(b);
        int ls = (info == null) ? 0 : info.getLinkSpeed();
        String ip = (info == null) ? "null" : Formatter.formatIpAddress(info.getIpAddress());
        int freq = (info == null) ? 0 : info.getFrequency();

        tv_cur_ssid.setText(String.format(getString(R.string.cur_ssid), s));
        tv_cur_bssid.setText(getString(R.string.cur_bssid) + b);
        tv_cur_speed.setText(getString(R.string.cur_speed) + ls);
        tv_cur_ip.setText(getString(R.string.cur_ip) + ip);
        tv_cur_freq.setText(getString(R.string.cur_freq) + freq);

    }

    public void connect(View view) {
        final Timer timer = new Timer();
        final Handler handler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what > 1) {
                    connectPeriod();// cannot call mWifiConnecter.clearConnect directly
                } else if (msg.what == 1) {
                    connectPeriod(); //last connect set button text to "CONNECT"
                    timer.cancel();
                    btn_conn.setText("CONNECT");
                    btn_conn.setEnabled(true);
                }
            }
        };
        ssid = et_ssid.getText().toString();
        passwd = et_passwd.getText().toString();
        encryp = sp_encryp.getSelectedItem().toString();

        mWifiConnecter.clearConnect3(this);
        setCurrentSsid();
        mWifiConnecter.connect(ssid, encryp, passwd, this);

        if (pref_checkbox) {
            btn_conn.setEnabled(false);
            btn_conn.setText("Testing...");
            timer.schedule(new TimerTask() {
                int i = Integer.valueOf(pref_count).intValue();

                @Override
                public void run() {
                    if (i > 0) {
                        Message msg = new Message();
                        msg.what = i--;
                        handler.sendMessage(msg);
                    } else if (i == -1) {
                        Message msg = new Message();
                        msg.what = 2;
                        handler.sendMessage(msg);
                    }
                }
            }, Integer.valueOf(pref_frequency).intValue() * 1500, Integer.valueOf(pref_frequency).intValue() * 1000);
        }
    }

    public void connectPeriod() {
        mWifiConnecter.shutDownWifi();
        setCurrentSsid();
//        mWifiConnecter.connect(ssid, encryp, passwd, this);
        mWifiConnecter.turnOnWifi();
        setCurrentSsid();
    }

    public void clearConnect(View view) {
        mWifiConnecter.clearConnect3(this);
        setCurrentSsid();
        et_ssid.setText("");
        et_passwd.setText("");
        sp_encryp.setSelection(0);
    }

    public void scan(View view) {
        ssid = et_ssid.getText().toString();
        mWifiConnecter.clearConnect2();
        mWifiConnecter.scanSpecifiedSSID(ssid, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        pref_checkbox = prefs.getBoolean("example_checkbox", false);
        pref_frequency = prefs.getString("example_list", "null");
        pref_count = prefs.getString("example_list2", "null");

//        share data
        Data myData = (Data) getApplication();
        myData.prefCheckBox = pref_checkbox;
        myData.prefFreq = pref_frequency;
        myData.prefCount = pref_count;
    }

    @Override
    public void onStarted(String ssid) {
        Log.v("jacard", "------onStarted------");
        Toast.makeText(MainActivity.this, "onStarted", Toast.LENGTH_SHORT).show();
        mDialog.setMessage("Connecting to " + ssid + " ...");
        mDialog.show();
    }

    @Override
    public void onScan(String ssid) {
        Log.v("jacard", "------onScan------");
        Toast.makeText(MainActivity.this, "onScan", Toast.LENGTH_SHORT).show();
        mDialog.setMessage("Scaning " + ssid + " ...");
        mDialog.show();
    }

    public void onScanSuccess(String ssid) {
        Log.v("jacard", "------onScanSuccess------");
        Toast.makeText(MainActivity.this, "onScanSuccess : " + ssid, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSuccess(WifiInfo info) {
        Log.v("jacard", "------onSuccess------");
        Toast.makeText(MainActivity.this, "onSuccess : " + info.getSSID(), Toast.LENGTH_SHORT).show();
        setCurrentSsid();
    }

    @Override
    public void onFailure(String reason) {
        Log.v("jacard", "------onFailure------" + reason);
        Toast.makeText(MainActivity.this, "onFailure : " + reason, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFinished(boolean isSuccessed) {
        Log.v("jacard", "------onFinished------");
        mDialog.dismiss();
        Toast.makeText(MainActivity.this, "onFinished : " + isSuccessed, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClearConfig() {
        Toast.makeText(MainActivity.this, "onClearConfig", Toast.LENGTH_LONG).show();
    }

    public void onShutDownWifi() {
        Toast.makeText(MainActivity.this, "onShutDownWifi", Toast.LENGTH_LONG).show();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity2.class);
            item.setIntent(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    public void ToggleButtonClick(View v) {
        final ToggleButton toggleButton = (ToggleButton) findViewById(R.id.toggleButton);
        if (toggleButton.isChecked()) {

            initIperf();
            //If a test is already running then a cancel command is issued through the iperfTask interface.
        } else {
            if (iperfTask == null) {
                toggleButton.setChecked(false);
                return;
            }
            iperfTask.cancel(true);
            iperfTask.onCancelled();
            iperfTask = null;
        }
    }

    public void initIperf() {
//        final TextView tv = (TextView) findViewById(R.id.OutputText);
        InputStream in;
        try {
            //The asset "iperf" (from assets folder) inside the activity is opened for reading.
            in = getResources().getAssets().open("iperf");
        } catch (IOException e2) {
//            tv.append("\nError occurred while accessing system resources, please reboot and try again.");
            return;
        }
        try {
            //Checks if the file already exists, if not copies it.
            new FileInputStream("/data/data/com.peanutswifi/iperf");
        } catch (FileNotFoundException e1) {
            try {
                //The file named "iperf" is created in a system designated folder for this application.
                OutputStream out = new FileOutputStream("/data/data/com.peanutswifi/iperf", false);
                // Transfer bytes from "in" to "out"
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();
                //After the copy operation is finished, we give execute permissions to the "iperf" executable using shell commands.
                Process processChmod = Runtime.getRuntime().exec("/system/bin/chmod 744 /data/data/com.peanutswifi/iperf");
                // Executes the command and waits untill it finishes.
                processChmod.waitFor();
            } catch (IOException e) {
//                tv.append("\nError occurred while accessing system resources, please reboot and try again.");
                return;
            } catch (InterruptedException e) {
//                tv.append("\nError occurred while accessing system resources, please reboot and try again.");
                return;
            }
            //Creates an instance of the class IperfTask for running an iperf test, then executes.
            iperfTask = new IperfTask();
            iperfTask.execute();
            return;
        }
        //Creates an instance of the class IperfTask for running an iperf test, then executes.
        iperfTask = new IperfTask();
        iperfTask.execute();
        return;
    }

    class IperfTask extends AsyncTask<Void, String, String> {
        final ToggleButton toggleButton = (ToggleButton) findViewById(R.id.toggleButton);

        Process process = null;

        //This function is used to implement the main task that runs on the background.
        @Override
        protected String doInBackground(Void... voids) {
            //Iperf command syntax check using a Regular expression to protect the system from user exploitation.
//            String str = inputCommands.getText().toString();
            String str = "iperf -s"; // excute iperf command,can be changed or use EditView for user input
            if (!str.matches("(iperf )?((-[s,-server])|(-[c,-client] ([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5]))|(-[c,-client] \\w{1,63})|(-[h,-help]))(( -[f,-format] [bBkKmMgG])|(\\s)|( -[l,-len] \\d{1,5}[KM])|( -[B,-bind] \\w{1,63})|( -[r,-tradeoff])|( -[v,-version])|( -[N,-nodelay])|( -[T,-ttl] \\d{1,8})|( -[U,-single_udp])|( -[d,-dualtest])|( -[w,-window] \\d{1,5}[KM])|( -[n,-num] \\d{1,10}[KM])|( -[p,-port] \\d{1,5})|( -[L,-listenport] \\d{1,5})|( -[t,-time] \\d{1,8})|( -[i,-interval] \\d{1,4})|( -[u,-udp])|( -[b,-bandwidth] \\d{1,20}[bBkKmMgG])|( -[m,-print_mss])|( -[P,-parallel] d{1,2})|( -[M,-mss] d{1,20}))*")) {
                publishProgress("Error: invalid syntax. Please try again.\n\n");
                return null;
            }
            try {
                //The user input for the parameters is parsed into a string list as required from the ProcessBuilder Class.
//                String[] commands = inputCommands.getText().toString().split(" ");
                String[] commands = str.split(" ");
                List<String> commandList = new ArrayList<String>(Arrays.asList(commands));
                //If the first parameter is "iperf", it is removed
                if (commandList.get(0).equals((String) "iperf")) {
                    commandList.remove(0);
                }
                //The execution command is added first in the list for the shell interface.
                commandList.add(0, "/data/data/com.peanutswifi/iperf");
                //The process is now being run with the verified parameters.
                process = new ProcessBuilder().command(commandList).redirectErrorStream(true).start();
                //A buffered output of the stdout is being initialized so the iperf output could be displayed on the screen.
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                int read;
                //The output text is accumulated into a string buffer and published to the GUI
                char[] buffer = new char[4096];
                StringBuffer output = new StringBuffer();
                while ((read = reader.read(buffer)) > 0) {
                    output.append(buffer, 0, read);
                    //This is used to pass the output to the thread running the GUI, since this is separate thread.
                    publishProgress(output.toString());
                    output.delete(0, output.length());
                }
                reader.close();
                process.destroy();
            } catch (IOException e) {
                publishProgress("\nError occurred while accessing system resources, please reboot and try again.");
                e.printStackTrace();
            }
            return null;

        }

        //This function is called by the AsyncTask class when IperfTask.cancel is called.
        //It is used to terminate an already running task.
        @Override
        public void onCancelled() {
            //The running process is destroyed and system resources are freed.
            if (process != null) {
                process.destroy();
                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //The toggle button is switched to "off"
            toggleButton.setChecked(false);
        }

        @Override
        public void onPostExecute(String result) {
            //The running process is destroyed and system resources are freed.
            if (process != null) {
                process.destroy();

                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //The toggle button is switched to "off"
            toggleButton.setChecked(false);

        }

    }

    private String getVersionName() {

        // 用于管理安装的apk和未安装的apk
        PackageManager packageManager = getPackageManager();

        try {
            // 得到apk的功能清单文件:为了防止出错直接使用getPackageName()方法获得包名
            PackageInfo packageInfo = packageManager.getPackageInfo(getPackageName(), 0);

            //返回版本名称
            return packageInfo.versionName;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
