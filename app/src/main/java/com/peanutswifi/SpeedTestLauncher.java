/*
	This file is part of SpeedTest.

    SpeedTest is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SpeedTest is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with SpeedTest.  If not, see <http://www.gnu.org/licenses/>.

 */
package com.peanutswifi;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Test speed of our network connection
 * @author jacard
 * @version 1.0
 *
 */
public class SpeedTestLauncher extends Activity {

	List<Double> downlinkSpeedUpdateList = new ArrayList<>();
	List<Double> downlinkSpeedFinishList = new ArrayList<>();
	List<Double> uplinkSpeedUpdateList = new ArrayList<>();
	List<Double> uplinkSpeedFinishList = new ArrayList<>();

	long downloadStart = 0;
	long uploadStart = 0;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mDecimalFormater=new DecimalFormat("##.##");
		setContentView(R.layout.activity_speedtest);

		bindListeners();
	}

	/**
	 * Setup event handlers and bind variables to values from xml
	 */
	private void bindListeners() {
		mBtnStart = (Button) findViewById(R.id.speedtest_start);
		mTxtSpeed = (TextView) findViewById(R.id.downlink_speed);
		mTxtSpeed2 = (TextView) findViewById(R.id.uplink_speed);
		mTxtError = (TextView) findViewById(R.id.error_text);

		mBtnStart.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(final View view) {
				mBtnStart.setEnabled(false);
				for (String url: DOWNLOAD_URLS) {
					mWorker download = new mWorker();
					download.setUrl(url);
					Thread downloadThread = new Thread(download);
					downloadThread.start();
				}
			}
		});
	}


	private final Handler mHandler=new Handler(){
		@Override
		public void handleMessage(final Message msg) {
			double downlinkSpeedUpdata = 0;
			double downlinkSpeedFinish = 0;
			double uplinkSpeedUpdata = 0;
			double uplinkSpeedFinish = 0;

			switch(msg.what){
				case MSG_UPDATE_STATUS:
					final SpeedInfo info1=(SpeedInfo) msg.obj;
					downlinkSpeedUpdateList.add(info1.kiloBytes);
					if(downlinkSpeedUpdateList.size() == DOWNLOAD_URLS.length) {
						for (double speed: downlinkSpeedUpdateList){
							downlinkSpeedUpdata += speed;
						};
						mTxtSpeed.setText(String.format(getResources().getString(R.string.update_speed),
								mDecimalFormater.format(downlinkSpeedUpdata)));
						downlinkSpeedUpdateList.clear();
					};
					break;

				case MSG_COMPLETE_STATUS:
					final  SpeedInfo info2=(SpeedInfo) msg.obj;
					downlinkSpeedFinishList.add(info2.kiloBytes);
					final String errorUrl = msg.getData().getString("url");
					final String errorMess = msg.getData().getString("error");
					if (errorUrl != null) {
						mTxtError.append(errorUrl + " " + errorMess + "\n");
					}
					if(downlinkSpeedFinishList.size() == DOWNLOAD_URLS.length) {
						for (double speed: downlinkSpeedFinishList) {
							downlinkSpeedFinish += speed;
						}
						mTxtSpeed.setText(String.format(getResources().getString(R.string.update_downloaded_complete),
								mDecimalFormater.format(downlinkSpeedFinish)));
						downlinkSpeedFinishList.clear();

						for (String url: UPLOAD_URLS) {
							mWorkerUplink upload = new mWorkerUplink();
							upload.setUrl(url);
							Thread uploadThread = new Thread(upload);
							uploadThread.start();
						}
					};
					break;

				case MSG_UPDATE_STATUS_UPLINK:
					final SpeedInfo info3=(SpeedInfo) msg.obj;
					uplinkSpeedUpdateList.add(info3.kiloBytes);
					if(uplinkSpeedUpdateList.size() == UPLOAD_URLS.length) {
						for (double speed: uplinkSpeedUpdateList){
							uplinkSpeedUpdata += speed;
						};
						mTxtSpeed2.setText(String.format(getResources().getString(R.string.update_speed_uplink),
								mDecimalFormater.format(uplinkSpeedUpdata)));
						uplinkSpeedUpdateList.clear();
					};
					break;

				case MSG_COMPLETE_STATUS_UPLINK:
					final  SpeedInfo info4=(SpeedInfo) msg.obj;
					final String errorUrl2 = msg.getData().getString("url");
					final String errorMess2 = msg.getData().getString("error");
					if (errorUrl2 != null) {
						mTxtError.append(errorUrl2 + " " + errorMess2 + "\n");
					}
					uplinkSpeedFinishList.add(info4.kiloBytes);
					if(uplinkSpeedFinishList.size() == UPLOAD_URLS.length) {
						for (double speed : uplinkSpeedFinishList) {
							uplinkSpeedFinish += speed;
						}
						mTxtSpeed2.setText(String.format(getResources().getString(R.string.update_uploaded_complete),
								mDecimalFormater.format(uplinkSpeedFinish)));
						uplinkSpeedFinishList.clear();
						mBtnStart.setEnabled(true);
					}

					break;

				default:
					super.handleMessage(msg);
			}
		}
	};

	/**
	 * Our Slave worker that does actually all the work
	 */
	private final class mWorker implements Runnable{

		private String url;

		public void setUrl(String url) {
			this.url = url;
		}

		@Override
		public void run() {
			InputStream stream=null;

			try {
//				downlink speedtest
				int bytesIn=0;
				String downloadFileUrl=this.url;
				URL url=new URL(downloadFileUrl);
				URLConnection con=url.openConnection();
				con.setUseCaches(false);
				stream=con.getInputStream();

				downloadStart=System.currentTimeMillis();
				int currentByte=0;
				long updateStart=downloadStart;
				long updateDelta=0;
				int  bytesInThreshold=0;
				while((currentByte=stream.read())!=-1 && (System.currentTimeMillis()- downloadStart) < SPEED_TIME){
					bytesIn++;
					bytesInThreshold++;
					if(updateDelta>=UPDATE_THRESHOLD){
						Message msg=Message.obtain(mHandler, MSG_UPDATE_STATUS, calculate(updateDelta, bytesInThreshold));
						mHandler.sendMessage(msg);
						//Reset
						updateStart=System.currentTimeMillis();
						bytesInThreshold=0;
					}
					updateDelta = System.currentTimeMillis()- updateStart;
				}

				long downloadTime=(System.currentTimeMillis()-downloadStart);
				if(downloadTime==0){
					downloadTime=1;
				}

				Message msg=Message.obtain(mHandler, MSG_COMPLETE_STATUS, calculate(downloadTime, bytesIn));
				mHandler.sendMessage(msg);

			} 
			catch (MalformedURLException e) {
				Log.e(TAG, e.getMessage());
				Message msg=Message.obtain(mHandler, MSG_COMPLETE_STATUS, calculate(1, 1));
				Bundle bundle = new Bundle();
				bundle.putString("url", url);
				bundle.putString("error", e.getMessage());
				msg.setData(bundle);
				mHandler.sendMessage(msg);
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
				Message msg=Message.obtain(mHandler, MSG_COMPLETE_STATUS, calculate(1, 1));
				Bundle bundle = new Bundle();
				bundle.putString("url", url);
				bundle.putString("error", e.getMessage());
				msg.setData(bundle);
				mHandler.sendMessage(msg);
			}finally{
				try {
					if(stream!=null){
						stream.close();
					}
				} catch (IOException e) {
					//Suppressed
				}
			}

		}
	};


	private final class mWorkerUplink implements Runnable{

		private String url;

		public void setUrl(String url) {
			this.url = url;
		}

		@Override
		public void run() {
			OutputStream stream=null;

			try {
				int bytesOut=0;
				URL url = new URL(this.url);
				HttpURLConnection con = (HttpURLConnection)url.openConnection();
				con.setDoOutput(true);
				con.setChunkedStreamingMode(0);
				con.setRequestMethod("POST");
				stream=con.getOutputStream();
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream, "UTF-8"), 10);
				uploadStart=System.currentTimeMillis();
				long updateStart=uploadStart;
				long updateDelta=0;
				int  bytesOutThreshold=0;

				while((System.currentTimeMillis()-uploadStart) < SPEED_TIME){
					writer.write(genDataForUpload(100));
					bytesOut+=100;
					bytesOutThreshold+=100;
					if(updateDelta>=UPDATE_THRESHOLD){
						Message msg=Message.obtain(mHandler, MSG_UPDATE_STATUS_UPLINK, calculate(updateDelta, bytesOutThreshold));
						mHandler.sendMessage(msg);
						//Reset
						updateStart=System.currentTimeMillis();
						bytesOutThreshold=0;
					}
					updateDelta = System.currentTimeMillis()- updateStart;
				}

				long uploadTime=(System.currentTimeMillis()-uploadStart);
				if(uploadTime==0){
					uploadTime=1;
				}

				Message msg=Message.obtain(mHandler, MSG_COMPLETE_STATUS_UPLINK, calculate(uploadTime, bytesOut));
				mHandler.sendMessage(msg);

			}
			catch (MalformedURLException e) {
				Log.e(TAG, e.getMessage());
				Message msg=Message.obtain(mHandler, MSG_COMPLETE_STATUS_UPLINK, calculate(1, 1));
				Bundle bundle = new Bundle();
				bundle.putString("url", url);
				bundle.putString("error", e.getMessage());
				msg.setData(bundle);
				mHandler.sendMessage(msg);
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
				Message msg=Message.obtain(mHandler, MSG_COMPLETE_STATUS_UPLINK, calculate(1, 1));
				Bundle bundle = new Bundle();
				bundle.putString("url", url);
				bundle.putString("error", e.getMessage());
				msg.setData(bundle);
				mHandler.sendMessage(msg);
			}finally{
				try {
					if(stream!=null){
						stream.close();
					}
				} catch (IOException e) {
					//Suppressed
				}
			}



		}
	}

	/**
	 * 	
	 * 1 byte = 0.0078125 kilobits
	 * 1 kilobits = 0.0009765625 megabit
	 * 
	 * @param downloadTime in miliseconds
	 * @param bytesIn number of bytes downloaded
	 * @return SpeedInfo containing current speed
	 */
	private SpeedInfo calculate(final long downloadTime, final long bytesIn){
		SpeedInfo info=new SpeedInfo();
		//from mil to sec
		long bytespersecond   =(bytesIn / downloadTime) * 1000;
//		double kilobits=bytespersecond * BYTE_TO_KILOBIT;
//		double megabits=kilobits  * KILOBIT_TO_MEGABIT;
//		info.downspeed=bytespersecond;
//		info.kilobits=kilobits;
//		info.megabits=megabits;
		double kiloBytes = bytespersecond * 0.0009765625;
		info.kiloBytes = kiloBytes;

		return info;
	}

	public String genDataForUpload(int size) throws IOException {

		Random random = new Random();
		StringBuilder builder = new StringBuilder(size);
		for(int i = 0;i< size;++i)
		{
			builder.append(Chars.charAt(random.nextInt(Chars.length())));
		}

		return  builder.toString();
	}


	/**
	 * Transfer Object
	 * @author devil
	 *
	 */
	private static class SpeedInfo{
		public double kilobits=0;
		public double megabits=0;
		public double downspeed=0;
		public double kiloBytes=0;
	}


	//Private fields	
	private static final String TAG = SpeedTestLauncher.class.getSimpleName();

	private static final double BYTE_TO_KILOBIT = 0.0078125;
