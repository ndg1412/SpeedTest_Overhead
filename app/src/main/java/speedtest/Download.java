package speedtest;

import android.util.Log;

import com.work.speedtest_overhead.Interface.IDownloadListener;
import com.work.speedtest_overhead.util.Config;
import com.work.speedtest_overhead.util.Network;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import fr.bmartel.protocol.http.HttpFrame;
import fr.bmartel.protocol.http.states.HttpStates;

/**
 * Created by ngodi on 2/22/2016.
 */
public class Download {
    private static final String TAG = "Download";
    String host;
    int port;
    String uri;
    String[] files;
    long total_size = 0;
    long speed_size = 0;
    long finish_size = 0;
    int total_download = 0;
    Timer tiDownload = null;
    String sLteName = null;
    List<Float> lMax = new ArrayList<Float>();
    long wlan_rx = 0, lte_rx;
    long wlan_rx_first = 0, lte_rx_first;

    private IDownloadListener downloadTestListenerList;
    public void addDownloadTestListener(IDownloadListener listener) {
        downloadTestListenerList = listener;
    }

    public Download(String host, int port, String uri, String[] files) {
        this.host = host;
        this.port = port;
        this.uri = uri;
        this.files = files;
        sLteName = Network.getLTEIfName();
        for(String file : files) {
            total_size += getUrlSize("http://" + host + ":" + port + uri + file);
            Log.d(TAG, "download total_size: " + total_size);
        }
    }

    public String Create_Head(String file) {
        String url = this.uri + file;
        Log.d(TAG, "Create_Head uri: " + url);
        String downloadRequest = "GET " + url + " HTTP/1.1\r\n" + "Host: " + this.host + "\r\n\r\n";
        Log.d(TAG, "Create_Head host: " + this.host);
        Log.d(TAG, "Create_Head downloadRequest: " + downloadRequest);


        return downloadRequest;
    }

