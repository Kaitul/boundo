/*
 * Copyright 2021 Clifford Liu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.madness.collision

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import com.madness.collision.util.PermissionUtils
import java.io.InputStream
import java.util.*

object Test {

    fun go(context: Context) {
        if (PermissionUtils.isUsageAccessPermitted(context)) getForegroundApp(context)
    }

    fun getForegroundApp(context: Context): String {
        val time = System.currentTimeMillis()
        val manager: UsageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats: List<UsageStats> = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 1000, time)
        if (stats.isNullOrEmpty()) return ""
        val mySortedMap: SortedMap<Long, UsageStats> = TreeMap()
        stats.forEach { mySortedMap[it.lastTimeUsed] = it }
        return mySortedMap[mySortedMap.lastKey()]?.packageName ?: ""
    }

    fun getLauncherActivities(context: Context): List<ResolveInfo> {
        val pm: PackageManager = context.packageManager
//        LauncherApps() // look into this class
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(mainIntent, 0).sortedWith(ResolveInfo.DisplayNameComparator(pm))
    }

    fun processLargeImage(stream: InputStream) {
        val decoder: BitmapRegionDecoder? = BitmapRegionDecoder.newInstance(stream, false)
        val region = decoder?.decodeRegion(Rect(10, 10, 50, 50), null)
    }
}
