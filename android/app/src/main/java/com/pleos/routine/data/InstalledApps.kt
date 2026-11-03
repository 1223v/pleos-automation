package com.pleos.routine.data

import android.content.Context
import android.content.Intent

data class InstalledApp(val packageName:String,val label:String)

/** Uses the existing MAIN/LAUNCHER visibility query, never a guessed package list. */
object InstalledApps {
    @Suppress("DEPRECATION")
    fun discover(context:Context):List<InstalledApp> {
        val manager=context.packageManager
        return manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .filter { info ->
                val activity=info.activityInfo
                activity!=null && activity.exported && activity.enabled && activity.applicationInfo.enabled &&
                    activity.packageName!=context.packageName && manager.getLaunchIntentForPackage(activity.packageName)!=null
            }
            .map { InstalledApp(it.activityInfo.packageName,it.loadLabel(manager).toString().take(100)) }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}
