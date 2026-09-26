package com.kiroland.mediacenter.data.live

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Opens channels in the broadcaster's apps and the TV's own tuner app. */
@Singleton
class LiveTvLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val packages: PackageManager get() = context.packageManager

    /** Which of the apps we care about are installed right now (they can be installed while we run). */
    fun installed(): Set<String> =
        (listOf(OfficialApps.MEDIAKLIKK, OfficialApps.M4_SPORT, OfficialApps.PLAY_STORE) + OfficialApps.TUNER_APPS)
            .filterTo(HashSet()) { launchIntent(it) != null || isInstalled(it) }

    /** A real web browser, not the "no browser installed" stub some Android TV builds ship. */
    fun hasBrowser(): Boolean =
        packages.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse(PublicChannels.all.first().pageUrl)).addCategory(Intent.CATEGORY_BROWSABLE), 0)
            .any { it.activityInfo.packageName !in BROWSER_STUBS }

    /** @return false if nothing could be opened. */
    fun open(channel: LiveChannel): Boolean =
        LiveTvPlanner.steps(channel, installed(), hasBrowser()).any(::tryStep)

    fun openTuner(): Boolean {
        val app = LiveTvPlanner.tunerApp(installed()) ?: return false
        return tryStep(LaunchStep.LaunchApp(app))
    }

    private fun tryStep(step: LaunchStep): Boolean {
        val intent = when (step) {
            is LaunchStep.OpenLinkInApp ->
                Intent(Intent.ACTION_VIEW, Uri.parse(step.url)).setPackage(step.packageName)
            is LaunchStep.LaunchApp -> launchIntent(step.packageName) ?: return false
            is LaunchStep.OpenStorePage ->
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${step.packageName}"))
                    .setPackage(OfficialApps.PLAY_STORE)
            is LaunchStep.OpenWebsite ->
                Intent(Intent.ACTION_VIEW, Uri.parse(step.url)).addCategory(Intent.CATEGORY_BROWSABLE)
        }
        return start(intent)
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** TV apps register a leanback launcher entry; phone-first apps only a normal one. */
    private fun launchIntent(packageName: String): Intent? =
        packages.getLeanbackLaunchIntentForPackage(packageName) ?: packages.getLaunchIntentForPackage(packageName)

    private fun isInstalled(packageName: String): Boolean =
        runCatching { packages.getPackageInfo(packageName, 0) }.isSuccess

    private companion object {
        val BROWSER_STUBS = setOf("com.google.android.tv.frameworkpackagestubs")
    }
}
