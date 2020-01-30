package com.media.cache;

import android.net.Uri;
import android.text.TextUtils;

import com.media.cache.hls.M3U8;
import com.media.cache.hls.M3U8Utils;
import com.media.cache.listener.IVideoInfoCallback;
import com.media.cache.listener.IVideoInfoParseCallback;
import com.media.cache.utils.HttpUtils;
import com.media.cache.utils.LocalProxyThreadUtils;
import com.media.cache.utils.LocalProxyUtils;
import com.media.cache.utils.LogUtils;

import java.io.File;
import java.util.HashMap;

public class VideoInfoParserManager {

    private static VideoInfoParserManager sInstance;
    private IVideoInfoCallback mCallback;
    private LocalProxyConfig mConfig;

    public static VideoInfoParserManager getInstance() {
        if (sInstance == null) {
            synchronized (VideoInfoParserManager.class) {
                if (sInstance == null) {
                    sInstance = new VideoInfoParserManager();
                }
            }
        }
        return sInstance;
    }

    public synchronized void parseVideoInfo(VideoCacheInfo info, IVideoInfoCallback callback, LocalProxyConfig config, HashMap<String, String> headers, boolean shouldRedirect, String contentType) {
        this.mCallback = callback;
        this.mConfig = config;
        LocalProxyThreadUtils.submitRunnableTask(new Runnable() {
            @Override
            public void run() {
                doParseVideoInfoTask(info, headers, shouldRedirect, contentType);
            }
        });
    }

    private void doParseVideoInfoTask(VideoCacheInfo info, HashMap<String, String> headers, boolean shouldRedirect, String contentType) {
        try {
            if (info == null) {
                mCallback.onBaseVideoInfoFailed(new Throwable("Video info is null."));
                return;
            }
            if (!HttpUtils.matchHttpSchema(info.getVideoUrl())) {
                mCallback.onBaseVideoInfoFailed(new Throwable("Can parse the request resource's schema."));
                return;
            }

            String finalUrl = info.getVideoUrl();

            //Redirect is enabled, send redirect request to get final location.
            if (mConfig.isRedirect() && shouldRedirect) {
                finalUrl = HttpUtils.getFinalUrl(mConfig, info.getVideoUrl(), headers);
                if (TextUtils.isEmpty(finalUrl)) {
                    mCallback.onBaseVideoInfoFailed(new Throwable("FinalUrl is null."));
                    return;
                }
                mCallback.onFinalUrl(finalUrl);
            }
            info.setFinalUrl(finalUrl);

            Uri uri = Uri.parse(finalUrl);
            String fileName = uri.getLastPathSegment();
            LogUtils.i("parseVideoInfo  fileName = " + fileName);
            //By suffix name.
            if (fileName != null) {
                fileName = fileName.toLowerCase();
                if (fileName.endsWith(".m3u8")) {
                    parseM3U8Info(info, headers);
                    return;
                } else if (fileName.endsWith(".mp4")) {
                    LogUtils.i("parseVideoInfo MP4_TYPE");
                    info.setVideoType(Video.Type.MP4_TYPE);
                    mCallback.onBaseVideoInfoSuccess(info);
                    return;
                } else if (fileName.endsWith(".mov")) {
                    LogUtils.i("parseVideoInfo QUICKTIME_TYPE");
                    info.setVideoType(Video.Type.QUICKTIME_TYPE);
                    mCallback.onBaseVideoInfoSuccess(info);
                    return;
                } else if (fileName.endsWith(".webm")) {
                    LogUtils.i("parseVideoInfo WEBM_TYPE");
                    info.setVideoType(Video.Type.WEBM_TYPE);
                    mCallback.onBaseVideoInfoSuccess(info);
                    return;
                } else if (fileName.endsWith(".3gp")) {
                    LogUtils.i("parseVideoInfo GP3_TYPE");
                    info.setVideoType(Video.Type.GP3_TYPE);
                    mCallback.onBaseVideoInfoSuccess(info);
                    return;
                }
            }
            String mimeType = null;
            if (!TextUtils.isEmpty(contentType) && !"unknown".equals(contentType)) {
                mimeType = contentType;
            } else {
                //Add more video mimeType.
                mimeType = HttpUtils.getMimeType(mConfig, finalUrl, headers);
            }
            LogUtils.i("parseVideoInfo mimeType="+mimeType);
            if (mimeType != null) {
                mimeType = mimeType.toLowerCase();
                if (mimeType.contains(Video.Mime.MIME_TYPE_MP4)) {
                    LogUtils.i("parseVideoInfo MP4_TYPE");
                    info.setVideoType(Video.Type.MP4_TYPE);
                    mCallback.onBaseVideoInfoSuccess(info);
                } else if (isM3U8Mimetype(mimeType)) {
                    parseM3U8Info(info, headers);
                } else if (mimeType.contains(Video.Mime.MIME_TYPE_WEBM)) {
                    LogUtils.i("parseVideoInfo QUICKTIME_TYPE");
                    info.setVideoType(Video.Type.WEBM_TYPE);
                    mCallback.onBaseVideoInfoSuccess(info);
                } else if (mimeType.contains(Video.Mime.MIME_TYPE_QUICKTIME)) {
                    LogUtils.i("parseVideoInfo WEBM_TYPE");
                    info.setVideoType(Video.Type.QUICKTIME_TYPE);
                    mCallback.onBaseVideoInfoSuccess(info);
                } else if (mimeType.contains(Video.Mime.MIME_TYPE_3GP)) {
                    LogUtils.i("parseVideoInfo GP3_TYPE");
                    info.setVideoType(Video.Type.GP3_TYPE);
                    mCallback.onBaseVideoInfoSuccess(info);
                } else {
                    mCallback.onBaseVideoInfoFailed(new Throwable("MimeType not found."));
                }
            } else {
                mCallback.onBaseVideoInfoFailed(new Throwable("MimeType is null."));
            }
        } catch (Exception e) {
            mCallback.onBaseVideoInfoFailed(e);
        }
    }

