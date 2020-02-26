package com.media.cache;

import android.content.pm.PackageManager;

import com.android.baselib.utils.LogUtils;
import com.media.cache.model.VideoTaskItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Download Queue.
 * Non-thread safe
 */
public class DownloadQueue {

    private List<VideoTaskItem> mQueue;

    public DownloadQueue() {
        mQueue = new ArrayList<>();
    }

    //put it into queue
    public void offer(VideoTaskItem item) {
        mQueue.add(item);
    }

    //Remove Queue head item,
    //Return Next Queue head.
    public VideoTaskItem poll() {
        try {
            if (mQueue.size() >= 2) {
                mQueue.remove(0);
                return mQueue.get(0);
            } else if (mQueue.size() == 1) {
                mQueue.remove(0);
            }
        } catch (Exception e) {
            LogUtils.w("DownloadQueue remove failed.");
        }
        return null;
    }

    public VideoTaskItem peek() {
        try {
            if (mQueue.size() >= 1) {
                return mQueue.get(0);
            }
        } catch (Exception e) {
            LogUtils.w("DownloadQueue get failed.");
        }
        return null;
    }

    public boolean remove(VideoTaskItem item) {
        if (contains(item)) {
            return mQueue.remove(item);
        }
        return false;
    }

    public boolean contains(VideoTaskItem item) {
        return mQueue.contains(item);
    }

    public VideoTaskItem getTaskItem(String url) {
        try {
            for (int index = 0; index < mQueue.size(); index++) {
                if (mQueue.get(index).getUrl().equals(url)) {
                    return mQueue.get(index);
                }
            }
        } catch (Exception e) {
            LogUtils.w("DownloadQueue getTaskItem failed.");
        }
        return null;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int size() {
        return mQueue.size();
    }

    public boolean isHead(VideoTaskItem item) {
        return item.equals(peek());
    }
}
