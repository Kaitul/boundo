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

package com.madness.collision.unit.api_viewing.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.madness.collision.util.Xml
import java.io.File
import java.util.zip.ZipFile

object ManifestUtil {
    /**
     * Using [xml][Xml]
     * @param path That of an APK file.
     * @param attr The desired attribute, without name space.
     * @sample getManifestAttr("/sdcard/00a/Boundo.apk", arrayOf("application", "roundIcon"))
     * @see Xml
     */
    fun getManifestAttr(path: String, attr: Array<String> = emptyArray()): String {
        return getManifestAttr(File(path), attr)
    }

    fun getManifestAttr(file: File, attr: Array<String> = emptyArray()): String {
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml") ?: return@use ""
                zip.getInputStream(entry).use {
                    Xml(it.readBytes(), Xml.MODE_FIND, attr).attrAsset
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            ""
        }
    }

    fun getIcon(context: Context, applicationInfo: ApplicationInfo, sourceDir: String): Drawable? {
        try {
            val res = context.packageManager.getResourcesForApplication(applicationInfo)
            val resID = getManifestAttr(sourceDir, arrayOf("application", "icon"))
            if (resID.isEmpty()) return null
            return ResourcesCompat.getDrawable(res, resID.toInt(), null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getRoundIcon(context: Context, applicationInfo: ApplicationInfo, sourceDir: String): Drawable? {
        try {
            val res = context.packageManager.getResourcesForApplication(applicationInfo)
            val resID = getManifestAttr(sourceDir, arrayOf("application", "roundIcon"))
            if (resID.isEmpty()) return null
            return ResourcesCompat.getDrawable(res,resID.toInt(), null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getMinSdk(sourceDir: String): String {
        try {
            val res = getManifestAttr(sourceDir, arrayOf("uses-sdk", "minSdkVersion"))
            if (res.isEmpty()) return ""
            return res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    fun getCompileSdk(sourceDir: String): String {
        try {
            val res = getManifestAttr(sourceDir, arrayOf("manifest", "compileSdkVersion"))
            if (res.isEmpty()) return ""
            return res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }
}