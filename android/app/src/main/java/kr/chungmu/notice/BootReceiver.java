package kr.chungmu.notice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 칠판이 켜지면(또는 앱이 업데이트되면) 자동으로 대기 시작 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.topic(context).isEmpty()) {
            try { NoticeService.start(context); } catch (Exception ignored) {}
        }
    }
}
