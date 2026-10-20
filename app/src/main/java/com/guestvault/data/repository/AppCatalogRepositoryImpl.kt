package com.guestvault.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.guestvault.domain.model.AppCatalogDefaults
import com.guestvault.domain.model.GuestApp
import com.guestvault.domain.repository.AppCatalogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads every app that has a launcher entry on this device.
 *
 * Two important filters:
 * 1. GuestVault itself is never offered (the guest must not be able to open
 *    the owner dashboard).
 * 2. Other launchers / home apps are never offered. Allowing one would let a
 *    guest leave the restricted guest home instantly.
 */
@Singleton
class AppCatalogRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AppCatalogRepository {

    @Suppress("DEPRECATION")
    override suspend fun getLaunchableApps(): List<GuestApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val selfPackage = context.packageName

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        val launchers = runCatching {
            packageManager.queryIntentActivities(launcherIntent, 0)
        }.getOrElse { emptyList() }

        val homePackages = runCatching {
            packageManager.queryIntentActivities(homeIntent, 0)
        }.getOrElse { emptyList() }
            .map { it.activityInfo.packageName }
            .toSet()

        val seen = HashSet<String>(launchers.size)
        val apps = ArrayList<GuestApp>(launchers.size)

        for (resolveInfo in launchers) {
            val packageName = resolveInfo.activityInfo?.packageName ?: continue
            if (packageName == selfPackage) continue
            if (packageName in homePackages) continue
            if (!seen.add(packageName)) continue

            val appInfo = resolveInfo.activityInfo.applicationInfo ?: continue
            val label = runCatching {
                packageManager.getApplicationLabel(appInfo).toString()
            }.getOrNull().orEmpty().ifBlank { packageName }

            apps += GuestApp(
                packageName = packageName,
                label = label,
                category = AppCatalogDefaults.categoryFor(packageName),
                isRecommended = AppCatalogDefaults.isRecommended(packageName),
                isSensitive = AppCatalogDefaults.isSensitive(packageName)
            )
        }

        apps.sortedWith(
            compareByDescending<GuestApp> { it.isRecommended }
                .thenBy { it.label.lowercase() }
        ).also { Log.d(TAG, "Found ${it.size} launchable apps") }
    }

    private companion object {
        const val TAG = "AppCatalog"
    }
}
