package com.zyprex.texttransfer;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.IBinder;
import android.widget.Toast;
//import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class MainService extends Service {
    public HttpServer server = new HttpServer(MainActivity.getIpAddress(), MainActivity.getPort());
    private final MyBinder mBinder = new MyBinder();

    class MyBinder extends Binder {
        public void startService(){
            try {
                server.start(10*60*1000);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Toast.makeText(MainService.this, "server start", Toast.LENGTH_SHORT).show();
        }
    }

    public MainService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return the communication channel to the service.
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // run in foreground
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);
        Notification notification = new NotificationCompat.Builder(this, "CHANNEL_ID")
                .setContentTitle(getString(R.string.app_name) + ": HTTP server")
                .setContentText(MainActivity.getIpAddressString())
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.mipmap.tt_512x512)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.tt_512x512))
                .setContentIntent(pi)
                .build();
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server.isAlive()) {
            server.stop();
            Toast.makeText(MainService.this, "server stop", Toast.LENGTH_SHORT).show();
        }
    }
}