/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.spartacusrex.spartacuside;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.spartacusrex.spartacuside.session.TermSession;
import com.spartacusrex.spartacuside.startup.setup.filemanager;
import com.spartacusrex.spartacuside.util.TermSettings;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Date;
import java.util.Enumeration;

/**
 * A terminal emulator activity.
 */

public class Term extends Activity {
    /**
     * The ViewFlipper which holds the collection of EmulatorView widgets.
     */
    private TermViewFlipper mViewFlipper;

    /**
     * The name of the ViewFlipper in the resources.
     */
    private static final int VIEW_FLIPPER = R.id.view_flipper;

    private ArrayList<TermSession> mTermSessions;

    private SharedPreferences mPrefs;
    private TermSettings mSettings;

    private final static int SELECT_TEXT_ID = 0;
    private final static int COPY_ALL_ID = 1;
    private final static int PASTE_ID = 2;
    private final static int CLEAR_ALL_ID = 3;

    private boolean mAlreadyStarted = false;

    private Intent TSIntent;

    public static final int REQUEST_CHOOSE_WINDOW = 1;
    public static final String EXTRA_WINDOW_ID = "jackpal.androidterm.window_id";
    private int onResumeSelectWindow = -1;


    //    private PowerManager.WakeLock mWakeLock;
    //    private WifiManager.WifiLock mWifiLock;

