package com.work.speedtest_overhead;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.work.speedtest_overhead.object.SpeedData;
import com.work.speedtest_overhead.object.UpDownObject;
import com.work.speedtest_overhead.util.Config;
import com.work.speedtest_overhead.util.Network;
import com.work.speedtest_overhead.wiget.SpeedView;

import speedtest.Download;
import com.work.speedtest_overhead.Interface.IDownloadListener;
import com.work.speedtest_overhead.Interface.IUploadListener;
import speedtest.Upload;

/**
 * Created by ngodi on 2/24/2016.
 */
public class SpeedActivity extends Activity {
    private static final String TAG = "SpeedActivity";
    Context context;
    ImageButton ibSetting;
    RelativeLayout rlDownloadSpeed, rlUploadSpeed, rlProgress;
    TextView tvDownloadMaxResult, tvDownloadAvgResult, tvDownloadWifi, tvDownloadLte;
    TextView tvUploadMaxResult, tvUploadAvgResult, tvUploadWifi, tvUploadLte;
    TextView tvProgressText;
    ProgressBar pbStatus;
    ImageButton ibStartSpeed;
    AsyncTask<Void, Void, String> atSpeedTest;
    SpeedView svSpeedDisplay;
    SharedPreferences prefs;
    public boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.speedtest);
        context = this;

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        ibSetting = (ImageButton) findViewById(R.id.ibSetting);
        rlDownloadSpeed = (RelativeLayout) findViewById(R.id.rlDownloadSpeed);
        rlUploadSpeed = (RelativeLayout) findViewById(R.id.rlUploadSpeed);
        tvDownloadMaxResult = (TextView) findViewById(R.id.tvDownloadMaxResult);
        tvDownloadAvgResult = (TextView) findViewById(R.id.tvDownloadAvgResult);
        tvDownloadWifi = (TextView) findViewById(R.id.tvDownloadWifi);
        tvDownloadLte = (TextView) findViewById(R.id.tvDownloadLte);
        tvUploadMaxResult = (TextView) findViewById(R.id.tvUploadMaxResult);
        tvUploadAvgResult = (TextView) findViewById(R.id.tvUploadAvgResult);
        tvUploadWifi = (TextView) findViewById(R.id.tvUploadWifi);
        tvUploadLte = (TextView) findViewById(R.id.tvUploadLte);

        rlProgress = (RelativeLayout) findViewById(R.id.rlProgress);
        tvProgressText = (TextView) findViewById(R.id.tvProgressText);

        svSpeedDisplay = (SpeedView) findViewById(R.id.svSpeedDisplay);
        pbStatus = (ProgressBar) findViewById(R.id.pbStatus);
        ibStartSpeed = (ImageButton) findViewById(R.id.ibStartSpeed);
        ibSetting.setOnClickListener(settingListener);
        ibStartSpeed.setOnClickListener(startListener);
        String ip = prefs.getString(Config.PREF_KEY_SERVER_HOST, null);
        int port = prefs.getInt(Config.PREF_KEY_SERVER_PORT, 0);
        if((ip == null) || (port == 0))
            SettingServer(context);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

