package com.work.speedtest_overhead.Interface;

/**
 * Created by ngodi on 2/24/2016.
 */
public interface IDownloadListener {
    public void onDownloadPacketsReceived(float transferRateBitPerSeconds, float wifi_avg, float lte_avg);

    public void onDownloadError(int errorCode, String message);

    public void onDownloadProgress(int percent);
    public void onDownloadUpdate(float speed);
}
