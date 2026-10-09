/*  Copyright (C) 2026 UltimateGadget contributors

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.external.ultimatewidgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.RemoteViews;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.UltimateHomeActivity;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.DailyTotals;

/**
 * UltimateGadget home-screen widget. Shows today's steps, the watch battery and the last known
 * heart rate for the active device, in the Ultimate dark brand palette. Tapping anywhere opens
 * {@link UltimateHomeActivity}.
 *
 * All database/sample reads happen off the main thread (see {@link #renderAsync}); only the final
 * {@link AppWidgetManager#updateAppWidget} call touches the widget, and that call is thread-safe.
 *
 * Fully local: no network, no analytics. Lives in its own package, isolated from the GB core.
 */
public class UltimateStatsWidget extends AppWidgetProvider {

    private static final Logger LOG = LoggerFactory.getLogger(UltimateStatsWidget.class);

    /** Custom broadcast used by the local receiver to force a refresh. */
    public static final String ACTION_REFRESH =
            "nodomain.freeyourgadget.gadgetbridge.external.ultimatewidgets.REFRESH";

    private static BroadcastReceiver localReceiver = null;

    @Override
    public void onUpdate(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
        renderAsync(context.getApplicationContext(), appWidgetManager, appWidgetIds);
    }

    @Override
    public void onEnabled(final Context context) {
        super.onEnabled(context);
        if (localReceiver == null) {
            localReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(final Context ctx, final Intent intent) {
                    refreshAll(ctx.getApplicationContext());
                }
            };
            final IntentFilter filter = new IntentFilter();
            filter.addAction(GBApplication.ACTION_NEW_DATA);
            filter.addAction(GBDevice.ACTION_DEVICE_CHANGED);
            LocalBroadcastManager.getInstance(context.getApplicationContext())
                    .registerReceiver(localReceiver, filter);
            LOG.debug("UltimateStatsWidget local receiver registered");
        }
    }

    @Override
    public void onDisabled(final Context context) {
        super.onDisabled(context);
        if (localReceiver != null) {
            LocalBroadcastManager.getInstance(context.getApplicationContext())
                    .unregisterReceiver(localReceiver);
            localReceiver = null;
            LOG.debug("UltimateStatsWidget local receiver unregistered");
        }
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            refreshAll(context.getApplicationContext());
        }
    }

    /** Re-renders every active instance of this widget. */
    private void refreshAll(final Context appContext) {
        final AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        final ComponentName component = new ComponentName(appContext, UltimateStatsWidget.class);
        final int[] ids = manager.getAppWidgetIds(component);
        if (ids != null && ids.length > 0) {
            renderAsync(appContext, manager, ids);
        }
    }

    private void renderAsync(final Context appContext, final AppWidgetManager manager, final int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0) {
            return;
        }
        // DB and sample-provider access is blocking; keep it off the main thread.
        new Thread(() -> {
            final WidgetStats stats = loadStats(appContext);
            final RemoteViews views = buildViews(appContext, stats);
            for (final int appWidgetId : appWidgetIds) {
                manager.updateAppWidget(appWidgetId, views);
            }
        }, "UltimateStatsWidget-render").start();
    }

    private RemoteViews buildViews(final Context context, final WidgetStats stats) {
        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.ultimate_stats_widget);

        views.setTextViewText(R.id.ultimate_widget_device_name, stats.deviceName);
        views.setTextViewText(R.id.ultimate_widget_steps_value, stats.stepsText);
        views.setTextViewText(R.id.ultimate_widget_battery_value, stats.batteryText);
        views.setTextViewText(R.id.ultimate_widget_hr_value, stats.heartRateText);

        // Whole widget opens the Ultimate home screen.
        final Intent openIntent = new Intent(context, UltimateHomeActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final PendingIntent openPending = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.ultimate_widget_root, openPending);

        return views;
    }

    // ---- Data loading (background thread only) ---------------------------------------------

    private WidgetStats loadStats(final Context context) {
        final WidgetStats stats = new WidgetStats(context);
        final GBDevice device = pickDevice();
        if (device == null) {
            stats.deviceName = context.getString(R.string.ultimate_widget_no_device);
            return stats;
        }

        stats.deviceName = device.getAliasOrName() != null
                ? device.getAliasOrName()
                : (device.getName() != null ? device.getName() : device.getAddress());

        try {
            final Calendar today = GregorianCalendar.getInstance();
            final DailyTotals totals = DailyTotals.getDailyTotalsForDevice(device, today);
            stats.stepsText = String.valueOf((int) totals.getSteps());
        } catch (final Exception e) {
            LOG.warn("Failed to load steps for widget", e);
        }

        try {
            final int battery = device.getBatteryLevel(0);
            if (battery >= 0 && battery <= 100) {
                stats.batteryText = battery + "%";
            }
        } catch (final Exception e) {
            LOG.warn("Failed to read battery for widget", e);
        }

        try {
            final int hr = loadLastHeartRate(device);
            if (hr > 0) {
                stats.heartRateText = String.valueOf(hr);
            }
        } catch (final Exception e) {
            LOG.warn("Failed to load heart rate for widget", e);
        }

        return stats;
    }

    private GBDevice pickDevice() {
        final GBApplication app = GBApplication.app();
        if (app == null || app.getDeviceManager() == null) {
            return null;
        }
        final List<GBDevice> devices = app.getDeviceManager().getDevices();
        if (devices == null || devices.isEmpty()) {
            return null;
        }
        for (final GBDevice d : devices) {
            if (d.isInitialized()) {
                return d;
            }
        }
        return devices.get(0);
    }

    /** Last valid heart-rate sample over the past 6h, falling back to the resting-HR provider. */
    private int loadLastHeartRate(final GBDevice device) {
        try (DBHandler db = GBApplication.acquireDB()) {
            final DaoSession session = db.getDaoSession();
            final DeviceCoordinator coordinator = device.getDeviceCoordinator();
            if (coordinator == null) {
                return 0;
            }

            final long nowMillis = System.currentTimeMillis();
            final int nowSec = (int) (nowMillis / 1000L);
            final int fromSec = (int) ((nowMillis - 6L * 60 * 60 * 1000) / 1000L);

            final SampleProvider<? extends ActivitySample> provider =
                    coordinator.getSampleProvider(device, session);
            if (provider != null) {
                final List<? extends ActivitySample> samples =
                        provider.getAllActivitySamples(fromSec, nowSec);
                if (samples != null) {
                    for (int i = samples.size() - 1; i >= 0; i--) {
                        final int hr = samples.get(i).getHeartRate();
                        if (hr > 0 && hr <= 250) {
                            return hr;
                        }
                    }
                }
            }

            try {
                final nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider<? extends nodomain.freeyourgadget.gadgetbridge.model.HeartRateSample> resting =
                        coordinator.getHeartRateRestingSampleProvider(device, session);
                if (resting != null) {
                    final nodomain.freeyourgadget.gadgetbridge.model.HeartRateSample latest = resting.getLatestSample();
                    if (latest != null) {
                        final int hr = latest.getHeartRate();
                        if (hr > 0 && hr <= 250) {
                            return hr;
                        }
                    }
                }
            } catch (final Exception ignored) {
                // Not every coordinator exposes a resting-HR provider.
            }
        } catch (final Exception e) {
            LOG.warn("Heart-rate DB access failed for widget", e);
        }
        return 0;
    }

    /** Immutable snapshot of what the widget shows, with sensible "no data" defaults. */
    private static final class WidgetStats {
        String deviceName;
        String stepsText;
        String batteryText;
        String heartRateText;

        WidgetStats(final Context context) {
            final String dash = context.getString(R.string.ultimate_widget_no_value);
            this.deviceName = context.getString(R.string.ultimate_widget_no_device);
            this.stepsText = dash;
            this.batteryText = dash;
            this.heartRateText = dash;
        }
    }
}
