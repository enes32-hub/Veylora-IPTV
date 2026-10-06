package tv.own.owntv.core.tv

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.settings.SettingsRepository
import java.util.concurrent.TimeUnit

/** Best-effort daily local refresh. Never starts an activity or a media stream. */
class DailyMovieWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val koin = GlobalContext.get()
        val settings = koin.get<SettingsRepository>()
        if (!settings.androidTvHomeEnabled.first()) return Result.success()
        val profile = settings.activeProfileId.first()
        if (profile >= 0) koin.get<TvHomeRepository>().refreshProfile(profile)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            if (!tv.own.owntv.core.CoreBuildInfo.tvHome) {
                WorkManager.getInstance(context).cancelUniqueWork("veylora-daily-movies")
                return
            }
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "veylora-daily-movies", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyMovieWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(24, TimeUnit.HOURS).build(),
            )
        }
    }
}
