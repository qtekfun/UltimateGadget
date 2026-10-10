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
package nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.p2p;

import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.HuaweiP2PManager;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.HuaweiUploadManager;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.requests.SendFileUploadInfo;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

/**
 * P2P channel to the golf mini-app of the watch, so golf course maps can be listed, added and
 * deleted without Huawei Health.
 *
 * Huawei Health talks to the watch golf app through the P2P service (0x34), addressing it by
 * package name. Sport watches (e.g. Watch GT Runner 2) use "com.huawei.health.wear.golf"; the
 * source module on the phone side is "hw.unitedevice.golf". All message formats here were
 * reconstructed by static analysis of the Huawei Health APK (own phone, own account), never from
 * captured Huawei server traffic.
 */
public class HuaweiP2PGolfService extends HuaweiBaseP2PService {
    private final Logger LOG = LoggerFactory.getLogger(HuaweiP2PGolfService.class);

    public static final String MODULE = "hw.unitedevice.golf";

    public static final String PACKAGE_SPORT_WATCH = "com.huawei.health.wear.golf";
    public static final String PACKAGE_HARMONY_WATCH = "com.huawei.sport.workout";

    private String currentPackage = PACKAGE_SPORT_WATCH;

    public HuaweiP2PGolfService(HuaweiP2PManager manager) {
        super(manager);
    }

    @Override
    public String getModule() {
        return MODULE;
    }

    @Override
    public String getPackage() {
        return currentPackage;
    }

    @Override
    public String getFingerprint() {
        return "SystemApp";
    }

    @Override
    public void registered() {
        LOG.info("HuaweiP2PGolfService registered");
        currentPackage = PACKAGE_SPORT_WATCH;
    }

    // Golf business messages: 36 byte header (7 little-endian ints + 8 reserved bytes) + payload.
    private static final int HEADER_SIZE = 36;
    private static final int TYPE_LOCAL_COURSE_LIST = 16;

    private static byte[] buildMessage(int type, int msgId, byte[] payload) {
        final ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(type);
        buf.putInt(1); // version
        buf.putInt(HEADER_SIZE + payload.length); // total length
        buf.putInt(0); // business header length
        buf.putInt(msgId);
        buf.putInt(0); // response state
        buf.putInt(0); // map style
        buf.position(HEADER_SIZE);
        buf.put(payload);
        return buf.array();
    }

    /** Read-only: asks the watch which courses it has downloaded. */
    private void requestLocalCourseList(final Runnable next) {
        currentPackage = PACKAGE_SPORT_WATCH;
        final byte[] msg = buildMessage(TYPE_LOCAL_COURSE_LIST, 1, new byte[]{1, 0, 0, 0});
        LOG.info("Golf probe: sending local course list request ({} bytes)", msg.length);
        sendCommand(msg, (code, data) -> {
            LOG.info("Golf probe: course list request ack code {} data {}", code,
                    data == null ? "null" : StringUtils.bytesToHex(data));
            if (next != null) {
                next.run();
            }
        });
    }

    // Records in the watch's LOCAL_COURSE_LIST reply: courseId(4) + version(4) + flag(4) + 8 reserved.
    private static final int COURSE_LIST_RECORD_SIZE = 20;

    @Override
    public void unregister() {
    }

    // GOLF_GPS_INFO_SEND: business type 8. This is DEVICE-INITIATED: the watch sends it (with its
    // own msgId) when the user opens "find course near me" on the watch golf app, carrying its GPS
    // position. Huawei Health replies with a GOLF_COURSE_LIST_FILE (type 12) using the SAME msgId,
    // containing nearby courses. Our earlier unsolicited type-12 push (Proof of concept 2) used an
    // arbitrary msgId the watch was not waiting for, which is the likely reason it was ignored.
    // Payload layout (sport watch, i.e. watchType 0): 4 reserved/unknown bytes, then latitude
    // (double, 8 bytes LE), then longitude (double, 8 bytes LE). Reconstructed from Huawei Health's
    // GolfDataReceiverFactory/GolfDeviceProxy via static analysis of the APK, not from captured
    // Huawei server traffic.
    private static final int TYPE_GPS_INFO_SEND = 8;

