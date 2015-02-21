/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * *****************************************************************************
 * Copyright (c) 2012-2015. NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.maplib.datasource.ngw;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.SettingsConstants;

import static com.nextgis.maplib.util.Constants.NGW_ACCOUNT_TYPE;
import static com.nextgis.maplib.util.Constants.TAG;
/*
https://udinic.wordpress.com/2013/07/24/write-your-own-android-sync-adapter/#more-507
http://www.fussylogic.co.uk/blog/?p=1031
http://www.fussylogic.co.uk/blog/?p=1035
http://www.fussylogic.co.uk/blog/?p=1037
http://developer.android.com/training/sync-adapters/creating-sync-adapter.html
https://github.com/elegion/ghsync
http://habrahabr.ru/company/e-Legion/blog/206210/
http://habrahabr.ru/company/e-Legion/blog/216857/
http://stackoverflow.com/questions/5486228/how-do-we-control-an-android-sync-adapter-preference
https://books.google.ru/books?id=SXlMAQAAQBAJ&pg=PA158&lpg=PA158&dq=android:syncAdapterSettingsAction&source=bl&ots=T832S7VvKb&sig=vgNNDHfwyMzvINeHfdfDhu9tREs&hl=ru&sa=X&ei=YviqVIPMF9DgaPOUgOgP&ved=0CFUQ6AEwBw#v=onepage&q=android%3AsyncAdapterSettingsAction&f=false

 */

public class SyncAdapter
        extends AbstractThreadedSyncAdapter
{
    public static final String SYNC_START    = "com.nextgis.maplib.sync_start";
    public static final String SYNC_FINISH   = "com.nextgis.maplib.sync_finish";
    public static final String SYNC_CANCELED = "com.nextgis.maplib.sync_canceled";
    public static final String SYNC_CHANGES  = "com.nextgis.maplib.sync_changes";


    public SyncAdapter(
            Context context,
            boolean autoInitialize)
    {
        super(context, autoInitialize);
    }


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public SyncAdapter(
            Context context,
            boolean autoInitialize,
            boolean allowParallelSyncs)
    {
        super(context, autoInitialize, allowParallelSyncs);
    }


    @Override
    public void onPerformSync(
            Account account,
            Bundle bundle,
            String authority,
            ContentProviderClient contentProviderClient,
            SyncResult syncResult)
    {
        Log.d(TAG, "onPerformSync");

        getContext().sendBroadcast(new Intent(SYNC_START));

        IGISApplication application = (IGISApplication) getContext();
        MapContentProviderHelper mapContentProviderHelper =
                (MapContentProviderHelper) application.getMap();
        if (null != mapContentProviderHelper) {
            sync(mapContentProviderHelper, authority, syncResult);
        }

        if (!isCanceled()) {
            SharedPreferences settings = getContext().getSharedPreferences(
                    Constants.PREFERENCES, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
            SharedPreferences.Editor editor = settings.edit();
            editor.putLong(SettingsConstants.KEY_PREF_LAST_SYNC_TIMESTAMP, System.currentTimeMillis());
            editor.commit();
        }

        getContext().sendBroadcast(new Intent(isCanceled() ? SYNC_CANCELED : SYNC_FINISH));
    }


    protected void sync(
            LayerGroup layerGroup,
            String authority,
            SyncResult syncResult)
    {
        for (int i = 0; i < layerGroup.getLayerCount(); i++) {
            if (isCanceled()) {
                return;
            }
            ILayer layer = layerGroup.getLayer(i);
            if (layer instanceof LayerGroup) {
                sync((LayerGroup) layer, authority, syncResult);
            } else if (layer instanceof NGWVectorLayer) {
                NGWVectorLayer ngwVectorLayer = (NGWVectorLayer) layer;
                ngwVectorLayer.sync(this, authority, syncResult);
            }
        }
    }


    public static void setSyncPeriod(
            IGISApplication application,
            Bundle extras,
            long pollFrequency)
    {
        final AccountManager accountManager = AccountManager.get((Context) application);
        for (Account account : accountManager.getAccountsByType(NGW_ACCOUNT_TYPE)) {
            ContentResolver.addPeriodicSync(
                    account, application.getAuthority(), extras, pollFrequency);
        }
    }


    public boolean isCanceled()
    {
        return Thread.currentThread().isInterrupted();
    }


    protected void onCanceled()
    {
        // we try do this before thread is killed
        getContext().sendBroadcast(new Intent(SYNC_CANCELED));
    }


    // This will only be invoked when the SyncAdapter indicates that it doesn't support parallel syncs.
    @Override
    public void onSyncCanceled()
    {
        onCanceled();
        super.onSyncCanceled();
    }


    // This will only be invoked when the SyncAdapter indicates that it does support parallel syncs.
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onSyncCanceled(Thread thread)
    {
        onCanceled();
        super.onSyncCanceled(thread);
    }
}
