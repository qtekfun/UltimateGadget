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
package nodomain.freeyourgadget.gadgetbridge.devices.huawei.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiConstants;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.p2p.HuaweiP2PMapkitService;

/**
 * Lists the offline maps currently stored on a Huawei watch (resolving the numeric map id to a
 * region/country name) and lets the user delete them, without going through the firmware install
 * screen. The map id -> name table comes from offvmp (Me7c7, MIT).
 */
public class HuaweiMapManagementActivity extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(HuaweiMapManagementActivity.class);

    private GBDevice device;
    private ListView listView;
    private TextView emptyView;
    private MapAdapter adapter;
    private JSONObject regions;

    private final List<MapItem> items = new ArrayList<>();

    private final BroadcastReceiver mapListReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final long[] ids = intent.getLongArrayExtra(HuaweiP2PMapkitService.EXTRA_MAP_IDS);
            final int[] types = intent.getIntArrayExtra(HuaweiP2PMapkitService.EXTRA_MAP_TYPES);
            final int[] versions = intent.getIntArrayExtra(HuaweiP2PMapkitService.EXTRA_MAP_VERSIONS);
            items.clear();
            if (ids != null && types != null && versions != null) {
                for (int i = 0; i < ids.length; i++) {
                    items.add(new MapItem(ids[i], (byte) types[i], versions[i]));
                }
            }
            runOnUiThread(() -> {
                adapter.notifyDataSetChanged();
                emptyView.setText(items.isEmpty()
                        ? getString(R.string.huawei_offline_maps_none)
                        : "");
            });
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        device = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        setTitle(R.string.huawei_offline_maps_title);

        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        final int pad = Math.round(getResources().getDisplayMetrics().density * 12);
        root.setPadding(pad, pad, pad, pad);

        emptyView = new TextView(this);
        emptyView.setText(R.string.huawei_offline_maps_loading);
        emptyView.setPadding(0, pad, 0, pad);
        root.addView(emptyView);

        listView = new ListView(this);
        adapter = new MapAdapter(this);
        listView.setAdapter(adapter);
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        regions = loadRegions();

        LocalBroadcastManager.getInstance(this).registerReceiver(
                mapListReceiver, new IntentFilter(HuaweiP2PMapkitService.ACTION_MAP_LIST));

        requestMaps();
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mapListReceiver);
        super.onDestroy();
    }

    private void requestMaps() {
        if (device == null || !device.isInitialized()) {
            emptyView.setText(R.string.huawei_offline_maps_not_connected);
            return;
        }
        emptyView.setText(R.string.huawei_offline_maps_loading);
        GBApplication.deviceService(device).onSendConfiguration(HuaweiConstants.PREF_HUAWEI_OFFLINE_MAP_QUERY);
    }

    private JSONObject loadRegions() {
        try (InputStream is = getAssets().open("offline_map_regions.json")) {
            byte[] buf = new byte[is.available()];
            int read = is.read(buf);
            JSONObject root = new JSONObject(new String(buf, 0, Math.max(read, 0), StandardCharsets.UTF_8));
            return root.optJSONObject("regions");
        } catch (Exception e) {
            LOG.error("Could not load offline_map_regions.json", e);
            return null;
        }
    }

    private String regionName(long mapId) {
        if (regions != null) {
            final JSONObject r = regions.optJSONObject(Long.toString(mapId));
            if (r != null) {
                final String region = r.optString("region", "");
                final String country = r.optString("country", "");
                if (!region.isEmpty() && !country.isEmpty() && !region.equals(country)) {
                    return region + ", " + country;
                }
                if (!region.isEmpty()) {
                    return region;
                }
            }
        }
        return getString(R.string.huawei_offline_maps_unknown, Long.toString(mapId));
    }

    private String typeName(byte mapType) {
        switch (mapType) {
            case 1:
                return getString(R.string.huawei_offline_maps_type_contour);
            case 2:
                return getString(R.string.huawei_offline_maps_type_global);
            default:
                return getString(R.string.huawei_offline_maps_type_map);
        }
    }

    private void confirmDelete(final MapItem item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.huawei_offline_maps_delete_title)
                .setMessage(getString(R.string.huawei_offline_maps_delete_confirm, regionName(item.mapId)))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    GBApplication.deviceService(device).onSendConfiguration(
                            HuaweiConstants.PREF_HUAWEI_OFFLINE_MAP_DELETE_PREFIX + item.mapId + ":" + item.mapType);
                    Toast.makeText(this, R.string.huawei_offline_maps_deleting, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static class MapItem {
        final long mapId;
        final byte mapType;
        final int version;

        MapItem(long mapId, byte mapType, int version) {
            this.mapId = mapId;
            this.mapType = mapType;
            this.version = version;
        }
    }

    private class MapAdapter extends ArrayAdapter<MapItem> {
        MapAdapter(Context context) {
            super(context, 0, items);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            final MapItem item = items.get(position);
            final LinearLayout row = new LinearLayout(HuaweiMapManagementActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            final int pad = Math.round(getResources().getDisplayMetrics().density * 8);
            row.setPadding(0, pad, 0, pad);

            final LinearLayout texts = new LinearLayout(HuaweiMapManagementActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);

            final TextView title = new TextView(HuaweiMapManagementActivity.this);
            title.setTextAppearance(android.R.style.TextAppearance_Medium);
            title.setText(regionName(item.mapId));

            final TextView sub = new TextView(HuaweiMapManagementActivity.this);
            sub.setText(typeName(item.mapType) + " · v" + item.version);

            texts.addView(title);
            texts.addView(sub);
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final Button del = new Button(HuaweiMapManagementActivity.this);
            del.setText(R.string.huawei_offline_maps_delete);
            del.setOnClickListener(v -> confirmDelete(item));
            row.addView(del);

            return row;
        }
    }
}
