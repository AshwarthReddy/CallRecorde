package net.synapticweb.callrecorder;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Service;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

public class MyServiceaccessibility  extends AccessibilityService {

    AccessibilityServiceInfo info;
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.d("TAG", "onAccessibilityEvent: event=" + event);

        String packagename = event.getPackageName().toString();
        PackageManager pm = this.getPackageManager();
        try {
            ApplicationInfo ai =pm.getApplicationInfo(packagename, 0);
            CharSequence al =  pm.getApplicationLabel(ai);
            Log.d("TAG", "app name: =" + al);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        // info = event.getSource()
        /*if (null == info) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (event.getPackageName().toString().equals("com.whatsapp")){
                StringBuilder message = new StringBuilder();
                if (!event.getText().isEmpty()) {
                    for (CharSequence subText : event.getText()) {
                        message.append(subText);
                    }
                    if (message.toString().contains("Message from")){
                        String name= message.toString().substring(3);
                    }
                }
            }
        }*/
    }

    @Override
    public void onInterrupt() {
        Log.d("TAG", "Oninturrupt soemthing went wrong");
    }

    @Override
    protected void onServiceConnected() {
        // pass the typeof events you want your service to listen to
        // other will not be handledby this service
        info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED;
              /*  | AccessibilityEvent.TYPE_VIEW_FOCUSED
                | AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                | AccessibilityEvent.TYPES_ALL_MASK;*/

        // In case you want your service to work only with a particular application
        //or when that application is in foreground, you should specify those applications package
        //names here, otherwise the service would listen events from all the applications
        info.packageNames =  null;
                //new String[] {"com.example.android.myFirstApp", "com.example.android.mySecondApp"};

        // Set the type of feedback your service will provide.
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
                | AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
        // the notification timeout is the time interval after which the service would
        // listen from the system. Anything happening between that interval won't be
        // captured by the service
        info.notificationTimeout = 100;

        // finally set the serviceInfo
        this.setServiceInfo(info);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {

        int action = event.getAction();
        int keyCode = event.getKeyCode();
        // the service listens for both pressing and releasing the key
        // so the below code executes twice, i.e. you would encounter two Toasts
        // in order to avoid this, we wrap the code inside an if statement
        // which executes only when the key is released
        if (action == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                Log.d("Check", "KeyUp");
                Toast.makeText(this, "KeyUp", Toast.LENGTH_SHORT).show();
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                Log.d("Check", "KeyDown");
                Toast.makeText(this, "KeyDown", Toast.LENGTH_SHORT).show();
            }
        }
        return super.onKeyEvent(event);
    }
}