//	private static final double BYTE_TO_KILOBIT = 0.008;
	private static final double KILOBIT_TO_MEGABIT = 0.0009765625;
	private static final String[] DOWNLOAD_URLS = {
			"http://dl.maxthon.cn/mx3/mx3.4.5.2000cn.exe",
            "http://dlsw.baidu.com/sw-search-sp/soft/3a/12350/QQ_8.1.17283.0_setup.1458109312.exe",
//			"http://xiuxiu.dl.meitu.com/XiuXiu_Setup_3.6.1.exe",
//			"http://download.ie.sogou.com/se/sogou_explorer_4.0q.exe",
//			"http://dl.baofeng.com/baofeng5/Baofeng5-5.19.1129.exe",
//			"http://download.ie.sogou.com/se/sogou_explorer_4.0q.exe",
    };

	private static final String[] UPLOAD_URLS = {
            "http://netsp.master.qq.com/cgi-bin/netspeed/",
			"http://www.taobao.com/",
//			"http://www.sohu.com/",
//			"http://www.kankan.com/",
//			"http://www.tudou.com/",
//			"http://www.speedtest.cn/",
//			"http://www.sogou.com/",
	};

	private Button mBtnStart;
	private TextView mTxtSpeed;
	private TextView mTxtSpeed2;
	private TextView mTxtError;

	private final int MSG_UPDATE_STATUS=1;
	private final int MSG_COMPLETE_STATUS=2;
	private final int MSG_UPDATE_STATUS_UPLINK=3;
	private final int MSG_COMPLETE_STATUS_UPLINK=4;

	private final static int UPDATE_THRESHOLD=300;
	private final static int SPEED_TIME = 5000;

	private DecimalFormat mDecimalFormater;
	private String Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
}