    private boolean isM3U8Mimetype(String mimeType) {
        return mimeType.contains(Video.Mime.MIME_TYPE_M3U8_1)
                || mimeType.contains(Video.Mime.MIME_TYPE_M3U8_2)
                || mimeType.contains(Video.Mime.MIME_TYPE_M3U8_3)
                || mimeType.contains(Video.Mime.MIME_TYPE_M3U8_4);
    }

    private void parseM3U8Info(VideoCacheInfo info, HashMap<String, String> headers) {
        try {
            M3U8 m3u8 = M3U8Utils.parseM3U8Info(info.getVideoUrl(), false, null);
            //HLS LIVE video cannot be proxy cached.
            if (m3u8.hasEndList()) {
                String saveName = LocalProxyUtils.computeMD5(info.getVideoUrl());
                File dir = new File(mConfig.getCacheRoot(), saveName);
                if (!dir.exists()) {
                    dir.mkdir();
                }
                M3U8Utils.createRemoteM3U8(dir, m3u8);

                info.setSaveDir(dir.getAbsolutePath());
                info.setVideoType(Video.Type.HLS_TYPE);
                mCallback.onM3U8InfoSuccess(info, m3u8);
            } else {
                info.setVideoType(Video.Type.HLS_LIVE_TYPE);
                mCallback.onLiveM3U8Callback(info);
            }
        } catch (Exception e) {
            mCallback.onM3U8InfoFailed(e);
        }
    }

    public void parseM3U8File(VideoCacheInfo info, IVideoInfoParseCallback callback) {
        File remoteM3U8File = new File(info.getSaveDir(), "remote.m3u8");
        if (!remoteM3U8File.exists()) {
            callback.onM3U8FileParseFailed(info, new Throwable("Cannot find remote.m3u8 file."));
            return;
        }
        try {
            M3U8 m3u8 = M3U8Utils.parseM3U8Info(info.getVideoUrl(), true, remoteM3U8File);
            callback.onM3U8FileParseSuccess(info, m3u8);
        } catch (Exception e) {
            callback.onM3U8FileParseFailed(info, e);
        }
    }

}