    private TermService mTermService;
    private ServiceConnection mTSConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TermDebug.LOG_TAG, "Bound to TermService");
            TermService.TSBinder binder = (TermService.TSBinder) service;
            mTermService = binder.getService();
            populateViewFlipper();
        }

        public void onServiceDisconnected(ComponentName arg0) {
            mTermService = null;
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.e(TermDebug.LOG_TAG, "onCreate");
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSettings = new TermSettings(mPrefs);

        TSIntent = new Intent(this, TermService.class);
        
        //Not Needed..
        //startService(TSIntent);

        if (!bindService(TSIntent, mTSConnection, BIND_AUTO_CREATE)) {
            Log.w(TermDebug.LOG_TAG, "bind to service failed!");
        }

        setContentView(R.layout.term_activity);
        mViewFlipper = (TermViewFlipper) findViewById(VIEW_FLIPPER);
        registerForContextMenu(mViewFlipper);
        
//        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
//        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TermDebug.LOG_TAG);
//        mWakeLock.acquire();

//        WifiManager wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
//        mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, TermDebug.LOG_TAG);

        updatePrefs();
        mAlreadyStarted = true;
    }


    private void populateViewFlipper() {
        if (mTermService != null) {
            mTermSessions = mTermService.getSessions(getFilesDir());

//            if (mTermSessions.size() == 0) {
//                mTermSessions.add(createTermSession());
//            }

            for (TermSession session : mTermSessions) {
                EmulatorView view = createEmulatorView(session);
                mViewFlipper.addView(view);
            }

            updatePrefs();
        }

        //Set back to ESC
        
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mViewFlipper.removeAllViews();
        unbindService(mTSConnection);

        //stopService(TSIntent);

        mTermService = null;
        mTSConnection = null;

//        if (mWakeLock.isHeld()) {
//            mWakeLock.release();
//        }
//        if (mWifiLock.isHeld()) {
//            mWifiLock.release();
//        }
    }

    private void restart() {
        startActivity(getIntent());
        finish();
    }

    public String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("SpartacusRex GET LOCAL IP : ", ex.toString());
        }
        
        return null;
    }

    /*private TermSession createTermSession() {
        String HOME = getFilesDir().getPath();
        String APK  = getPackageResourcePath();
        String IP   = getLocalIpAddress();
        if(IP == null){
           IP = "127.0.0.1";
        }
        
        String initialCommand = "export HOME="+HOME+";cd $HOME;~/system/init "+HOME+" "+APK+" "+IP;
//        String initialCommand = "export HOME="+HOME+";cd $HOME";


        return new TermSession(this,mSettings, null, initialCommand);
    }*/

    private EmulatorView createEmulatorView(TermSession session) {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        EmulatorView emulatorView = new EmulatorView(this, session, mViewFlipper, metrics);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.LEFT
        );
        emulatorView.setLayoutParams(params);

        session.setUpdateCallback(emulatorView.getUpdateCallback());

        return emulatorView;
    }

    private TermSession getCurrentTermSession() {
        return mTermSessions.get(mViewFlipper.getDisplayedChild());
    }

    private EmulatorView getCurrentEmulatorView() {
        return (EmulatorView) mViewFlipper.getCurrentView();
    }

    private void updatePrefs() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        for (View v : mViewFlipper) {
            ((EmulatorView) v).setDensity(metrics);
            ((EmulatorView) v).updatePrefs(mSettings);
        }
        {
            Window win = getWindow();
            WindowManager.LayoutParams params = win.getAttributes();
            final int FULLSCREEN = WindowManager.LayoutParams.FLAG_FULLSCREEN;
            int desiredFlag = mSettings.showStatusBar() ? 0 : FULLSCREEN;
            if (desiredFlag != (params.flags & FULLSCREEN)) {
                if (mAlreadyStarted) {
                    // Can't switch to/from fullscreen after
                    // starting the activity.
                    restart();
                } else {
                    win.setFlags(desiredFlag, FULLSCREEN);
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mTermSessions != null && mTermSessions.size() < mViewFlipper.getChildCount()) {
            for (int i = 0; i < mViewFlipper.getChildCount(); ++i) {
                EmulatorView v = (EmulatorView) mViewFlipper.getChildAt(i);
                if (!mTermSessions.contains(v.getTermSession())) {
                    v.onPause();
                    mViewFlipper.removeView(v);
                    --i;
                }
            }
        }

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSettings.readPrefs(mPrefs);
        updatePrefs();

        if (onResumeSelectWindow >= 0) {
            mViewFlipper.setDisplayedChild(onResumeSelectWindow);
            onResumeSelectWindow = -1;
        } else {
            mViewFlipper.resumeCurrentView();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        mViewFlipper.pauseCurrentView();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        EmulatorView v = (EmulatorView) mViewFlipper.getCurrentView();
        if (v != null) {
            v.updateSize(true);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.menu_window_list) {
            //startActivityForResult(new Intent(this, WindowList.class), REQUEST_CHOOSE_WINDOW);
            //Show a list of windows..
            openContextMenu(mViewFlipper);
//        } else if (id == R.id.menu_reset) {
//            doResetTerminal();
        } else if (id == R.id.menu_toggle_soft_keyboard) {
            doToggleSoftKeyboard();
        
        } else if (id == R.id.menu_back_esc) {
            doBACKtoESC();
        
        } else if (id == R.id.menu_keylogger) {
            doToggelKeyLogger();
        
        } else if (id == R.id.menu_paste) {
            doPaste();
        
        } else if (id == R.id.menu_copyall) {
            doCopyAll();
            
        } else if (id == R.id.menu_copyemail) {
            doEmailTranscript();
        }

        /*if (id == R.id.menu_preferences) {
            doPreferences();
        } else if (id == R.id.menu_new_window) {
            //doCreateNewWindow();
            //show keyboards..
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
            
        } else if (id == R.id.menu_close_window) {
            doCloseWindow();
        } else if (id == R.id.menu_window_list) {
            startActivityForResult(new Intent(this, WindowList.class), REQUEST_CHOOSE_WINDOW);
        } else if (id == R.id.menu_reset) {
            doResetTerminal();
        } else if (id == R.id.menu_send_email) {
            doEmailTranscript();
        } else if (id == R.id.menu_special_keys) {
            doDocumentKeys();
        } else if (id == R.id.menu_toggle_soft_keyboard) {
            doToggleSoftKeyboard();
        } else if (id == R.id.menu_toggle_wakelock) {
            doToggleWakeLock();
        } else if (id == R.id.menu_toggle_wifilock) {
            doToggleWifiLock();
        }*/

        return super.onOptionsItemSelected(item);
    }

    private void doCreateNewWindow() {
        if (mTermSessions == null) {
            Log.w(TermDebug.LOG_TAG, "Couldn't create new window because mTermSessions == null");
            return;
        }

//        TermSession session = createTermSession();
//        mTermSessions.add(session);
//        EmulatorView view = createEmulatorView(session);
//        view.updatePrefs(mSettings);
//        mViewFlipper.addView(view);
//        mViewFlipper.setDisplayedChild(mViewFlipper.getChildCount()-1);
    }

    private void doCloseWindow() {
        if (mTermSessions == null) {
            return;
        }

        EmulatorView view = getCurrentEmulatorView();
        if (view == null) {
            return;
        }
        TermSession session = mTermSessions.remove(mViewFlipper.getDisplayedChild());
        view.onPause();
        session.finish();
        mViewFlipper.removeView(view);
        if (mTermSessions.size() == 0) {
            finish();
        } else {
            mViewFlipper.showNext();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        switch (request) {
        case REQUEST_CHOOSE_WINDOW:
            if (result == RESULT_OK && data != null) {
                int position = data.getIntExtra(EXTRA_WINDOW_ID, -2);
                if (position >= 0) {
                    // Switch windows after session list is in sync, not here
                    onResumeSelectWindow = position;
                } else if (position == -1) {
                    doCreateNewWindow();
                }
            } else {
                // Close the activity if user closed all sessions
                if (mTermSessions.size() == 0) {
                    finish();
                }
            }
            break;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        /*MenuItem wakeLockItem = menu.findItem(R.id.menu_toggle_wakelock);
        MenuItem wifiLockItem = menu.findItem(R.id.menu_toggle_wifilock);
        if (mWakeLock.isHeld()) {
            wakeLockItem.setTitle(R.string.disable_wakelock);
        } else {
            wakeLockItem.setTitle(R.string.enable_wakelock);
        }
        if (mWifiLock.isHeld()) {
            wifiLockItem.setTitle(R.string.disable_wifilock);
        } else {
            wifiLockItem.setTitle(R.string.enable_wifilock);
        }*/
        
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
      super.onCreateContextMenu(menu, v, menuInfo);

        //Show alist of windows
        menu.setHeaderTitle("Terminals");
        menu.add(0, 0, 0, "Terminal 1");
        menu.add(0, 1, 1, "Terminal 2");
        menu.add(0, 2, 2, "Terminal 3");
        menu.add(0, 3, 3, "Terminal 4");

//      menu.setHeaderTitle(R.string.edit_text);
//      menu.add(0, SELECT_TEXT_ID, 0, R.string.select_text);
//      menu.add(0, COPY_ALL_ID, 0, R.string.copy_all);
//      menu.add(0, PASTE_ID, 0,  R.string.paste);
//      if (!canPaste()) {
//          menu.getItem(PASTE_ID).setEnabled(false);
//      }

    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        //Set the selected window..
        
        mViewFlipper.setDisplayedChild(item.getItemId());

        /*switch (item.getItemId()) {
          case SELECT_TEXT_ID:
            getCurrentEmulatorView().toggleSelectingText();
            return true;
          case COPY_ALL_ID:
            doCopyAll();
            return true;
          case PASTE_ID:
            doPaste();
            return true;
          default:
            return super.onContextItemSelected(item);
          }*/

          return super.onContextItemSelected(item);
        }

    private boolean canPaste() {
        ClipboardManager clip = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clip.hasText()) {
            return true;
        }
        return false;
    }

    private void doPreferences() {
        startActivity(new Intent(this, TermPreferences.class));
    }

    private void doResetTerminal() {
        restart();
    }

    private void doEmailTranscript() {
        // Don't really want to supply an address, but
        // currently it's required, otherwise we get an
        // exception.
        String addr = "user@example.com";
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"+ addr));
        String intro = "Terminal Transcript @ "+new Date().toLocaleString()+"\n\n";
        intent.putExtra("body", intro+getCurrentTermSession().getTranscriptText().trim());
        startActivity(intent);
    }

    private void doCopyAll() {
        ClipboardManager clip = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        clip.setText(getCurrentTermSession().getTranscriptText().trim());
    }

    private void doPaste() {
        ClipboardManager clip = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(!clip.hasText()){
            Toast tt = Toast.makeText(this, "No text to Paste..", Toast.LENGTH_SHORT);
            tt.show();
            return;
        }

        CharSequence paste = clip.getText();
        byte[] utf8;
        try {
            utf8 = paste.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.e(TermDebug.LOG_TAG, "UTF-8 encoding not found.");
            return;
        }

        getCurrentTermSession().write(paste.toString());
    }

    private void doDocumentKeys() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        Resources r = getResources();
        dialog.setTitle(r.getString(R.string.control_key_dialog_title));
        dialog.setMessage(
            formatMessage(mSettings.getControlKeyId(), TermSettings.CONTROL_KEY_ID_NONE,
                r, R.array.control_keys_short_names,
                R.string.control_key_dialog_control_text,
                R.string.control_key_dialog_control_disabled_text, "CTRLKEY")
            + "\n\n" +
            formatMessage(mSettings.getFnKeyId(), TermSettings.FN_KEY_ID_NONE,
                r, R.array.fn_keys_short_names,
                R.string.control_key_dialog_fn_text,
                R.string.control_key_dialog_fn_disabled_text, "FNKEY"));
         dialog.show();
     }

     private String formatMessage(int keyId, int disabledKeyId,
         Resources r, int arrayId,
         int enabledId,
         int disabledId, String regex) {
         if (keyId == disabledKeyId) {
             return r.getString(disabledId);
         }
         String[] keyNames = r.getStringArray(arrayId);
         String keyName = keyNames[keyId];
         String template = r.getString(enabledId);
         String result = template.replaceAll(regex, keyName);
         return result;
    }

    private void doToggleSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
    }

    private void doToggelKeyLogger() {
        if(mTermService == null){
            return;
        }

        boolean on = mTermService.isKeyLoggerOn();
        mTermService.setKeyLogger(!on);
        if(mTermService.isKeyLoggerOn()){
            Toast tt = Toast.makeText(this, "KEY LOGGER NOW ON!\n\nCheck ~/.keylog \n\n# tail -f ~/.keylog", Toast.LENGTH_LONG);
            tt.show();
        }else{
            Toast tt = Toast.makeText(this, "Key Logger switched off..", Toast.LENGTH_SHORT);
            tt.show();
        }
        
    }
    private void doBACKtoESC() {
        if(mTermService == null){
            return;
        }
        
        boolean on = mTermService.isBackESC();
        mTermService.setBackToESC(!on);
        if(mTermService.isBackESC()){
            Toast tt = Toast.makeText(this, "BACK => ESC", Toast.LENGTH_SHORT);
            tt.show();
        }else{
            Toast tt = Toast.makeText(this, "BACK behaves NORMALLY", Toast.LENGTH_SHORT);
            tt.show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //Is BACK ESC
//        Log.v("Terminal IDE","TERM : onkeyDown code:"+keyCode+" flags:"+event.getFlags()+" meta:"+event.getMetaState());

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if(mTermService.isBackESC()){
//                Log.v("SpartacusRex","TERM : ESC sent instead of back.!");
                //Send the ESC sequence..
                int ESC = TermKeyListener.KEYCODE_ESCAPE;
                getCurrentEmulatorView().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, ESC));
                getCurrentEmulatorView().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   ESC));
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
}


    private void doToggleWakeLock() {
//        if (mWakeLock.isHeld()) {
//            mWakeLock.release();
//        } else {
//            mWakeLock.acquire();
//        }
    }

    private void doToggleWifiLock() {
//        if (mWifiLock.isHeld()) {
//            mWifiLock.release();
//        } else {
//            mWifiLock.acquire();
//        }
    }
}