    @Override
    public void handleData(byte[] data) {
        if (data == null || data.length < HEADER_SIZE) {
            LOG.info("HuaweiP2PGolfService handleData: {} bytes (too short for a header)", data == null ? 0 : data.length);
            return;
        }
        final ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        final int type = buf.getInt();
        buf.getInt(); // version
        buf.getInt(); // total length
        buf.getInt(); // business head length
        final int msgId = buf.getInt();
        buf.getInt(); // response state
        buf.getInt(); // map style
        LOG.info("HuaweiP2PGolfService handleData: type {} msgId {} ({} bytes)", type, msgId, data.length);
        LOG.info("HuaweiP2PGolfService payload: {}", StringUtils.bytesToHex(java.util.Arrays.copyOfRange(data, HEADER_SIZE, Math.min(data.length, HEADER_SIZE + 64))));

        if (type == TYPE_LOCAL_COURSE_LIST && data.length >= HEADER_SIZE + 4) {
            final int courseCount = ByteBuffer.wrap(data, HEADER_SIZE, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            // Each record is 20 bytes: courseId(4 LE) + version(4) + flag(4) + 8 reserved.
            final StringBuilder ids = new StringBuilder();
            int off = HEADER_SIZE + 4;
            int n = 0;
            while (off + 4 <= data.length) {
                final int cid = ByteBuffer.wrap(data, off, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                final int ver = (off + 8 <= data.length) ? ByteBuffer.wrap(data, off + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() : 0;
                if (ids.length() > 0) ids.append(", ");
                ids.append(cid).append("(v").append(ver).append(")");
                if (collectingList) listCollector.put(cid, ver);
                off += COURSE_LIST_RECORD_SIZE;
                n++;
            }
            LOG.info("Golf: watch reports {} downloaded course(s); this frame has {} ids: {}", courseCount, n, ids);
            // The list arrives across frames; broadcast once we've gathered them all.
            if (collectingList && listCollector.size() >= courseCount) {
                collectingList = false;
                broadcastCourseList();
            }
        } else if (type == TYPE_GPS_INFO_SEND && data.length >= HEADER_SIZE + 20) {
            final ByteBuffer payload = ByteBuffer.wrap(data, HEADER_SIZE, data.length - HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            payload.getInt(); // reserved (sport watch convention)
            final double lat = payload.getDouble();
            final double lon = payload.getDouble();
            LOG.info("Golf: watch asked for courses near lat={} lon={} msgId={} (not handled)", lat, lon, msgId);
        }
    }

    // Golf course push protocol (from Huawei Health's GolfHiWearBusinessType / GolfCourseMapInfo,
    // static analysis of the APK). Adding a course is: PUSH_SHAKE (14, payload courseId+version,
    // mapStyle in the header) -> upload the map file -> register it (COURSE_LIST_FILE 12).
    private static final int TYPE_COURSE_PUSH_SHAKE = 14;
    private static final int TYPE_COURSE_FILE = 11;
    private static final int TYPE_COURSE_DELETE = 15;
    private static final int MAP_STYLE_VECTOR = 1;
    private static final int PUSH_MSG_ID = 100;
    // Fresh message id per request, like Huawei Health's GolfMsgHeader.newMsgId() (a fixed id can be
    // de-duplicated by the watch, which is likely why repeated deletes appeared to do nothing).
    private static final java.util.concurrent.atomic.AtomicInteger MSG_ID = new java.util.concurrent.atomic.AtomicInteger(1000);
    private static int nextMsgId() {
        return MSG_ID.getAndIncrement();
    }

    /** Delete courses from the watch (type 15): count header + N x courseId (LE). */
    public void deleteCourses(int[] courseIds) {
        currentPackage = PACKAGE_SPORT_WATCH;
        final ByteBuffer payload = ByteBuffer.allocate(4 + courseIds.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(courseIds.length); // GolfNumberHeader
        for (int id : courseIds) payload.putInt(id);
        final byte[] msg = buildGolfMessage(TYPE_COURSE_DELETE, nextMsgId(), 0, 4, payload.array());
        LOG.info("Golf: DELETE courses {} ({} bytes)", java.util.Arrays.toString(courseIds), msg.length);
        sendCommand(msg, (code, data) -> {
            LOG.info("Golf: delete ack code {} data {}", code, data == null ? "null" : StringUtils.bytesToHex(data));
            requestLocalCourseList(null);
        });
    }

    // ---- Public API for the UltimateGadget golf course UI ----

    public static final String ACTION_GOLF_COURSE_LIST = "nodomain.freeyourgadget.gadgetbridge.huawei.GOLF_COURSE_LIST";
    public static final String EXTRA_GOLF_COURSE_IDS = "golf_course_ids";
    public static final String EXTRA_GOLF_COURSE_VERSIONS = "golf_course_versions";

    // Aggregates the watch's course-list reply (delivered across several frames).
    private final java.util.LinkedHashMap<Integer, Integer> listCollector = new java.util.LinkedHashMap<>();
    private boolean collectingList = false;

    /** Ask the watch for its downloaded courses; the aggregated list is sent via ACTION_GOLF_COURSE_LIST. */
    public void requestCourseListAndBroadcast() {
        currentPackage = PACKAGE_SPORT_WATCH;
        listCollector.clear();
        collectingList = true;
        final byte[] msg = buildMessage(TYPE_LOCAL_COURSE_LIST, 1, new byte[]{1, 0, 0, 0});
        LOG.info("Golf: requesting course list for UI");
        sendCommand(msg, (code, data) -> LOG.info("Golf: course list request ack {}", code));
    }

    /** Read a course .bin from a local file and send it to the watch (shake + wrapped type-11 file). */
    public void sendCourseMapFromFile(String path, int courseId, int version) {
        try {
            final File f = new File(path);
            final byte[] data = new byte[(int) f.length()];
            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) { raf.readFully(data); }
            LOG.info("Golf: sending course {} v{} from {} ({} bytes)", courseId, version, path, data.length);
            sendCourseMap(courseId, version, data);
        } catch (Exception e) {
            LOG.error("Golf: sendCourseMapFromFile failed", e);
        }
    }

    private void broadcastCourseList() {
        final int[] ids = new int[listCollector.size()];
        final int[] versions = new int[listCollector.size()];
        int i = 0;
        for (java.util.Map.Entry<Integer, Integer> e : listCollector.entrySet()) {
            ids[i] = e.getKey();
            versions[i] = e.getValue();
            i++;
        }
        final Intent intent = new Intent(ACTION_GOLF_COURSE_LIST);
        intent.putExtra(EXTRA_GOLF_COURSE_IDS, ids);
        intent.putExtra(EXTRA_GOLF_COURSE_VERSIONS, versions);
        LocalBroadcastManager.getInstance(manager.getSupportProvider().getContext()).sendBroadcast(intent);
        LOG.info("Golf: broadcast course list ({} courses)", ids.length);
    }

    /**
     * Add flow (from HH's GolfDeviceProxy): PUSH_SHAKE(14) carrying courseId + the file's msgId,
     * then send the map as a GOLF_COURSE_FILE(11) whose CONTENT is a wrapped blob:
     *   [36B golf header type11, bizHeadLen=8, dataLen=binLen, mapStyle=1, msgId] + [courseId+version] + [raw .bin]
     * uploaded as "<courseId>.bin". The wrapped type-11 file both delivers and registers the course
     * (no separate type-12). Vector maps need no key.
     */
    private void sendCourseMap(int courseId, int version, byte[] data) {
        sendPushShake(courseId, PUSH_MSG_ID, () -> uploadCourseFile(courseId, version, data));
    }

    /** Announce the incoming course: type 14, payload = courseId + the upcoming file's msgId. */
    private void sendPushShake(int courseId, int fileMsgId, Runnable next) {
        currentPackage = PACKAGE_SPORT_WATCH;
        final ByteBuffer payload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(courseId);
        payload.putInt(fileMsgId);
        final byte[] msg = buildGolfMessage(TYPE_COURSE_PUSH_SHAKE, fileMsgId, 0, 0, payload.array());
        LOG.info("Golf: PUSH_SHAKE course {} fileMsgId {} ({} bytes)", courseId, fileMsgId, msg.length);
        sendCommand(msg, (code, dataResp) -> {
            LOG.info("Golf: push shake ack code {} data {}", code, dataResp == null ? "null" : StringUtils.bytesToHex(dataResp));
            if (next != null) next.run();
        });
    }

    /** Upload the wrapped type-11 course file as "<courseId>.bin". */
    private void uploadCourseFile(int courseId, int version, byte[] data) {
        currentPackage = PACKAGE_SPORT_WATCH;
        // business head = courseId(4) + version(4); payload = businessHead + raw map bytes
        final ByteBuffer payload = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(courseId);
        payload.putInt(version);
        payload.put(data);
        final byte[] wrapped = buildGolfMessage(TYPE_COURSE_FILE, PUSH_MSG_ID, MAP_STYLE_VECTOR, 8, payload.array());

        final HuaweiUploadManager.FileUploadInfo fileInfo = new HuaweiUploadManager.FileUploadInfo();
        fileInfo.setFileType((byte) 7);
        fileInfo.setFileName(courseId + ".bin");
        fileInfo.setUploadData(new HuaweiUploadManager.UploadDataBuffer(wrapped));
        fileInfo.setSrcPackage(getModule());
        fileInfo.setDstPackage(getPackage());
        fileInfo.setSrcFingerprint(getLocalFingerprint());
        fileInfo.setDstFingerprint(getFingerprint());
        fileInfo.setFileUploadCallback(new HuaweiUploadManager.FileUploadCallback() {
            @Override public void onUploadStart() { LOG.info("Golf course file upload: start ({} bytes wrapped)", wrapped.length); }
            @Override public void onUploadProgress(int progress) { LOG.info("Golf course file upload: progress {}", progress); }
            @Override public void onUploadComplete() {
                LOG.info("Golf course file upload: COMPLETE for course {}", courseId);
                // Re-query AND broadcast, so the UI list refreshes exactly when the course is
                // actually registered on the watch (the upload takes a few seconds).
                requestCourseListAndBroadcast();
            }
            @Override public void onError(int code) { LOG.info("Golf course file upload: ERROR {}", code); }
        });
        final HuaweiUploadManager up = manager.getSupportProvider().getUploadManager();
        up.setFileUploadInfo(fileInfo);
        try {
            new SendFileUploadInfo(manager.getSupportProvider(), up).doPerform();
        } catch (IOException e) {
            LOG.error("Golf course file upload: send failed", e);
        }
    }

    /** Build a golf P2P message with an explicit mapStyle and business-head length. */
    private static byte[] buildGolfMessage(int type, int msgId, int mapStyle, int businessHeadLength, byte[] payload) {
        final ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(type);
        buf.putInt(1); // version
        buf.putInt(HEADER_SIZE + payload.length); // total length
        buf.putInt(businessHeadLength);
        buf.putInt(msgId);
        buf.putInt(0); // response state
        buf.putInt(mapStyle);
        buf.position(HEADER_SIZE);
        buf.put(payload);
        return buf.array();
    }

    public static HuaweiP2PGolfService getRegisteredInstance(HuaweiP2PManager manager) {
        return (HuaweiP2PGolfService) manager.getRegisteredService(HuaweiP2PGolfService.MODULE);
    }
}
