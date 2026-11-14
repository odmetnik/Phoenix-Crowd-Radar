package hr.phoenixart.crowdradar;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import hr.phoenixart.crowdradar.core.Presence;
import java.security.SecureRandom;
import java.util.*;

/** Foreground-only group presence. No GPS, personal names, or internet traffic. */
@SuppressLint("MissingPermission")
final class BlePresence {
    interface Listener { void peer(int id, int rssi, BluetoothDevice device); void status(String message); }
    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothLeAdvertiser advertiser;
    private BluetoothLeScanner scanner;
    private ParcelUuid service;
    private byte[] secret;
    private int sessionId;
    private boolean running;
    private AdvertiseCallback advertising;
    BlePresence(Context context, Listener listener) { this.context = context; this.listener = listener; }
    private final ScanCallback scanning = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            byte[] activeKey = secret;
            if (!running || activeKey == null || result.getScanRecord() == null) return;
            Integer id = Presence.verify(activeKey, result.getScanRecord().getServiceData(service), System.currentTimeMillis());
            if (id != null && id != sessionId) handler.post(() -> { if (running) listener.peer(id, result.getRssi(), result.getDevice()); });
        }
        @Override public void onScanFailed(int code) { handler.post(() -> { stop(); listener.status("BLE skeniranje nije uspjelo: " + code); }); }
    };
    void start(byte[] key) {
        stop();
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        try {
            if (adapter == null || !adapter.isEnabled()) { listener.status("Uključi Bluetooth u postavkama telefona."); return; }
            advertiser = adapter.getBluetoothLeAdvertiser(); scanner = adapter.getBluetoothLeScanner();
            if (advertiser == null || scanner == null) { listener.status("Telefon ne podržava potrebno BLE oglašavanje."); return; }
            secret = key.clone(); service = new ParcelUuid(Presence.service(key)); sessionId = new SecureRandom().nextInt(); running = true;
            scanner.startScan(Collections.singletonList(new ScanFilter.Builder().setServiceUuid(service).build()),
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanning);
            advertise();
            listener.status("Tražim članove grupe • samo blizina");
            handler.postDelayed(refresh, 30000);
        } catch (RuntimeException e) { stop(); listener.status("BLE nije dostupan. Provjeri dozvole i Bluetooth."); }
    }
    private void advertise() {
        AdvertiseCallback current = new AdvertiseCallback() {
            @Override public void onStartFailure(int errorCode) {
                handler.post(() -> { if (advertising == this) { stop(); listener.status("BLE oglašavanje nije uspjelo: " + errorCode); } });
            }
        };
        advertising = current;
        // Primary: flags + 128-bit service UUID. Scan response: 128-bit service + 12-byte authenticated presence = 30 bytes.
        advertiser.startAdvertising(new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).setConnectable(true).build(),
            new AdvertiseData.Builder().addServiceUuid(service).build(),
            new AdvertiseData.Builder().addServiceData(service, Presence.packet(secret, sessionId, System.currentTimeMillis())).build(), current);
    }
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try { advertiser.stopAdvertising(advertising); advertise(); handler.postDelayed(this, 30000); }
            catch (RuntimeException e) { stop(); listener.status("BLE prekinut. Ponovno pokreni pretragu."); }
        }
    };
    void stop() {
        running = false; handler.removeCallbacks(refresh);
        try { if (scanner != null) scanner.stopScan(scanning); } catch (RuntimeException ignored) {}
        try { if (advertiser != null && advertising != null) advertiser.stopAdvertising(advertising); } catch (RuntimeException ignored) {}
        if (secret != null) Arrays.fill(secret, (byte)0);
        secret = null; scanner = null; advertiser = null; advertising = null;
    }
    int sessionId() { return sessionId; }
}
