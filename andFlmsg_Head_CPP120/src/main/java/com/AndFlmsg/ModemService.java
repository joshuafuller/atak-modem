/*
 * ModemService.java
 *
 * Copyright (C) 2011 John Douyere (VK2ETA)
 * Based on Pskmail from Per Crusefalk and Rein Couperus
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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.util.concurrent.Semaphore;


/**
 * @author John Douyere (VK2ETA)
 */
public class ModemService extends Service {
    //Semaphores to instruct the RxTx Thread to start or stop
    public static Semaphore restartRxModem = new Semaphore(1, false);
    static String version = "Version 1.4.2, 2020-12-01";

    static boolean TXActive = false;

    //JD temp FIX: init as first modem in list
    private static final int savedModeIndex = config.getPreferenceI("LASTMODEUSED", Modem.DEFAULT_MODEM_CODE);

    static int TxModem = savedModeIndex;
    static int RxModem = savedModeIndex;

    // globals to pass info to gui windows
    static String TXmonitor = "";
    static String TermWindow = "";
    static int cpuload;
    // globals for communication
    static String TX_Text = "";

    static {
        System.loadLibrary("AndFlmsg_Flmsg_Interface");
    }

    //Declaration of native classes
    public native static void saveEnv();

    @Override
    public void onCreate() {
        //Save Environment if we need to access Java code/variables from C++
        saveEnv();

        //Initialize Modem (creates the various type of modem objects)
        Modem.ModemInit();

        //Check that we have a current mode, otherwise take the first one in the list (useful when we have a NIL list of custom modes)
        ModemService.RxModem = ModemService.TxModem = ModemService.RxModem;

        //Reset frequency and squelch
        Modem.reset();

        //Make sure the display strings are blank
        ModemService.TXmonitor = "";
        ModemService.TermWindow = "";
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start the RxThread
        Modem.startmodem();

        //Make sure Android keeps this running even if resources are limited
        //Display the notification in the system bar at the top at the same time
        startForeground(1, AndFlmsg.myNotification);

        // Keep this service running until it is explicitly stopped, so use sticky.
        //VK2ETA To-DO: Check if START_STICKY causes the service restart on ACRA report
        //VK2ETA	return START_STICKY;
        return START_NOT_STICKY;

    }

    @Override
    public void onDestroy() {
        // Kill the Rx Modem thread
        Modem.stopRxModem();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // Nothing here, not used
        return null;
    }
}
