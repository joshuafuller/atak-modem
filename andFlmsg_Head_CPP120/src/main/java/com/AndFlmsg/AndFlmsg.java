/*
 * AndFlmsg.java
 *
 * Copyright (C) 2014 John Douyere (VK2ETA)
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.AndFlmsg;

/**
 * @author John Douyere <vk2eta@gmail.com>
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LayoutAnimationController;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;


@SuppressLint("SetJavaScriptEnabled")
public class AndFlmsg extends AppCompatActivity {

    private static boolean havePassedAllPermissionsTest = false;

    public static Window myWindow = null;

    public static boolean RXParamsChanged = false;

    public static SharedPreferences mysp = null;

    // Horizontal Fling detection despite scrollview
    private GestureDetector mGesture;
    // screen transitions / animations
    private static final int NORMAL = 0;
    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static final int TOP = 3;
    private static final int BOTTOM = 4;

    // Views values
    private final static int TERMVIEW = 1;
    private final static int MODEMVIEWnoWF = 3;
    public final static int MODEMVIEWwithWF = 4;

    static ProgressBar SignalQuality = null;
    static ProgressBar CpuLoad = null;

    public static int currentview = 0;

    // Layout Views
    private static TextView myTermTV;
    private static ScrollView myTermSC;
    private static TextView myModemTV;
    private static ScrollView myModemSC;

    private static String savedTextMessage = "";

    // Generic button variable. Just for callback initialisation
    private Button myButton;

    public static String TerminalBuffer = "";
    public static String ModemBuffer = "";

    // Member object for processing of Rx and Tx
    // Can be stopped (i.e no RX) to save battery and allow Android to reclaim
    // resources if not visible to the user
    public static boolean ProcessorON = false;
    private static final boolean modemPaused = false;

    // Notifications
    //private NotificationManager myNotificationManager;
    public static Notification myNotification = null;

    // Need handler for callbacks to the UI thread
    public static final Handler mHandler = new Handler();

    // Listener for changes in preferences
    public static OnSharedPreferenceChangeListener splistener;

    // Runnable for updating the signal quality bar in Modem Window
    public static final Runnable updatesignalquality = new Runnable() {
        public void run() {
            if ((SignalQuality != null)
                    && ((currentview == MODEMVIEWnoWF) || (currentview == MODEMVIEWwithWF))) {
                SignalQuality.setProgress((int) Modem.metric);
                SignalQuality.setSecondaryProgress((int) Modem.squelch);
            }
        }
    };

    // Runnable for updating the CPU load bar in Modem Window
    public static final Runnable updatecpuload = new Runnable() {
        public void run() {
            if ((CpuLoad != null)
                    && ((currentview == MODEMVIEWnoWF) || (currentview == MODEMVIEWwithWF))) {
                CpuLoad.setProgress(Processor.cpuload);
            }
        }
    };

    // Create runnable for posting to terminal window
    public static final Runnable addtoterminal = new Runnable() {
        public void run() {
            // myTV.setText(Processor.TermWindow);
            if (myTermTV != null) {
                //Update done with setText below
                //myTermTV.append(Processor.TermWindow);
                TerminalBuffer += Processor.TermWindow;
                Processor.TermWindow = "";
                if (TerminalBuffer.length() > 10000)
                    TerminalBuffer = TerminalBuffer.substring(2000);
                myTermTV.setText(TerminalBuffer);
                // Then scroll to the bottom
                if (myTermSC != null) {
                    myTermSC.post(new Runnable() {
                        public void run() {
                            myTermSC.fullScroll(View.FOCUS_DOWN);
                        }
                    });
                }
            }
        }
    };

    // Create runnable for posting to modem window
    public static final Runnable addtomodem = new Runnable() {
        public void run() {
            // myTV.setText(Processor.TermWindow);
            if (myModemTV != null) {
                //Only add the size limited ModemBuffer otherwise we overload the textview display
                //myModemTV.append(Processor.monitor);
                ModemBuffer += Processor.monitor;
                Processor.monitor = "";
                //Noted a slowing down of Gui after a large number of characters are received
                //Reduced the buffer size if (ModemBuffer.length() > 60000)
                if (ModemBuffer.length() > 10000)
                    ModemBuffer = ModemBuffer.substring(5000);
                //Reassign only the size limited buffer
                myModemTV.setText(ModemBuffer);
                // Then scroll to the bottom
                if (myModemSC != null) {
                    myModemSC.post(new Runnable() {
                        public void run() {
                            myModemSC.fullScroll(View.FOCUS_DOWN);
                        }
                    });
                }
            }
        }
    };

    @Override
    public void onBackPressed() {
        Toast.makeText(this, getString(R.string.txt_PleaseUseMenuExit), Toast.LENGTH_SHORT).show();
    }


    public static boolean readLogsPermit = false;
    public static boolean writeExtStoragePermit = false;
    public static boolean recordAudioPermit = false;
    public static boolean modifyAudioSettingsPermit = false;
    public static boolean internetPermit = false;
    public static boolean broadcastStickyPermit = false;
    public static boolean readPhoneStatePermit = false;
    private final int REQUEST_PERMISSIONS = 15556;
    public final String[] permissionList = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.READ_LOGS,
            Manifest.permission.BROADCAST_STICKY,
            Manifest.permission.INTERNET,
            Manifest.permission.READ_PHONE_STATE
    };


    //Request permission from the user
    private void requestAllCriticalPermissions() {
        ActivityCompat.requestPermissions(this, permissionList, REQUEST_PERMISSIONS);
    }


    private boolean allPermissionsOk() {
        final int granted = PackageManager.PERMISSION_GRANTED;

        writeExtStoragePermit = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == granted;
        recordAudioPermit = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == granted;
        modifyAudioSettingsPermit = ContextCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS) == granted;
        readLogsPermit = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_LOGS) == granted;
        broadcastStickyPermit = ContextCompat.checkSelfPermission(this, Manifest.permission.BROADCAST_STICKY) == granted;
        internetPermit = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == granted;
        readPhoneStatePermit = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == granted;

        return recordAudioPermit && modifyAudioSettingsPermit //&& readLogsPermit never granted in later versions of Android
                && broadcastStickyPermit && internetPermit && readPhoneStatePermit;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        int granted = PackageManager.PERMISSION_GRANTED;
        for (int i = 0; i < grantResults.length; i++) {
            switch (permissions[i]) {
                case Manifest.permission.RECORD_AUDIO:
                    recordAudioPermit = grantResults[i] == granted;
                    break;
                case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                    writeExtStoragePermit = grantResults[i] == granted;
                    break;
                case Manifest.permission.MODIFY_AUDIO_SETTINGS:
                    modifyAudioSettingsPermit = grantResults[i] == granted;
                    break;
                case Manifest.permission.READ_LOGS:
                    readLogsPermit = grantResults[i] == granted;
                    break;
                case Manifest.permission.BROADCAST_STICKY:
                    broadcastStickyPermit = grantResults[i] == granted;
                    break;
                case Manifest.permission.INTERNET:
                    internetPermit = grantResults[i] == granted;
                    break;
                case Manifest.permission.READ_PHONE_STATE:
                    readPhoneStatePermit = grantResults[i] == granted;
                    break;
            }
        }
        //Re-do overall check
        havePassedAllPermissionsTest = allPermissionsOk();
        if (havePassedAllPermissionsTest &&
                requestCode == REQUEST_PERMISSIONS) { //Only if requested at OnCreate time
            performOnCreate();
            performOnStart();
        } else {
            //Close that activity and return to previous screen
            finish();
            //Kill the process
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Avoid app restart if already running and pressing on the app icon again
        if (!isTaskRoot()) {
            final Intent intent = getIntent();
            final String intentAction = intent.getAction();
            if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intentAction != null && intentAction.equals(Intent.ACTION_MAIN)) {
                //Log.w(LOG_TAG, "Main Activity is not the root.  Finishing Main Activity instead of launching.");
                finish();
                return;
            }
        }


        //Debug only: Menu hack to force showing the overflow button (aka menu button) on the
        //   action bar EVEN IF there is a hardware button
        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");

            if (menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception e) {
            // presumably, not relevant
        }

        //Request all permissions up-front and be done with it.
        //If the app can't perform properly with what is requested then
        // abort rather than have a crippled app running
        //Dangerous permissions groups that need to ne asked for:
        //Contacts: for when creating a new mail if we want to get the email address of a contact. Optional.
        //Location: for GPS to send position and to get accurage time for scanning servers. Essential.
        //Microphone: to get the audio input for the modems. Essential.
        //Phone: to disconnect the Bluetooth audio if a phone call comes in. Otherwise we
        //   send the phone call over the radio. Not allowed in Amateur radio or only with severe restrictions. Essential.
        //Storage: to read and write to the SD card. Essential, otherwise why use the app. There is Tivar for Rx only applications.
        //First check if the app already has the permissions
        havePassedAllPermissionsTest = allPermissionsOk();
        if (havePassedAllPermissionsTest) {
            performOnCreate();
        } else {
            requestAllCriticalPermissions();
        }

    }


    //Could be executed only when all necessary permissions are allowed
    private void performOnCreate() {

        // Init config
        mysp = PreferenceManager.getDefaultSharedPreferences(this);

        //Set the Activity's Theme
        setTheme(R.style.andFlmsgStandard);

        // Get the RSID flags from stored preferences
        Modem.txRsidOn = config.getPreferenceB("TXRSID", false);
        Modem.rxRsidOn = config.getPreferenceB("RXRSID", false);

        //Update the list of available modems
        Modem.updateModemCapabilityList();

        //If we do not have a last mode, this is the first time in the app
        if (Processor.RxModem == -1) {
            Processor.RxModem = Modem.getMode("8PSK1000");
        }

        // We start with the Terminal screen
        displayTerminal(NORMAL);
    }

    /**
     * Called when the activity is (re)started (to foreground)
     **/
    @Override
    public void onStart() {
        super.onStart();

        //Conditional to having passed the permission tests
        if (havePassedAllPermissionsTest) {
            performOnStart();
        }
    }

    //Only when all permissions are agreed on
    @TargetApi(Build.VERSION_CODES.O)
    void performOnStart() {

        // Store preference reference for later (config.java)
        mysp = PreferenceManager.getDefaultSharedPreferences(this);
        // Refresh defaults since we could be coming back
        // from the preference activity

        // Re-initilize modem when NOT busy to use the latest parameters
        if (RXParamsChanged) {
            // Reset flag then stop and restart modem
            RXParamsChanged = false;
            // Cycle modem service off then on
            if (ProcessorON) {
                if (Modem.modemState == Modem.RXMODEMRUNNING) {
                    Modem.stopRxModem();
                    stopService(new Intent(AndFlmsg.this, Processor.class));
                    ProcessorON = false;
                    // Force garbage collection to prevent Out Of Memory errors
                    // on small RAM devices
                    System.gc();
                }
            }
            // Wait for modem to stop and then restart
            while (Modem.modemState != Modem.RXMODEMIDLE) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            // Force garbage collection to prevent Out Of Memory errors on small
            // RAM devices
            System.gc();

            // Get current mode index (returns first position if not in list
            // anymore in case we changed to custom mode list on the current
            // mode)
            Processor.RxModem = Processor.TxModem = Modem.
                    customModeListInt[Modem.getModeIndex(Processor.RxModem)];

            startService(new Intent(AndFlmsg.this, Processor.class));
            ProcessorON = true;
        } else { // start if not ON yet AND we haven't paused the modem manually
            if (!ProcessorON && !modemPaused) {
                String NOTIFICATION_CHANNEL_ID = "com.AndFlmsg";
                String channelName = "Background Modem";
                NotificationChannel chan;
                NotificationCompat.Builder mBuilder;
                String chanId = "";
                //New code for support of Android version 8+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
                    chan.setLightColor(Color.BLUE);
                    chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
                    NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    assert manager != null;
                    manager.createNotificationChannel(chan);
                    chanId = chan.getId();
                }
                mBuilder = new NotificationCompat.Builder(this, chanId)
                        .setSmallIcon(R.drawable.notificationicon)
                        .setContentTitle(getString(R.string.txt_ModemON))
                        .setContentText(getString(R.string.txt_FldigiModemOn))
                        .setOngoing(true);
                // Creates an explicit intent for an Activity in your app
                Intent notificationIntent = new Intent(this, AndFlmsg.class);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                //Google: The stack builder object will contain an artificial back stack for the started Activity.
                // This ensures that navigating backward from the Activity leads out of your application to the Home screen.
                TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
                // Adds the back stack for the Intent (but not the Intent itself)
                stackBuilder.addParentStack(AndFlmsg.class);
                // Adds the Intent that starts the Activity to the top of the stack
                stackBuilder.addNextIntent(notificationIntent);
                PendingIntent pIntent =
                        stackBuilder.getPendingIntent(
                                0,
                                0
                        );
                mBuilder.setContentIntent(pIntent);
                myNotification = mBuilder.build();
                // Force garbage collection to prevent Out Of Memory errors on
                // small RAM devices
                System.gc();
                startService(new Intent(AndFlmsg.this, Processor.class));
                ProcessorON = true;
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Toast.makeText(AndFlmsg.this, "requestCode" + requestCode, Toast.LENGTH_SHORT).show();
    }

    // Option Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }


    // Customize the Option Menu at run time
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }


    // Option Screen handler
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(
                AndFlmsg.this);
        switch (item.getItemId()) {
            case R.id.prefs:
                Intent OptionsActivity = new Intent(AndFlmsg.this,
                        myPreferences.class);
                startActivity(OptionsActivity);
                break;
            case R.id.exit:
                myAlertDialog.setMessage(getString(R.string.txt_AreYouSureExit));
                myAlertDialog.setCancelable(false);
                myAlertDialog.setPositiveButton(getString(R.string.txt_Yes),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // Stop the Modem and Listening Service
                                if (ProcessorON) {
                                    stopService(new Intent(AndFlmsg.this,
                                            Processor.class));
                                    ProcessorON = false;
                                }
                                // Close that activity and return to previous screen
                                finish();
                                // Kill the process
                                android.os.Process.killProcess(android.os.Process.myPid());
                            }
                        });
                myAlertDialog.setNegativeButton(getString(R.string.txt_No), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
                myAlertDialog.show();
                break;
        }
        return true;
    }

    public static void screenAnimation(ViewGroup panel, int screenAnimation) {

        AnimationSet set = new AnimationSet(true);

        Animation animation = new AlphaAnimation(0.0f, 1.0f);
        animation.setDuration(100);
        set.addAnimation(animation);

        switch (screenAnimation) {

            case NORMAL:
                return;
            // break;

            case RIGHT:
                animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF,
                        -1.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f);
                break;
            case LEFT:
                animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF,
                        1.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f);
                break;

            case TOP:
                animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF,
                        0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                        Animation.RELATIVE_TO_SELF, -1.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f);
                break;

            case BOTTOM:
                animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF,
                        0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                        Animation.RELATIVE_TO_SELF, 1.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f);
                break;

        }

        animation.setDuration(200);
        set.addAnimation(animation);

        LayoutAnimationController controller = new LayoutAnimationController(
                set, 0.25f);
        if (panel != null) {
            panel.setLayoutAnimation(controller);
        }
    }


    //Set the button text size based on user's preferences
    private void setTextSize(Button thisButton) {
        int textSize = config.getPreferenceI("BUTTONTEXTSIZE", 12);
        if (textSize < 7) textSize = 7;
        if (textSize > 20) textSize = 20;
        thisButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
    }


    // Display the Terminal layout and associate it's buttons
    private void displayTerminal(int screenMovement) {
        // Change layout and remember which one we are on
        currentview = TERMVIEW;
        setContentView(R.layout.terminal);
        screenAnimation((ViewGroup) findViewById(R.id.termscreen),
                screenMovement);
        myTermTV = findViewById(R.id.terminalview);
        myTermTV.setHorizontallyScrolling(false);
        myTermTV.setTextSize(16);
        myWindow = getWindow();

        // If blank (on start), display version
        final String welcomeString = "\n" + this.getString(R.string.txt_WelcomeToAndFlmsg) + " "
                + Processor.version
                + this.getString(R.string.txt_WelcomeIntro);
        if (TerminalBuffer.length() == 0) {
            TerminalBuffer = welcomeString;
        } else {
            if (TerminalBuffer.equals(welcomeString)) {
                TerminalBuffer = "";
            }
        }
        // Reset terminal display in case it was blanked out by a new oncreate
        // call
        myTermTV.setText(TerminalBuffer);
        myTermSC = findViewById(R.id.terminalscrollview);
        // update with whatever we have already accumulated then scroll
        AndFlmsg.mHandler.post(AndFlmsg.addtoterminal);

        //Restore the data entry field at the bottom
        EditText myView = findViewById(R.id.edit_text_out);
        myView.setText(savedTextMessage);
        myView.setSelection(myView.getText().length());
        //Add a textwatcher to save the text as it is being typed
        myView.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable arg0) {
            }

            public void beforeTextChanged(CharSequence arg0, int arg1,
                                          int arg2, int arg3) {
            }

            public void onTextChanged(CharSequence arg0, int arg1, int arg2,
                                      int arg3) {
                savedTextMessage = arg0.toString();
            }

        });

        // initialise CPU load bar display
        CpuLoad = findViewById(R.id.cpu_load);

        // initialise squelch and signal quality dislay
        SignalQuality = findViewById(R.id.signal_quality);

        // JD Initialize the Send Text button (commands in connected mode)
        myButton = findViewById(R.id.button_sendtext);
        setTextSize(myButton);
        myButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                TextView view = findViewById(R.id.edit_text_out);
                String intext = view.getText().toString();
                //Clear the text field
                view.setText("");
                savedTextMessage = "";
                Processor.TX_Text += (intext + "\n");
                Modem.txData("", "", intext + "\n", 0, 0, false, "");
            }
        });
    }

    //Save last mode used for next app start
    public static void saveLastModeUsed(int modemCode) {
        SharedPreferences.Editor editor = AndFlmsg.mysp.edit();
        editor.putString("LASTMODEUSED", Integer.toString(modemCode));
        // Commit the edits!
        editor.commit();

    }
}