//        loManager.removeUpdates(gpslistener);

    }



    final Handler hSpeedCircle = new Handler() {
        public void handleMessage(Message msg) {
            float val = (float) msg.obj;
            svSpeedDisplay.setValue(val);
        }
    };

    final Handler hDownload = new Handler() {
        public void handleMessage(Message msg) {
            rlProgress.setVisibility(View.GONE);
            tvProgressText.setText("Download");
            rlDownloadSpeed.setVisibility(View.VISIBLE);
            UpDownObject object = (UpDownObject) msg.obj;
            tvDownloadMaxResult.setText(String.format("%.2f", object.getMax()));
            tvDownloadAvgResult.setText(String.format("%.2f", object.getAvg()));
            tvDownloadWifi.setText(String.format("%.2f", object.getWifi()));
            tvDownloadLte.setText(String.format("%.2f", object.getLte()));
            svSpeedDisplay.setValue(object.getMax());
        }
    };

    final Handler hUpload = new Handler() {
        public void handleMessage(Message msg) {
            rlProgress.setVisibility(View.GONE);
            tvProgressText.setText("Upload");
            rlUploadSpeed.setVisibility(View.VISIBLE);
            UpDownObject object = (UpDownObject) msg.obj;
            tvUploadMaxResult.setText(String.format("%.2f", object.getMax()));
            tvUploadAvgResult.setText(String.format("%.2f", object.getAvg()));
            tvUploadWifi.setText(String.format("%.2f", object.getWifi()));
            tvUploadLte.setText(String.format("%.2f", object.getLte()));
            svSpeedDisplay.setValue(object.getMax());
        }
    };

    final Handler hStatus = new Handler() {
        public void handleMessage(Message msg) {
            SpeedData data = (SpeedData)msg.obj;
            rlProgress.setVisibility(View.VISIBLE);
        }
    };

    final Handler hButton = new Handler() {
        public void handleMessage(Message msg) {
            SpeedData data = (SpeedData)msg.obj;
            rlProgress.setVisibility(View.VISIBLE);
            ibStartSpeed.setVisibility(View.VISIBLE);
        }
    };

    public OnClickListener settingListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            SettingServer(context);
        }
    };

    public OnClickListener startListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            rlDownloadSpeed.setVisibility(View.GONE);
            rlUploadSpeed.setVisibility(View.GONE);

            if(!isRunning) {
                SelectTest(context);
            } else
                Toast.makeText(context, "upload or download progress is running", Toast.LENGTH_LONG).show();
        }
    };

    public void SettingServer(Context context) {
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.settingserver);
        dialog.setTitle("Setting Server");
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        final EditText etServerIp = (EditText)dialog.findViewById(R.id.etServerIp);
        final EditText etServerPort = (EditText)dialog.findViewById(R.id.etServerPort);
        String ip = prefs.getString(Config.PREF_KEY_SERVER_HOST, null);
        int port = prefs.getInt(Config.PREF_KEY_SERVER_PORT, 0);
        if(ip != null)
            etServerIp.setText(ip);
        if(port > 0)
            etServerPort.setText(String.valueOf(port));
        Button btSettingServerOk = (Button)dialog.findViewById(R.id.btSettingServerOk);
        btSettingServerOk.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                try {
                    SharedPreferences.Editor editor = prefs.edit();
                    String ip = etServerIp.getText().toString().trim();
                    if (!Network.isIP(ip))
                        Toast.makeText(SpeedActivity.this, "Server host is not ip address", Toast.LENGTH_LONG).show();
                    int port = Integer.valueOf(etServerPort.getText().toString().trim());
                    if (Network.CheckHost(ip)) {
                        editor.putString(Config.PREF_KEY_SERVER_HOST, ip);
                        editor.putInt(Config.PREF_KEY_SERVER_PORT, port);
                        Config.strServer_Ip = ip;
                        Config.iServer_Port = port;
                        Log.d(TAG, "ip: " + Config.strServer_Ip + ", port: " + Config.iServer_Port);
                        editor.commit();
                        dialog.dismiss();
                    } else
                        Toast.makeText(SpeedActivity.this, "Can not ping to server ip", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(SpeedActivity.this, "port is number", Toast.LENGTH_LONG).show();
                }
            }
        });
        dialog.show();
    }

    public void SelectTest(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);
        builder.setTitle("Select Test");
        final String[] list = context.getResources().getStringArray(R.array.list_select_test);
        builder.setSingleChoiceItems(list, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, final int which) {
                dialog.dismiss();
                tvProgressText.setText(list[which]);
                atSpeedTest = new AsyncTask<Void, Void, String>() {
                    @Override
                    protected String doInBackground(Void... params) {
                        isRunning = true;
                        String ip = prefs.getString(Config.PREF_KEY_SERVER_HOST, null);
                        int port = prefs.getInt(Config.PREF_KEY_SERVER_PORT, 0);
                        Log.d(TAG, "ip: " + ip + ", port: " + port);
                        if(list[which].equals("Download")) {
                            Download down = new Download(ip, port, "/speedtest/", Config.DOWNLOAD_FILE);
                            down.addDownloadTestListener(new IDownloadListener() {
                                @Override
                                public void onDownloadPacketsReceived(float transferRateBitPerSeconds, float wifi_avg, float lte_avg) {
                                    Log.d(TAG, "Download [ OK ]");
                                    Log.d(TAG, "download transfer rate  : " + transferRateBitPerSeconds + " Mbit/second");
                                    Log.d(TAG, "##################################################################");

                                    UpDownObject data = new UpDownObject(transferRateBitPerSeconds, wifi_avg, lte_avg);
                                    Message msg = new Message();
                                    msg.obj = data;
                                    hDownload.sendMessage(msg);
                                }

                                @Override
                                public void onDownloadError(int errorCode, String message) {

                                }

                                @Override
                                public void onDownloadProgress(int percent) {
//                                  Log.d(TAG, "Download  percent: " + percent);
                                    if (percent == 0) {
                                        Message msg = new Message();
                                        hStatus.sendMessage(msg);
                                    }
                                    pbStatus.setProgress(percent);
                                }

                                @Override
                                public void onDownloadUpdate(float speed) {
                                    Log.d(TAG, "onDownloadUpdate speed: " + speed);
                                    Message msg = new Message();
                                    msg.obj = speed;
                                    hSpeedCircle.sendMessage(msg);
                                }
                            });
                            down.Download_Run();
                        } else if(list[which].equals("Upload")) {
                            Upload up = new Upload(ip, 80, "/", Config.UPLOAD_SIZE);
                            up.addUploadTestListener(new IUploadListener() {
                                @Override
                                public void onUploadPacketsReceived(float transferRateBitPerSeconds, float wifi_avg, float lte_avg) {
                                    Log.d(TAG, "========= Upload [ OK ]   =============");
                                    Log.d(TAG, "upload transfer rate  : " + transferRateBitPerSeconds + " Mbit/second");
                                    Log.d(TAG, "##################################################################");
                                    UpDownObject data = new UpDownObject(transferRateBitPerSeconds, wifi_avg, lte_avg);
                                    Message msg = new Message();
                                    msg.obj = data;
                                    hUpload.sendMessage(msg);
                                }

                                @Override
                                public void onUploadError(int errorCode, String message) {

                                }

                                @Override
                                public void onUploadProgress(int percent) {
//                                  Log.d(TAG, "Upload  percent: " + percent);
                                    if (percent == 0) {
                                        Message msg = new Message();
                                        hStatus.sendMessage(msg);
                                    }
                                    pbStatus.setProgress(percent);
                                }

                                @Override
                                public void onUploadUpdate(float speed) {
                                    Log.d(TAG, "onUploadUpdate speed: " + speed);
                                    Message msg = new Message();
                                    msg.obj = speed;
                                    hSpeedCircle.sendMessage(msg);
                                }
                            });
                            up.Upload_Run();
                        }
                        isRunning = false;
                        return "";
                    }

                    @Override
                    protected void onPostExecute(String result) {

                    }

                };
                atSpeedTest.execute(null, null, null);
            }
        });
        builder.show();
    }
}

