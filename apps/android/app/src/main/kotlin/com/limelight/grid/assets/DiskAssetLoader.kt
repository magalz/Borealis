package com.limelight.grid.assets;

import android.content.Context;
import android.graphics.BitmapFactory;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.utils.CacheHelper;

import java.io.File;

public class DiskAssetLoader {
    private final File cacheDir;

    public DiskAssetLoader(Context context) {
        this.cacheDir = context.getCacheDir();
    }

    public boolean checkCacheExists(LoaderTuple tuple) {
        return CacheHelper.cacheFileExists(cacheDir, "boxart", tuple.computer.uuid, tuple.app.getAppId() + ".png");
    }

    // https://developer.android.com/topic/performance/graphics/load-bitmap.html
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculates the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public File getFile(String computerUuid, int appId) {
        return CacheHelper.openPath(false, cacheDir, "boxart", computerUuid, appId + ".png");
    }

    public void deleteAssetsForComputer(String computerUuid) {
        File dir = CacheHelper.openPath(false, cacheDir, "boxart", computerUuid);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
    }

    // BOREALIS: moved here from the 12.2 asset loader that was removed with the host and app lists.
    public static class LoaderTuple {
        public final ComputerDetails computer;
        public final NvApp app;

        public LoaderTuple(ComputerDetails computer, NvApp app) {
            this.computer = computer;
            this.app = app;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LoaderTuple)) {
                return false;
            }

            LoaderTuple other = (LoaderTuple) o;
            return computer.uuid.equals(other.computer.uuid) && app.getAppId() == other.app.getAppId();
        }

        @Override
        public String toString() {
            return "("+computer.uuid+", "+app.getAppId()+")";
        }
    }
}
