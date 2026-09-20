package com.hamza.studyhub.teams

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalException
import java.util.concurrent.Executors

/**
 * Read-only Microsoft school-account authentication boundary.
 * No client secret is used; tokens are owned by MSAL and are never written to our files.
 */
object TeamsAuthManager {
    val SCOPES: List<String> = listOf("EduAssignments.ReadBasic")

    private val executor = Executors.newSingleThreadExecutor()

    fun signInAndSync(
        activity: Activity,
        onStatus: (String) -> Unit,
        onFinished: (Result<TeamsDeepSyncEngine.SyncSummary>) -> Unit
    ) {
        if (!TeamsAuthStore.isConfigured(activity)) {
            onFinished(Result.failure(IllegalStateException("Application client ID is not configured")))
            return
        }

        onStatus("جاري تجهيز تسجيل الدخول المدرسي...")
        val configFile = TeamsAuthStore.buildMsalConfigFile(activity)

        PublicClientApplication.createSingleAccountPublicClientApplication(
            activity.applicationContext,
            configFile,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication) {
                    val parameters = SignInParameters.builder()
                        .withActivity(activity)
                        .withScopes(SCOPES)
                        .withCallback(object : AuthenticationCallback {
                            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                                TeamsAuthStore.saveAccount(activity, authenticationResult.account?.username)
                                onStatus("تم تسجيل الدخول. جاري فحص Teams والواجبات القديمة والجديدة...")
                                executor.execute {
                                    val result = runCatching {
                                        val summary = TeamsDeepSyncEngine(
                                            activity.applicationContext,
                                            TeamsGraphClient(authenticationResult.accessToken)
                                        ).sync()
                                        TeamsAuthStore.saveAssignmentCount(
                                            activity,
                                            summary.discovered + summary.updated + summary.unchanged
                                        )
                                        TeamsAuthStore.saveSyncSuccess(activity)
                                        TeamsSyncWorker.schedule(activity.applicationContext)
                                        summary
                                    }.onFailure {
                                        TeamsAuthStore.saveSyncError(activity, it.message ?: it.javaClass.simpleName)
                                    }
                                    activity.runOnUiThread { onFinished(result) }
                                }
                            }

                            override fun onError(exception: MsalException) {
                                TeamsAuthStore.saveSyncError(activity, exception.message ?: exception.errorCode)
                                onFinished(Result.failure(exception))
                            }

                            override fun onCancel() {
                                onFinished(Result.failure(IllegalStateException("Microsoft sign-in was cancelled")))
                            }
                        })
                        .build()

                    application.signIn(parameters)
                }

                override fun onError(exception: MsalException) {
                    TeamsAuthStore.saveSyncError(activity, exception.message ?: exception.errorCode)
                    onFinished(Result.failure(exception))
                }
            }
        )
    }

    /** Worker-thread only. */
    fun createPca(context: Context): ISingleAccountPublicClientApplication {
        return PublicClientApplication.createSingleAccountPublicClientApplication(
            context.applicationContext,
            TeamsAuthStore.buildMsalConfigFile(context)
        )
    }

    /** Worker-thread only. Returns null when there is no currently signed-in school account. */
    fun acquireTokenSilently(context: Context): String? {
        if (!TeamsAuthStore.isConfigured(context)) return null
        val pca = createPca(context)
        val account = pca.currentAccount.currentAccount ?: return null

        val params = AcquireTokenSilentParameters.Builder()
            .withScopes(SCOPES)
            .forAccount(account)
            .fromAuthority(account.authority)
            .build()

        return pca.acquireTokenSilent(params).accessToken
    }
}
