package com.guestvault.data.session

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.guestvault.domain.model.WipeResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cleans up after a guest session.
 *
 * Honest scope:
 * - GuestVault can always clear its own cache and the guest scratch directory.
 * - GuestVault can only clear *other* apps' data when it runs as Device Owner
 *   (`adb shell dpm set-device-owner ...` or a fully managed device). Android
 *   does not allow a normal app to do this.
 */
@Singleton
class GuestDataWiper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val guestWorkspace: File get() = File(context.filesDir, GUEST_DIR)

    /** Creates an isolated scratch area for the guest session. */
    fun prepareGuestWorkspace() {
        runCatching {
            if (!guestWorkspace.exists()) guestWorkspace.mkdirs()
            File(guestWorkspace, "session.lock").writeText(System.currentTimeMillis().toString())
        }.onFailure { Log.w(TAG, "Could not prepare guest workspace", it) }
    }

    suspend fun wipe(packages: Set<String>): WipeResult = withContext(Dispatchers.IO) {
        var filesRemoved = 0
        var bytesFreed = 0L

        val directories = listOfNotNull(
            context.cacheDir,
            context.externalCacheDir,
            guestWorkspace,
            File(context.filesDir, "guest_scratch"),
            cacheDir("webview"),
            cacheDir("code_cache")
        ).distinct()

        directories.forEach { directory ->
            val (files, bytes) = directory.stats()
            filesRemoved += files
            bytesFreed += bytes
            if (directory.exists() && !directory.deleteRecursively()) {
                Log.w(TAG, "Could not fully delete ${directory.absolutePath}")
            }
            directory.mkdirs()
        }

        var clearedByOwner = false
        val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
        if (devicePolicyManager?.isDeviceOwnerApp(context.packageName) == true) {
            clearedByOwner = true
            val executor = ContextCompat.getMainExecutor(context)
            packages.forEach { packageName ->
                runCatching {
                    val listener = DevicePolicyManager.OnClearApplicationUserDataListener { _, success ->
                        Log.i(TAG, "Device-owner data clear for $packageName: $success")
                    }
                    devicePolicyManager.clearApplicationUserData(packageName, executor, listener)
                }.onFailure { Log.w(TAG, "Device-owner wipe failed for $packageName", it) }
            }
        }

        Log.i(TAG, "Wiped $filesRemoved files ($bytesFreed bytes)")
        WipeResult(
            bytesFreed = bytesFreed,
            clearedAppCount = if (clearedByOwner) packages.size else 0,
            clearedByDeviceOwner = clearedByOwner,
            filesRemoved = filesRemoved
        )
    }

    private fun cacheDir(name: String): File? =
        runCatching { File(context.cacheDir, name) }.getOrNull()

    private fun File.stats(): Pair<Int, Long> {
        if (!exists()) return 0 to 0L
        var files = 0
        var bytes = 0L
        walkTopDown().forEach { file ->
            if (file.isFile) {
                files++
                bytes += file.length()
            }
        }
        return files to bytes
    }

    private companion object {
        const val TAG = "GuestDataWiper"
        const val GUEST_DIR = "guest"
    }
}