    public void Download_Run() {
        downloadTestListenerList.onDownloadProgress(0);

        BlockingQueue queue = new LinkedBlockingQueue(Config.NUMBER_QUEUE_THREAD);
        Producer procedure = new Producer(queue, files);
        Consumer consumer = new Consumer(queue, total_size);
        Thread thPro = new Thread(procedure);
        Thread thCon = new Thread(consumer);
        wlan_rx = wlan_rx_first = Network.getRxByte(Config.WLAN_IF);
        lte_rx = lte_rx_first = Network.getRxByte(sLteName);
        long timeStart = System.currentTimeMillis();

        thPro.start();
        thCon.start();

        tiDownload = new Timer();
        tiDownload.schedule(new TimerTask() {
            @Override
            public void run() {
                long tmp_wlan = Network.getRxByte(Config.WLAN_IF);
                long tmp_lte = Network.getRxByte(sLteName);
                long wlan = tmp_wlan;
                long lte = tmp_lte;
                if(wlan < wlan_rx)
                    wlan += Config.ULONG_MAX;
                if(lte < lte_rx)
                    lte += Config.ULONG_MAX;
                if((tmp_wlan != 0) || (tmp_lte != 0)) {
                    float speed = ((wlan + lte - wlan_rx - lte_rx) * 8 / 1000000 * (1000 / (Config.TIMER_SLEEP)));
                    lMax.add(speed);
                    downloadTestListenerList.onDownloadUpdate(speed);
                    wlan_rx = tmp_wlan;
                    lte_rx = tmp_lte;
                }
            }
        }, 0, Config.TIMER_SLEEP);
        try {
            while(thPro.isAlive())
                thPro.join(100);
            while(thCon.isAlive())
                thCon.join(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long timeEnd = System.currentTimeMillis();
        long wlan_rx_end = Network.getRxByte(Config.WLAN_IF);
        Log.d(TAG, "giang dbg lte interface name: " + sLteName);
        long lte_rx_end = Network.getRxByte(sLteName);
        float transferRate_bps = getBandwidth();
//        float transfer = (finish_size * 8) / ((timeEnd - timeStart) / 1000f)/1000000;
        if(wlan_rx_end < wlan_rx_first)
            wlan_rx_end = Config.ULONG_MAX + wlan_rx_end;
        if(lte_rx_end < lte_rx_first)
            lte_rx_end = Config.ULONG_MAX + lte_rx_end;
        float transfer_wifi = ((wlan_rx_end - wlan_rx_first) * 8) / ((timeEnd - timeStart) / 1000f)/1000000;
        float transfer_lte = ((lte_rx_end - lte_rx_first) * 8) / ((timeEnd - timeStart) / 1000f)/1000000;
        downloadTestListenerList.onDownloadPacketsReceived(transferRate_bps, transfer_wifi, transfer_lte);
        tiDownload.cancel();
        tiDownload = null;
    }

    public class Do_Download extends Thread {
        String file;
        int download_size = 0;

        public Do_Download(String file) {
            this.file = file;

        }
        @Override
        public void run() {
            String request = Create_Head(file);

            Socket socket = null;
            try {
                socket = new Socket();
//                socket.setTcpNoDelay(false);
//                socket.setSoTimeout(10 * 1000);
                /* establish socket parameters */
                socket.setReuseAddress(true);

                socket.setKeepAlive(true);
                Log.d(TAG, "using port: " + port);
                socket.connect(new InetSocketAddress(host, port));
//                socket.connect(new InetSocketAddress(host, port), 10*1000);
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(request.getBytes());
//                total_download += request.length();
                outputStream.flush();
                HttpFrame httpFrame = new HttpFrame();
                HttpStates errorCode = httpFrame.decodeFrame(socket.getInputStream());
                HttpStates headerError = httpFrame.parseHeader(socket.getInputStream());
                int read = 0;
                byte[] buffer = new byte[10240];
                long totalPackets = 0;
                int frameLength = httpFrame.getContentLength();
                while ((read = socket.getInputStream().read(buffer)) != -1) {
                    totalPackets += read;

                    speed_size += read;

                    downloadTestListenerList.onDownloadProgress((int) (100 * speed_size / total_size));
                    if (totalPackets == frameLength) {
                        break;
                    }
                }
                if(errorCode == HttpStates.HTTP_FRAME_OK) {
                    download_size = frameLength;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public int getDownloadSize() {
            return download_size;
        }
    }

    public class Producer implements Runnable {
        BlockingQueue queue;
        String[] files;

        public Producer(BlockingQueue queue, String[] files) {
            this.queue = queue;
            this.files = files;
        }

        @Override
        public void run() {
            for(String file : files) {
                Do_Download down = new Do_Download(file);
                down.start();
                try {
                    queue.put(down);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    public class Consumer implements Runnable {
        BlockingQueue queue;
        long total_size;

        public Consumer(BlockingQueue queue, long total_size) {
            this.queue = queue;
            this.total_size = total_size;
        }
        @Override
        public void run() {

            while(finish_size < total_size) {
                try {
                    Do_Download down = (Do_Download)queue.take();
                    while (down.isAlive()) {
                        down.join(100);
                    }
                    finish_size += down.getDownloadSize();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public float getBandwidth() {
        float max = lMax.get(0);
        for(int i = 1; i < lMax.size(); i++) {
            if(max < lMax.get(i))
                max = lMax.get(i);
        }
        return max;
    }

    public int getUrlSize(String url) {
        int size = 0;
        try
        {
            URL uri = new URL(url);
            URLConnection ucon;
            ucon = uri.openConnection();
            ucon.connect();
            String contentLengthStr = ucon.getHeaderField("content-length");
            Log.d(TAG, "getUrlSize: " + contentLengthStr);
            size = Integer.valueOf(contentLengthStr);
        } catch(IOException e) {
            e.printStackTrace();
        }
        return size;
    }
}
