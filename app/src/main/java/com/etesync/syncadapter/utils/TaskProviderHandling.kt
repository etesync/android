package com.etesync.syncadapter.utils

import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import at.bitfire.ical4android.TaskProvider
import com.etesync.syncadapter.AccountSettings
import com.etesync.syncadapter.App
import com.etesync.syncadapter.log.Logger
import com.etesync.syncadapter.resource.LocalTaskList
import org.jetbrains.anko.defaultSharedPreferences

class TaskProviderHandling {
    companion object {
        fun getWantedTaskSyncProvider(context: Context): TaskProvider.ProviderName? {
            val openTasksAvailable = LocalTaskList.tasksProviderAvailable(context, TaskProvider.ProviderName.OpenTasks)
            val tasksOrgAvailable = LocalTaskList.tasksProviderAvailable(context, TaskProvider.ProviderName.TasksOrg)

            if (openTasksAvailable && tasksOrgAvailable) {
                if (context.defaultSharedPreferences.getBoolean(App.PREFER_TASKSORG, false))
                    return TaskProvider.ProviderName.TasksOrg
                else
                    return TaskProvider.ProviderName.OpenTasks
            } else {
                if (openTasksAvailable)
                    return TaskProvider.ProviderName.OpenTasks
                else if (tasksOrgAvailable)
                    return TaskProvider.ProviderName.TasksOrg
                else
                    return null
            }
        }

        fun updateTaskSync(context: Context, provider: TaskProvider.ProviderName) {
            for (account in AccountManager.get(context).getAccountsByType(App.accountType)) {
                val settings = AccountSettings(context, account)
                val calendarSyncInterval = settings.getSyncInterval(CalendarContract.AUTHORITY)
                val wantedProvider = getWantedTaskSyncProvider(context)
                val shouldSync = wantedProvider == provider

                Logger.log.info("Package (un)installed; Syncing (${shouldSync}) for ${provider.name}")
                if (shouldSync) {
                    if (calendarSyncInterval == null) {
                        // do nothing atm
                    } else if (ContentResolver.getIsSyncable(account, provider.authority) <= 0) {
                        ContentResolver.setIsSyncable(account, provider.authority, 1)
                        settings.setSyncInterval(provider.authority, calendarSyncInterval)
                    }
                } else {
                    ContentResolver.setIsSyncable(account, provider.authority, 0)
                }
            }
        }
    }
}