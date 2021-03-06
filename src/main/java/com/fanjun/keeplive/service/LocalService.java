package com.fanjun.keeplive.service;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import com.fanjun.keeplive.KeepLive;
import com.fanjun.keeplive.R;
import com.fanjun.keeplive.config.NotificationUtils;
import com.fanjun.keeplive.receiver.NotificationClickReceiver;
import com.fanjun.keeplive.receiver.OnepxReceiver;
@SuppressWarnings(value={"unchecked", "deprecation"})
public final class LocalService extends Service {
    private OnepxReceiver mOnepxReceiver;
    private ScreenStateReceiver screenStateReceiver;
    private boolean isPause = true;//控制暂停
    private MediaPlayer mediaPlayer;
    private MyBilder mBilder;
    @Override
    public void onCreate() {
        super.onCreate();
        if (mBilder == null) {
            mBilder = new MyBilder();
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        isPause = pm.isScreenOn();
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBilder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //播放无声音乐
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.novioce);
            mediaPlayer.setVolume(0f, 0f);
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    if (!isPause) {
                        play();
                    }
                }
            });
            play();
        }
        //像素保活
        if (mOnepxReceiver == null) {
            mOnepxReceiver = new OnepxReceiver();
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.SCREEN_OFF");
        intentFilter.addAction("android.intent.action.SCREEN_ON");
        registerReceiver(mOnepxReceiver, intentFilter);
        //屏幕点亮状态监听，用于单独控制音乐播放
        if(screenStateReceiver == null){
            screenStateReceiver = new ScreenStateReceiver();
        }
        IntentFilter  intentFilter2 = new IntentFilter();
        intentFilter2.addAction("_ACTION_SCREEN_OFF");
        intentFilter2.addAction("_ACTION_SCREEN_ON");
        registerReceiver(screenStateReceiver, intentFilter2);
        //启用前台服务，提升优先级
        if (KeepLive.foregroundNotification != null){
            Intent intent2 = new Intent(getApplicationContext(), NotificationClickReceiver.class);
            intent2.setAction(NotificationClickReceiver.CLICK_NOTIFICATION);
            Notification notification = NotificationUtils.createNotification(this, KeepLive.foregroundNotification.getTitle(), KeepLive.foregroundNotification.getDescription(), KeepLive.foregroundNotification.getIconRes(), intent2);
            startForeground(13691, notification);
        }
        //绑定守护进程
        try {
            this.bindService(new Intent(LocalService.this, RemoteService.class), connection, Context.BIND_ABOVE_CLIENT);
        }catch (Exception e){
        }
        if( KeepLive.keepLiveService != null){
            KeepLive.keepLiveService.onWorking(this);
        }
        return START_STICKY;
    }
    private void play() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }
    private void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }
    private class ScreenStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, Intent intent) {
            if (intent.getAction().equals("_ACTION_SCREEN_OFF")) {
                isPause = false;
                play();
            } else if (intent.getAction().equals("_ACTION_SCREEN_ON")) {
                isPause = true;
                pause();
            }
        }
    }
    private final class MyBilder extends GuardAidl.Stub {

        @Override
        public void wakeUp() throws RemoteException {

        }

    }
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Intent localService = new Intent(LocalService.this,
                    RemoteService.class);
            LocalService.this.startService(localService);
            LocalService.this.bindService(new Intent(LocalService.this,
                            RemoteService.class), connection,
                    Context.BIND_ABOVE_CLIENT);
            PowerManager pm = (PowerManager) LocalService.this.getSystemService(Context.POWER_SERVICE);
            boolean isScreenOn = pm.isScreenOn();
            if (isScreenOn){
                sendBroadcast(new Intent("_ACTION_SCREEN_ON"));
            }else{
                sendBroadcast(new Intent("_ACTION_SCREEN_OFF"));
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                if (mBilder != null)
                    mBilder.wakeUp();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };
    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(connection);
        unregisterReceiver(mOnepxReceiver);
        unregisterReceiver(screenStateReceiver);
        if( KeepLive.keepLiveService != null){
            KeepLive.keepLiveService.onStop(this);
        }
    }
}
