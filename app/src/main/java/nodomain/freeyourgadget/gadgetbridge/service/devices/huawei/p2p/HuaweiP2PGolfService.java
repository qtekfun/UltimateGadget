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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.HuaweiP2PManager;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

/**
 * Phase 0 spike: P2P channel to the golf mini-app of the watch.
 *
 * Huawei Health talks to the watch golf app through the P2P service (0x34), addressing it by
 * package name. Sport watches use "com.huawei.health.wear.golf" and Harmony watches use
 * "com.huawei.sport.workout". The source module on the phone side is "hw.unitedevice.golf".
 *
 * For now this only pings both candidate packages and logs the answers, so we can see which one
 * the watch accepts. No golf message is sent yet.
 */
public class HuaweiP2PGolfService extends HuaweiBaseP2PService {
    private final Logger LOG = LoggerFactory.getLogger(HuaweiP2PGolfService.class);

    public static final String MODULE = "hw.unitedevice.golf";

    public static final String PACKAGE_SPORT_WATCH = "com.huawei.health.wear.golf";
    public static final String PACKAGE_HARMONY_WATCH = "com.huawei.sport.workout";

    // Control: a package that does not exist, to tell what a negative answer looks like.
    private static final String PACKAGE_CONTROL = "com.qtekfun.nonexistent.probe";

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
        // Only the sport watch package is system signed; the Harmony one has a signed fingerprint
        // that is not needed for ping.
        return "SystemApp";
    }

    @Override
    public void registered() {
        LOG.info("HuaweiP2PGolfService registered, probing golf packages");
        probe(PACKAGE_SPORT_WATCH, () -> probe(PACKAGE_HARMONY_WATCH, () -> probe(PACKAGE_CONTROL, () ->
                requestLocalCourseList(this::sendTestCourseListFile))));
    }

    private void probe(final String pkg, final Runnable next) {
        currentPackage = pkg;
        LOG.info("Golf probe: ping {}", pkg);
        sendPing((code, data) -> {
            LOG.info("Golf probe: ping {} -> code {} data {}", pkg, code,
                    data == null ? "null" : StringUtils.bytesToHex(data));
            if (next != null) {
                next.run();
            }
        });
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

    // GOLF_COURSE_LIST_FILE: business type 12. Payload is a 4 byte course count (little-endian)
    // followed by N * 92 byte GolfCourseInfo records (80 byte UTF-8 name, zero padded; 4 byte
    // courseId LE; 4 byte version LE; 4 byte distance-in-meters LE). Format reconstructed from
    // Huawei Health's GolfCourseInfo.toBytes()/GolfNumberHeader via static analysis of the APK
    // (own phone, own account), not captured from any Huawei server traffic.
    private static final int TYPE_COURSE_LIST_FILE = 12;
    private static final int COURSE_RECORD_SIZE = 92;
    private static final int COURSE_NAME_MAX_BYTES = 80;

    private static byte[] buildCourseRecord(String name, int courseId, int version, int distanceMeters) {
        final ByteBuffer rec = ByteBuffer.allocate(COURSE_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        rec.put(nameBytes, 0, Math.min(nameBytes.length, COURSE_NAME_MAX_BYTES));
        rec.position(COURSE_NAME_MAX_BYTES);
        rec.putInt(courseId);
        rec.putInt(version);
        rec.putInt(distanceMeters);
        return rec.array();
    }

    /**
     * Phase 0 spike: push a single fabricated course record (no real map data) to see whether the
     * watch accepts the container format at all. This does not contain any Huawei course data.
     */
    private void sendTestCourseListFile() {
        currentPackage = PACKAGE_SPORT_WATCH;
        final byte[] record = buildCourseRecord("UltimateGadget Test", 900100001, 1, 0);
        final ByteBuffer businessHead = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(1);
        final ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE + 4 + record.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(TYPE_COURSE_LIST_FILE);
        buf.putInt(1); // version
        buf.putInt(buf.capacity()); // total length
        buf.putInt(4); // business head length (course count field)
        buf.putInt(2); // msgId
        buf.putInt(0); // response state
        buf.putInt(0); // map style
        buf.position(HEADER_SIZE);
        buf.put(businessHead.array());
        buf.put(record);
        final byte[] msg = buf.array();
        LOG.info("Golf probe: sending test course list file ({} bytes): {}", msg.length, StringUtils.bytesToHex(msg));
        sendCommand(msg, (code, data) -> LOG.info("Golf probe: test course list file ack code {} data {}", code,
                data == null ? "null" : StringUtils.bytesToHex(data)));
    }

    @Override
    public void unregister() {
    }

    @Override
    public void handleData(byte[] data) {
        if (data == null || data.length < HEADER_SIZE) {
            LOG.info("HuaweiP2PGolfService handleData: {} bytes (too short for a header)", data == null ? 0 : data.length);
            return;
        }
        final ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        LOG.info("HuaweiP2PGolfService handleData: type {} version {} total {} headLen {} msgId {} rsp {} mapStyle {} ({} bytes)",
                buf.getInt(), buf.getInt(), buf.getInt(), buf.getInt(), buf.getInt(), buf.getInt(), buf.getInt(), data.length);
        LOG.info("HuaweiP2PGolfService payload: {}", StringUtils.bytesToHex(java.util.Arrays.copyOfRange(data, HEADER_SIZE, Math.min(data.length, HEADER_SIZE + 64))));
    }

    public static HuaweiP2PGolfService getRegisteredInstance(HuaweiP2PManager manager) {
        return (HuaweiP2PGolfService) manager.getRegisteredService(HuaweiP2PGolfService.MODULE);
    }
}
