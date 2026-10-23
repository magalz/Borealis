package com.limelight.grid.assets

import android.content.Context
import android.graphics.BitmapFactory
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.utils.CacheHelper
import java.io.File

class DiskAssetLoader(context: Context) {
    private val cacheDir: File = context.cacheDir

    fun checkCacheExists(tuple: LoaderTuple): Boolean =
        CacheHelper.cacheFileExists(
            cacheDir,
            "boxart",
            tuple.computer.uuid,
            tuple.app.appId.toString() + ".png",
        )

    fun getFile(computerUuid: String?, appId: Int): File =
        CacheHelper.openPath(false, cacheDir, "boxart", computerUuid, appId.toString() + ".png")

    fun deleteAssetsForComputer(computerUuid: String?) {
        val dir = CacheHelper.openPath(false, cacheDir, "boxart", computerUuid)
        val files = dir.listFiles()
        if (files != null) {
            for (f in files) {
                f.delete()
            }
        }
    }

    // BOREALIS: moved here from the 12.2 asset loader that was removed with the host and app lists.
    // Kept as the Java had it: equals without hashCode. Nothing uses a tuple as a key of a hash.
    @Suppress("EqualsWithHashCodeExist")
    class LoaderTuple(@JvmField val computer: ComputerDetails, @JvmField val app: NvApp) {
        override fun equals(other: Any?): Boolean {
            if (other !is LoaderTuple) {
                return false
            }

            return checkNotNull(computer.uuid) == other.computer.uuid &&
                app.appId == other.app.appId
        }

        override fun toString(): String = "(" + computer.uuid + ", " + app.appId + ")"
    }

    companion object {
        // https://developer.android.com/topic/performance/graphics/load-bitmap.html
        @JvmStatic
        fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int,
        ): Int {
            // Raw height and width of image
            val height = options.outHeight
            val width = options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2

                // Calculates the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while (
                    (halfHeight / inSampleSize) >= reqHeight &&
                        (halfWidth / inSampleSize) >= reqWidth
                ) {
                    inSampleSize *= 2
                }
            }

            return inSampleSize
        }
    }
}
