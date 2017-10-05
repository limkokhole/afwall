/**
 * All iptables "communication" is handled by this class.
 * <p>
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
 * Copyright (C) 2011-2012  Umakanthan Chandran
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Rodrigo Zechin Rosauro, Umakanthan Chandran
 * @version 1.2
 */


package dev.ukanth.ufirewall;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.raizlabs.android.dbflow.sql.language.Delete;
import com.raizlabs.android.dbflow.sql.language.SQLite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.zip.GZIPInputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

import dev.ukanth.ufirewall.MainActivity.GetAppList;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogData;
import dev.ukanth.ufirewall.log.LogData_Table;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.service.RootShellService.RootCommand;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.JsonHelper;
import eu.chainfire.libsuperuser.Shell;
import eu.chainfire.libsuperuser.Shell.SU;

/**
 * Contains shared programming interfaces.
 * All iptables "communication" is handled by this class.
 */
public final class Api {
    /**
     * application logcat tag
     */
    public static final String TAG = "AFWall";

    /**
     * special application UID used to indicate "any application"
     */
    public static final int SPECIAL_UID_ANY = -10;
    /**
     * special application UID used to indicate the Linux Kernel
     */
    public static final int SPECIAL_UID_KERNEL = -11;
    /**
     * special application UID used for dnsmasq DHCP/DNS
     */
    public static final int SPECIAL_UID_TETHER = -12;
    /** special application UID used for netd DNS proxy */
    //public static final int SPECIAL_UID_DNSPROXY	= -13;
    /**
     * special application UID used for NTP
     */
    public static final int SPECIAL_UID_NTP = -14;

    public static final int NOTIFICATION_ID = 24556;

    private static String charsetName = "UTF8";
    private static String algorithm = "DES";
    private static int base64Mode = Base64.DEFAULT;

    private static final int WIFI_EXPORT = 0;
    private static final int DATA_EXPORT = 1;
    private static final int ROAM_EXPORT = 2;
    private static final int VPN_EXPORT = 3;
    private static final int LAN_EXPORT = 4;

    // Preferences
    public static String PREFS_NAME = "AFWallPrefs";
    public static final String PREF_FIREWALL_STATUS = "AFWallStaus";
    public static final String DEFAULT_PREFS_NAME = "AFWallPrefs";

    //for import/export rules
    public static final String PREF_3G_PKG = "AllowedPKG3G";
    public static final String PREF_WIFI_PKG = "AllowedPKGWifi";

    //revertback to old approach for performance
    public static final String PREF_3G_PKG_UIDS = "AllowedPKG3G_UIDS";
    public static final String PREF_WIFI_PKG_UIDS = "AllowedPKGWifi_UIDS";
    public static final String PREF_ROAMING_PKG_UIDS = "AllowedPKGRoaming_UIDS";
    public static final String PREF_VPN_PKG_UIDS = "AllowedPKGVPN_UIDS";
    public static final String PREF_LAN_PKG_UIDS = "AllowedPKGLAN_UIDS";


    public static final String PREF_CUSTOMSCRIPT = "CustomScript";
    public static final String PREF_CUSTOMSCRIPT2 = "CustomScript2"; // Executed on shutdown
    public static final String PREF_MODE = "BlockMode";
    public static final String PREF_ENABLED = "Enabled";
    // Modes
    public static final String MODE_WHITELIST = "whitelist";
    public static final String MODE_BLACKLIST = "blacklist";
    // Messages

    public static final String STATUS_CHANGED_MSG = "dev.ukanth.ufirewall.intent.action.STATUS_CHANGED";
    public static final String TOGGLE_REQUEST_MSG = "dev.ukanth.ufirewall.intent.action.TOGGLE_REQUEST";
    public static final String CUSTOM_SCRIPT_MSG = "dev.ukanth.ufirewall.intent.action.CUSTOM_SCRIPT";
    // Message extras (parameters)
    public static final String STATUS_EXTRA = "dev.ukanth.ufirewall.intent.extra.STATUS";
    public static final String SCRIPT_EXTRA = "dev.ukanth.ufirewall.intent.extra.SCRIPT";
    public static final String SCRIPT2_EXTRA = "dev.ukanth.ufirewall.intent.extra.SCRIPT2";

    private static final String ITFS_WIFI[] = InterfaceTracker.ITFS_WIFI;
    private static final String ITFS_3G[] = InterfaceTracker.ITFS_3G;
    private static final String ITFS_VPN[] = InterfaceTracker.ITFS_VPN;

    // iptables can exit with status 4 if two processes tried to update the same table
    private static final int IPTABLES_TRY_AGAIN = 4;

    private static String AFWALL_CHAIN_NAME = "afwall";

    private static final String dynChains[] = {"-3g-postcustom", "-3g-fork", "-wifi-postcustom", "-wifi-fork"};

    private static final String staticChains[] = {"", "-3g", "-wifi", "-reject", "-vpn", "-3g-tether", "-3g-home", "-3g-roam", "-wifi-tether", "-wifi-wan", "-wifi-lan"};

    // Cached applications
    public static List<PackageInfoData> applications = null;
    public static Set<String> recentlyInstalled = new HashSet<>();

    //for custom scripts
    public static String ipPath = null;
    public static String bbPath = null;
    private static Map<String, Integer> specialApps = null;

    public static void setRulesUpToDate(boolean rulesUpToDate) {
        Api.rulesUpToDate = rulesUpToDate;
    }

    private static boolean rulesUpToDate = false;

    /**
     * @brief Special user/group IDs that aren't associated with
     * any particular app.
     * <p>
     * See:
     * include/private/android_filesystem_config.h
     * in platform/system/core.git.
     * <p>
     * The accounts listed below are the only ones from
     * android_filesystem_config.h that are known to be used as
     * the UID of a process that uses the network.  The other
     * accounts in that .h file are either:
     * * used as supplemental group IDs for granting extra
     * privileges to apps,
     * * used as UIDs of processes that don't need the network,
     * or
     * * have not yet been reported by users as needing the
     * network.
     * <p>
     * The list is sorted in ascending UID order.
     */
    private static final String[] specialAndroidAccounts = {
            "root",
            "adb",
            "media",
            "vpn",
            "drm",
            "gps",
            "shell",
    };

    // returns c.getString(R.string.<acct>_item)
    private static String getSpecialDescription(Context c, String acct) {
        Resources r = c.getResources();
        String pkg = c.getPackageName();
        int rid = r.getIdentifier(acct + "_item", "string", pkg);
        return c.getString(rid);
    }

    /**
     * Display a simple alert box
     *
     * @param ctx     context
     * @param msgText message
     */
    public static void toast(final Context ctx, final CharSequence msgText) {
        if (ctx != null) {
            Handler mHandler = new Handler(Looper.getMainLooper());
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ctx, msgText, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public static void toast(final Context ctx, final CharSequence msgText, final int toastlen) {
        if (ctx != null) {
            Handler mHandler = new Handler(Looper.getMainLooper());
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ctx, msgText, toastlen).show();
                }
            });
        }
    }

    public static void setBinaryPath(Context ctx, boolean setv6) {
        boolean builtin = true;
        String pref = G.ip_path();

        if (pref.equals("system") || !setv6) {
            builtin = false;
        }

        String dir = "";
        if (builtin) {
            dir = ctx.getDir("bin", 0).getAbsolutePath() + "/";
        }
        Api.ipPath = dir + (setv6 ? "ip6tables" : "iptables");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            dir = ctx.getDir("bin", 0).getAbsolutePath() + "/";
            Api.ipPath = dir + "run_pie " + dir + (setv6 ? "ip6tables" : "iptables");
        }

        Api.bbPath = getBusyBoxPath(ctx, true);
    }


    /**
     * Determine toybox/busybox or built in
     *
     * @param ctx
     * @param considerSystem
     * @return
     */
    public static String getBusyBoxPath(Context ctx, boolean considerSystem) {

        if (G.bb_path().equals("system") && considerSystem) {
            return "busybox ";
        } else {
            String dir = ctx.getDir("bin", 0).getAbsolutePath();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                return dir + "/run_pie " + dir + "/busybox ";
            } else {
                return dir + "/busybox ";
            }
        }
    }


    /**
     * Get NFLog Path
     *
     * @param ctx
     * @returnC
     */
    public static String getNflogPath(Context ctx) {
        String dir = ctx.getDir("bin", 0).getAbsolutePath();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return dir + "/run_pie " + dir + "/nflog ";
        } else {
            return dir + "/nflog ";
        }
    }

    /**
     * Copies a raw resource file, given its ID to the given location
     *
     * @param ctx   context
     * @param resid resource id
     * @param file  destination file
     * @param mode  file permissions (E.g.: "755")
     * @throws IOException          on error
     * @throws InterruptedException when interrupted
     */
    private static void copyRawFile(Context ctx, int resid, File file, String mode) throws IOException, InterruptedException {
        final String abspath = file.getAbsolutePath();
        // Write the iptables binary
        final FileOutputStream out = new FileOutputStream(file);
        final InputStream is = ctx.getResources().openRawResource(resid);
        byte buf[] = new byte[1024];
        int len;
        while ((len = is.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.close();
        is.close();
        // Change the permissions

        Runtime.getRuntime().exec("chmod " + mode + " " + abspath).waitFor();
    }

    /**
     * Look up uid for each user by name, and if he exists, append an iptables rule.
     *
     * @param listCommands current list of iptables commands to execute
     * @param users        list of users to whom the rule applies
     * @param prefix       "iptables" command and the portion of the rule preceding "-m owner --uid-owner X"
     * @param suffix       the remainder of the iptables rule, following "-m owner --uid-owner X"
     */
    private static void addRuleForUsers(List<String> listCommands, String users[], String prefix, String suffix) {
        for (String user : users) {
            int uid = android.os.Process.getUidForName(user);
            if (uid != -1)
                listCommands.add(prefix + " -m owner --uid-owner " + uid + " " + suffix);
        }
    }

    private static void addRulesForUidlist(List<String> cmds, List<Integer> uids, String chain, boolean whitelist) {
        String action = whitelist ? " -j RETURN" : " -j " + AFWALL_CHAIN_NAME + "-reject";

        if (uids.indexOf(SPECIAL_UID_ANY) >= 0) {
            if (!whitelist) {
                cmds.add("-A " + chain + action);
            }
            // FIXME: in whitelist mode this blocks everything
        } else {
            for (Integer uid : uids) {
                if (uid != null && uid >= 0) {
                    cmds.add("-A " + chain + " -m owner --uid-owner " + uid + action);
                }
            }

			/*// netd runs as root, and on Android 4.3+ it handles all DNS queries
            if (uids.indexOf(SPECIAL_UID_DNSPROXY) >= 0) {
				addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53",  action);
			}*/

            String pref = G.dns_proxy();

            if (whitelist) {
                if (pref.equals("auto")) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j RETURN");
                    } else {
                        addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j " + AFWALL_CHAIN_NAME + "-reject");
                    }
                } else if (pref.equals("disable")) {
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j " + AFWALL_CHAIN_NAME + "-reject");
                } else {
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j RETURN");
                }
            } else {
                if (pref.equals("disable")) {
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j " + AFWALL_CHAIN_NAME + "-reject");
                } else if (pref.equals("enable")) {
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j RETURN");
                }
            }

            // NTP service runs as "system" user
            if (uids.indexOf(SPECIAL_UID_NTP) >= 0) {
                addRuleForUsers(cmds, new String[]{"system"}, "-A " + chain + " -p udp --dport 123", action);
            }

            boolean kernel_checked = uids.indexOf(SPECIAL_UID_KERNEL) >= 0;
            if (whitelist) {
                if (kernel_checked) {
                    // reject any other UIDs, but allow the kernel through
                    cmds.add("-A " + chain + " -m owner --uid-owner 0:999999999 -j " + AFWALL_CHAIN_NAME + "-reject");
                } else {
                    // kernel is blocked so reject everything
                    cmds.add("-A " + chain + " -j " + AFWALL_CHAIN_NAME + "-reject");
                }
            } else {
                if (kernel_checked) {
                    // allow any other UIDs, but block the kernel
                    cmds.add("-A " + chain + " -m owner --uid-owner 0:999999999 -j RETURN");
                    cmds.add("-A " + chain + " -j " + AFWALL_CHAIN_NAME + "-reject");
                }
            }
        }
    }

    private static void addRejectRules(List<String> cmds) {
        // set up reject chain to log or not log
        // this can be changed dynamically through the Firewall Logs activity

        if (G.enableLogService() && G.logTarget() != null) {
            if (G.logTarget().equals("LOG")) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-reject" + " -m limit --limit 1000/min -j LOG --log-prefix \"{AFL}\" --log-level 4 --log-uid");
            } else if (G.logTarget().equals("NFLOG")) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-reject" + " -j NFLOG --nflog-prefix \"{AFL}\" --nflog-group 40");
            }
        }
        cmds.add("-A " + AFWALL_CHAIN_NAME + "-reject" + " -j REJECT");
    }

    private static void addCustomRules(String prefName, List<String> cmds) {
        String[] customRules = G.pPrefs.getString(prefName, "").split("[\\r\\n]+");
        for (String s : customRules) {
            if (s.matches(".*\\S.*")) {
                cmds.add("#LITERAL# " + s);
            }
        }
    }

    /**
     * Reconfigure the firewall rules based on interface changes seen at runtime: tethering
     * enabled/disabled, IP address changes, etc.  This should only affect a small number of
     * rules; we want to avoid calling applyIptablesRulesImpl() too often since applying
     * 100+ rules is expensive.
     *
     * @param ctx  application context
     * @param cmds command list
     */
    private static void addInterfaceRouting(Context ctx, List<String> cmds, boolean ipv6) {
        try {
            final InterfaceDetails cfg = InterfaceTracker.getCurrentCfg(ctx);
            final boolean whitelist = G.pPrefs.getString(PREF_MODE, MODE_WHITELIST).equals(MODE_WHITELIST);
            for (String s : dynChains) {
                cmds.add("-F " + AFWALL_CHAIN_NAME + s);
            }

            if (whitelist) {
                // always allow the DHCP client full wifi access
                addRuleForUsers(cmds, new String[]{"dhcp", "wifi"}, "-A " + AFWALL_CHAIN_NAME + "-wifi-postcustom", "-j RETURN");
            }

            if (cfg.isTethered) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-postcustom -j " + AFWALL_CHAIN_NAME + "-wifi-tether");
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-postcustom -j " + AFWALL_CHAIN_NAME + "-3g-tether");
            } else {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-postcustom -j " + AFWALL_CHAIN_NAME + "-wifi-fork");
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-postcustom -j " + AFWALL_CHAIN_NAME + "-3g-fork");
            }

            if (G.enableLAN() && !cfg.isTethered) {
                if (ipv6 && !cfg.lanMaskV6.equals("")) {
                    cmds.add("-A afwall-wifi-fork -d " + cfg.lanMaskV6 + " -j afwall-wifi-lan");
                    cmds.add("-A afwall-wifi-fork '!' -d " + cfg.lanMaskV6 + " -j afwall-wifi-wan");
                } else if (!ipv6 && !cfg.lanMaskV4.equals("")) {
                    cmds.add("-A afwall-wifi-fork -d " + cfg.lanMaskV4 + " -j afwall-wifi-lan");
                    cmds.add("-A afwall-wifi-fork '!' -d " + cfg.lanMaskV4 + " -j afwall-wifi-wan");
                } else {
                    Log.i(TAG, "No ipaddress found for LAN");
                    // lets find one more time
                    //atleast allow internet - don't block completely
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-fork -j " + AFWALL_CHAIN_NAME + "-wifi-wan");
                }
            } else {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-fork -j " + AFWALL_CHAIN_NAME + "-wifi-wan");
            }

            if (G.enableRoam() && cfg.isRoaming) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-fork -j " + AFWALL_CHAIN_NAME + "-3g-roam");
            } else {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-fork -j " + AFWALL_CHAIN_NAME + "-3g-home");
            }
        } catch (Exception e) {
            Log.i(TAG, "Exception while applying shortRules " + e.getMessage());
        }

    }

    private static void applyShortRules(Context ctx, List<String> cmds, boolean ipv6) {
        Log.i(TAG, "Setting OUTPUT chain to DROP");
        cmds.add("-P OUTPUT DROP");
        addInterfaceRouting(ctx, cmds, ipv6);
        Log.i(TAG, "Setting OUTPUT chain to ACCEPT");
        cmds.add("-P OUTPUT ACCEPT");
    }

    /**
     * Purge and re-add all rules (internal implementation).
     *
     * @param ctx        application context (mandatory)
     * @param uidsWifi   list of selected UIDs for WIFI to allow or disallow (depending on the working mode)
     * @param uids3g     list of selected UIDs for 2G/3G to allow or disallow (depending on the working mode)
     * @param showErrors indicates if errors should be alerted
     */
    private static boolean applyIptablesRulesImpl(final Context ctx, List<Integer> uidsWifi, List<Integer> uids3g,
                                                  List<Integer> uidsRoam, List<Integer> uidsVPN, List<Integer> uidsLAN, final boolean showErrors,
                                                  List<String> out, boolean ipv6) {
        if (ctx == null) {
            return false;
        }

        assertBinaries(ctx, showErrors);
        if (G.isMultiUser()) {
            //FIXME: after setting this, we need to flush the iptables ?
            if (G.getMultiUserId() > 0) {
                AFWALL_CHAIN_NAME = "afwall" + G.getMultiUserId();
            }
        }
        final boolean whitelist = G.pPrefs.getString(PREF_MODE, MODE_WHITELIST).equals(MODE_WHITELIST);

        List<String> cmds = new ArrayList<String>();

        cmds.add("-P INPUT ACCEPT");
        cmds.add("-P FORWARD ACCEPT");

        // prevent data leaks due to incomplete rules
        Log.i(TAG, "Setting OUTPUT to Drop");
        cmds.add("-P OUTPUT DROP");

        for (String s : staticChains) {
            cmds.add("#NOCHK# -N " + AFWALL_CHAIN_NAME + s);
            cmds.add("-F " + AFWALL_CHAIN_NAME + s);
        }
        for (String s : dynChains) {
            cmds.add("#NOCHK# -N " + AFWALL_CHAIN_NAME + s);
            // addInterfaceRouting() will flush these chains, but not create them
        }

        cmds.add("#NOCHK# -D OUTPUT -j " + AFWALL_CHAIN_NAME);
        cmds.add("-I OUTPUT 1 -j " + AFWALL_CHAIN_NAME);

        // custom rules in afwall-{3g,wifi,reject} supersede everything else
        addCustomRules(Api.PREF_CUSTOMSCRIPT, cmds);
        cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g -j " + AFWALL_CHAIN_NAME + "-3g-postcustom");
        cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi -j " + AFWALL_CHAIN_NAME + "-wifi-postcustom");
        addRejectRules(cmds);

        if (G.enableInbound()) {
            // we don't have any rules in the INPUT chain prohibiting inbound traffic, but
            // local processes can't reply to half-open connections without this rule
            cmds.add("-A afwall -m state --state ESTABLISHED -j RETURN");
        }

        addInterfaceRouting(ctx, cmds, ipv6);

        // send wifi, 3G, VPN packets to the appropriate dynamic chain based on interface
        if (G.enableVPN()) {
            // if !enableVPN then we ignore those interfaces (pass all traffic)
            for (final String itf : ITFS_VPN) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + " -o " + itf + " -j " + AFWALL_CHAIN_NAME + "-vpn");
            }
            // KitKat policy based routing - see:
            // http://forum.xda-developers.com/showthread.php?p=48703545
            // This covers mark range 0x3c - 0x47.  The official range is believed to be
            // 0x3c - 0x45 but this is close enough.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + " -m mark --mark 0x3c/0xfffc -g " + AFWALL_CHAIN_NAME + "-vpn");
                cmds.add("-A " + AFWALL_CHAIN_NAME + " -m mark --mark 0x40/0xfff8 -g " + AFWALL_CHAIN_NAME + "-vpn");
            }
        }
        for (final String itf : ITFS_WIFI) {
            cmds.add("-A " + AFWALL_CHAIN_NAME + " -o " + itf + " -j " + AFWALL_CHAIN_NAME + "-wifi");
        }

        for (final String itf : ITFS_3G) {
            cmds.add("-A " + AFWALL_CHAIN_NAME + " -o " + itf + " -j " + AFWALL_CHAIN_NAME + "-3g");
        }

        final boolean any_wifi = uidsWifi.indexOf(SPECIAL_UID_ANY) >= 0;
        final boolean any_3g = uids3g.indexOf(SPECIAL_UID_ANY) >= 0;

        // special rules to allow 3G<->wifi tethering
        // note that this can only blacklist DNS/DHCP services, not all tethered traffic
        if (((!whitelist && (any_wifi || any_3g)) ||
                (uids3g.indexOf(SPECIAL_UID_TETHER) >= 0) || (uidsWifi.indexOf(SPECIAL_UID_TETHER) >= 0))) {

            String users[] = {"root", "nobody"};
            String action = " -j " + (whitelist ? "RETURN" : AFWALL_CHAIN_NAME + "-reject");

            // DHCP replies to client
            addRuleForUsers(cmds, users, "-A " + AFWALL_CHAIN_NAME + "-wifi-tether", "-p udp --sport=67 --dport=68" + action);

            // DNS replies to client
            addRuleForUsers(cmds, users, "-A " + AFWALL_CHAIN_NAME + "-wifi-tether", "-p udp --sport=53" + action);
            addRuleForUsers(cmds, users, "-A " + AFWALL_CHAIN_NAME + "-wifi-tether", "-p tcp --sport=53" + action);

            // DNS requests to upstream servers
            addRuleForUsers(cmds, users, "-A " + AFWALL_CHAIN_NAME + "-3g-tether", "-p udp --dport=53" + action);
            addRuleForUsers(cmds, users, "-A " + AFWALL_CHAIN_NAME + "-3g-tether", "-p tcp --dport=53" + action);
        }

        // if tethered, try to match the above rules (if enabled).  no match -> fall through to the
        // normal 3G/wifi rules
        cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-tether -j " + AFWALL_CHAIN_NAME + "-wifi-fork");
        cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-tether -j " + AFWALL_CHAIN_NAME + "-3g-fork");

        // NOTE: we still need to open a hole to let WAN-only UIDs talk to a DNS server
        // on the LAN
        if (whitelist) {
            cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-lan -p udp --dport 53 -j RETURN");
        }

        // now add the per-uid rules for 3G home, 3G roam, wifi WAN, wifi LAN, VPN
        // in whitelist mode the last rule in the list routes everything else to afwall-reject
        addRulesForUidlist(cmds, uids3g, AFWALL_CHAIN_NAME + "-3g-home", whitelist);
        addRulesForUidlist(cmds, uidsRoam, AFWALL_CHAIN_NAME + "-3g-roam", whitelist);
        addRulesForUidlist(cmds, uidsWifi, AFWALL_CHAIN_NAME + "-wifi-wan", whitelist);
        addRulesForUidlist(cmds, uidsLAN, AFWALL_CHAIN_NAME + "-wifi-lan", whitelist);
        addRulesForUidlist(cmds, uidsVPN, AFWALL_CHAIN_NAME + "-vpn", whitelist);

        Log.i(TAG, "Setting OUTPUT to Accept");
        cmds.add("-P OUTPUT ACCEPT");

        //look for custom rules
        if (ipv6) {
            if (G.blockIPv6()) {
                setBinaryPath(ctx, true);
                cmds.add("-P INPUT DROP");
                cmds.add("-P FORWARD DROP");
                cmds.add("-P OUTPUT DROP");
            } else {
                if (G.enableIPv6()) {
                    setBinaryPath(ctx, true);
                    cmds.add("-P INPUT ACCEPT");
                    cmds.add("-P FORWARD ACCEPT");
                    cmds.add("-P OUTPUT ACCEPT");
                }
            }
        }

        iptablesCommands(cmds, out, ipv6);
        return true;
    }

    /**
     * Add the repetitive parts (ipPath and such) to an iptables command list
     *
     * @param in  Commands in the format: "-A foo ...", "#NOCHK# -A foo ...", or "#LITERAL# <UNIX command>"
     * @param out A list of UNIX commands to execute
     */
    private static void iptablesCommands(List<String> in, List<String> out, boolean ipv6) {
        boolean firstLit = true;
        for (String s : in) {
            if (s.matches("#LITERAL# .*")) {
                if (firstLit) {
                    // export vars for the benefit of custom scripts
                    // "true" is a dummy command which needs to return success
                    firstLit = false;
                    out.add("export IPTABLES=\"" + ipPath + "\"; "
                            + "export BUSYBOX=\"" + bbPath + "\"; "
                            + "export IPV6=" + (ipv6 ? "1" : "0") + "; "
                            + "true");
                }
                out.add(s.replaceFirst("^#LITERAL# ", ""));
            } else if (s.matches("#NOCHK# .*")) {
                out.add(s.replaceFirst("^#NOCHK# ", "#NOCHK# " + ipPath + " "));
            } else {
                out.add(ipPath + " " + s);
            }
        }
    }

    private static void fixupLegacyCmds(List<String> cmds) {
        for (int i = 0; i < cmds.size(); i++) {
            String s = cmds.get(i);
            if (s.matches("#NOCHK# .*")) {
                s = s.replaceFirst("^#NOCHK# ", "");
            } else {
                s += " || exit";
            }
            cmds.set(i, s);
        }
    }


    /**
     * Purge and re-add all saved rules (not in-memory ones).
     * This is much faster than just calling "applyIptablesRules", since it don't need to read installed applications.
     *
     * @param ctx        application context (mandatory)
     * @param showErrors indicates if errors should be alerted
     * @param callback   If non-null, use a callback instead of blocking the current thread
     */
    public static boolean applySavedIptablesRules(Context ctx, boolean showErrors, RootCommand callback) {
        if (ctx == null) {
            return false;
        }
        try {
            Log.i(TAG, "applySavedIptablesRules invoked");
            initSpecial();

            final String savedPkg_wifi_uid = G.pPrefs.getString(PREF_WIFI_PKG_UIDS, "");
            final String savedPkg_3g_uid = G.pPrefs.getString(PREF_3G_PKG_UIDS, "");
            final String savedPkg_roam_uid = G.pPrefs.getString(PREF_ROAMING_PKG_UIDS, "");
            final String savedPkg_vpn_uid = G.pPrefs.getString(PREF_VPN_PKG_UIDS, "");
            final String savedPkg_lan_uid = G.pPrefs.getString(PREF_LAN_PKG_UIDS, "");

            boolean returnValue = false;
            List<String> cmds = new ArrayList<String>();

            setBinaryPath(ctx, false);
            returnValue = applyIptablesRulesImpl(ctx,
                    getListFromPref(savedPkg_wifi_uid),
                    getListFromPref(savedPkg_3g_uid),
                    getListFromPref(savedPkg_roam_uid),
                    getListFromPref(savedPkg_vpn_uid),
                    getListFromPref(savedPkg_lan_uid),
                    showErrors,
                    cmds, false);
            if (returnValue == false) {
                return false;
            }

            if (G.enableIPv6()) {
                setBinaryPath(ctx, true);
                returnValue = applyIptablesRulesImpl(ctx,
                        getListFromPref(savedPkg_wifi_uid),
                        getListFromPref(savedPkg_3g_uid),
                        getListFromPref(savedPkg_roam_uid),
                        getListFromPref(savedPkg_vpn_uid),
                        getListFromPref(savedPkg_lan_uid),
                        showErrors,
                        cmds, true);
                if (returnValue == false) {
                    return false;
                }
            } else {
                if (G.blockIPv6()) {
                    setBinaryPath(ctx, true);
                    List blockRules = new ArrayList<>();
                    blockRules.add("-P INPUT DROP");
                    blockRules.add("-P FORWARD DROP");
                    blockRules.add("-P OUTPUT DROP");
                    iptablesCommands(blockRules, cmds, true);
                }
            }

            rulesUpToDate = true;
            // update UI
            callback.setRetryExitCode(IPTABLES_TRY_AGAIN).runThread(ctx, cmds);
            return true;
        } catch (Exception e) {
            Log.d(TAG, "Exception while applying rules: " + e.getMessage());
            allowDefaultChains(ctx);
            return false;
        }
    }

    /*@Deprecated
    public static boolean applySavedIptablesRules(Context ctx, boolean showErrors) {
        return applySavedIptablesRules(ctx, showErrors, null);
    }*/

    public static boolean fastApply(Context ctx, RootCommand callback) {

        if (!rulesUpToDate) {
            return applySavedIptablesRules(ctx, true, callback);
        }
        Log.i(TAG, "Using fastApply");
        List<String> out = new ArrayList<String>();
        List<String> cmds;

        cmds = new ArrayList<String>();
        setBinaryPath(ctx, false);
        applyShortRules(ctx, cmds, false);
        iptablesCommands(cmds, out, false);
        if (G.enableIPv6()) {
            setBinaryPath(ctx, true);
            cmds = new ArrayList<String>();
            applyShortRules(ctx, cmds, true);
            cmds.add("-P INPUT ACCEPT");
            cmds.add("-P FORWARD ACCEPT");
            cmds.add("-P OUTPUT ACCEPT");
            iptablesCommands(cmds, out, true);
        } else if (G.blockIPv6()) {
            setBinaryPath(ctx, true);
            cmds = new ArrayList<String>();
            cmds.add("-P INPUT DROP");
            cmds.add("-P FORWARD DROP");
            cmds.add("-P OUTPUT DROP");
            iptablesCommands(cmds, out, true);
        }
        callback.setRetryExitCode(IPTABLES_TRY_AGAIN).runThread(ctx, out);
        return true;
    }

    /**
     * Save current rules using the preferences storage.
     *
     * @param ctx application context (mandatory)
     */
    public static void saveRules(Context ctx) {

        rulesUpToDate = false;

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        List<PackageInfoData> apps = getApps(ctx, null);

        if (apps != null) {
            // Builds a pipe-separated list of names
            StringBuilder newpkg_wifi = new StringBuilder();
            StringBuilder newpkg_3g = new StringBuilder();
            StringBuilder newpkg_roam = new StringBuilder();
            StringBuilder newpkg_vpn = new StringBuilder();
            StringBuilder newpkg_lan = new StringBuilder();

            for (int i = 0; i < apps.size(); i++) {
                if (apps.get(i) != null) {
                    if (apps.get(i).selected_wifi) {
                        if (newpkg_wifi.length() != 0) newpkg_wifi.append('|');
                        newpkg_wifi.append(apps.get(i).uid);

                    }
                    if (apps.get(i).selected_3g) {
                        if (newpkg_3g.length() != 0) newpkg_3g.append('|');
                        newpkg_3g.append(apps.get(i).uid);
                    }
                    if (G.enableRoam() && apps.get(i).selected_roam) {
                        if (newpkg_roam.length() != 0) newpkg_roam.append('|');
                        newpkg_roam.append(apps.get(i).uid);
                    }

                    if (G.enableVPN() && apps.get(i).selected_vpn) {
                        if (newpkg_vpn.length() != 0) newpkg_vpn.append('|');
                        newpkg_vpn.append(apps.get(i).uid);
                    }

                    if (G.enableLAN() && apps.get(i).selected_lan) {
                        if (newpkg_lan.length() != 0) newpkg_lan.append('|');
                        newpkg_lan.append(apps.get(i).uid);
                    }
                }
            }
            // save the new list of UIDs
            Editor edit = prefs.edit();
            edit.putString(PREF_WIFI_PKG_UIDS, newpkg_wifi.toString());
            edit.putString(PREF_3G_PKG_UIDS, newpkg_3g.toString());
            edit.putString(PREF_ROAMING_PKG_UIDS, newpkg_roam.toString());
            edit.putString(PREF_VPN_PKG_UIDS, newpkg_vpn.toString());
            edit.putString(PREF_LAN_PKG_UIDS, newpkg_lan.toString());

            edit.commit();
        }

    }

    /*public static void checkPermission(Context ctx) {
        int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 5469;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(ctx)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + ctx.getPackageName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                //((Activity)ctx).startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE);
                ctx.startActivity(intent);
            }
        }
    }*/

    /**
     * Purge all iptables rules.
     *
     * @param ctx        mandatory context
     * @param showErrors indicates if errors should be alerted
     * @param callback   If non-null, use a callback instead of blocking the current thread
     * @return true if the rules were purged
     */
    public static boolean purgeIptables(Context ctx, boolean showErrors, RootCommand callback) {

        List<String> cmds = new ArrayList<String>();
        List<String> out = new ArrayList<String>();

        for (String s : staticChains) {
            cmds.add("-F " + AFWALL_CHAIN_NAME + s);
        }
        for (String s : dynChains) {
            cmds.add("-F " + AFWALL_CHAIN_NAME + s);
        }
        //make sure reset the OUTPUT chain to accept state.
        cmds.add("-P OUTPUT ACCEPT");

        //Delete only when the afwall chain exist !
        cmds.add("-D OUTPUT -j " + AFWALL_CHAIN_NAME);

        addCustomRules(Api.PREF_CUSTOMSCRIPT2, cmds);

        try {
            assertBinaries(ctx, showErrors);

            // IPv4
            setBinaryPath(ctx, false);
            iptablesCommands(cmds, out, false);

            // IPv6
            if (G.enableIPv6()) {
                setBinaryPath(ctx, true);
                iptablesCommands(cmds, out, true);
            }

            if (callback != null) {
                callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
            } else {
                fixupLegacyCmds(out);
                if (runScriptAsRoot(ctx, out, new StringBuilder()) == -1) {
                    if (showErrors) toast(ctx, ctx.getString(R.string.error_purge));
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /*@Deprecated
    public static boolean purgeIptables(Context ctx, boolean showErrors) {
        // warning: this is a blocking call
        return purgeIptables(ctx, showErrors, null);
    }*/

    /**
     * Retrieve the current set of IPv4 or IPv6 rules and pass it to a callback
     *
     * @param ctx      application context
     * @param callback callback to receive rule list
     * @param useIPV6  true to list IPv6 rules, false to list IPv4 rules
     */
    public static void fetchIptablesRules(Context ctx, boolean useIPV6, RootCommand callback) {
        List<String> cmds = new ArrayList<String>();
        List<String> out = new ArrayList<String>();
        cmds.add("-n -v -L");
        setBinaryPath(ctx, false);
        iptablesCommands(cmds, out, false);
        if (useIPV6) {
            setBinaryPath(ctx, true);
            iptablesCommands(cmds, out, true);
        }
        callback.run(ctx, out);
    }

    /**
     * Run a list of commands with both iptables and ip6tables
     *
     * @param ctx      application context
     * @param cmds     list of commands to run
     * @param callback callback for completion
     */
    public static void apply46(Context ctx, List<String> cmds, RootCommand callback) {
        List<String> out = new ArrayList<String>();

        setBinaryPath(ctx, false);
        iptablesCommands(cmds, out, false);

        if (G.enableIPv6()) {
            setBinaryPath(ctx, true);
            iptablesCommands(cmds, out, true);
        }
        callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
    }

    //Cleanup unused shell opened by logservice
    public static void cleanupUid() {
        try {
            Set<String> uids = G.storedPid();
            if (uids != null && uids.size() > 0) {
                Shell.Interactive tempSession = new Shell.Builder().useSU().open();
                for (String uid : uids) {
                    Log.i(Api.TAG, "Cleaning up previous uid: " + uid);
                    tempSession.addCommand("kill -9 " + uid);
                }
                G.storedPid(new HashSet());
                if (tempSession != null) {
                    tempSession.kill();
                    tempSession.close();
                }
            }
        } catch (ClassCastException e) {
            Log.e(TAG, "ClassCastException in cleanupUid: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Exception in cleanupUid: " + e.getMessage());
        }
    }


    public static void applyIPv6Quick(Context ctx, List<String> cmds, RootCommand callback) {
        List<String> out = new ArrayList<String>();
        setBinaryPath(ctx, true);
        iptablesCommands(cmds, out, true);
        callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
    }

    public static void applyQuick(Context ctx, List<String> cmds, RootCommand callback) {
        List<String> out = new ArrayList<String>();

        setBinaryPath(ctx, false);
        iptablesCommands(cmds, out, false);

        //related to #511, disable ipv6 but use startup leak.
        if (G.enableIPv6() || G.fixLeak()) {
            setBinaryPath(ctx, true);
            iptablesCommands(cmds, out, true);
        }
        callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
    }

    /**
     * Delete all kingroot firewall rules.  For diagnostic purposes only.
     *
     * @param ctx      application context
     * @param callback callback for completion
     */
    public static void flushAllRules(Context ctx, RootCommand callback) {
        List<String> cmds = new ArrayList<String>();
        cmds.add("-F");
        cmds.add("-X");
        apply46(ctx, cmds, callback);
    }

    /**
     * Enable or disable logging by rewriting the afwall-reject chain.  Logging
     * will be enabled or disabled based on the preference setting.
     *
     * @param ctx      application context
     * @param callback callback for completion
     */
    public static void updateLogRules(Context ctx, RootCommand callback) {
        if (!isEnabled(ctx)) {
            return;
        }
        List<String> cmds = new ArrayList<String>();
        cmds.add("#NOCHK# -N " + AFWALL_CHAIN_NAME + "-reject");
        cmds.add("-F " + AFWALL_CHAIN_NAME + "-reject");
        addRejectRules(cmds);
        apply46(ctx, cmds, callback);
    }

    /**
     * Clear firewall logs by purging dmesg
     *
     * @param ctx      application context
     * @param callback Callback for completion status
     */
    public static void clearLog(Context ctx, RootCommand callback) {
        callback.run(ctx, getBusyBoxPath(ctx, true) + " dmesg -c");
    }

    public static void purgeOldLog() {
        long purgeInterval = System.currentTimeMillis() - 604800000;
        new Delete().from(LogData.class).where(LogData_Table.timestamp.lessThan(purgeInterval)).async().execute();
    }

    /**
     * Fetch kernel logs via busybox dmesg.  This will include {AFL} lines from
     * logging rejected packets.
     *
     * @return true if logging is enabled, false otherwise
     */
    public static List<LogData> fetchLogs() {
        //load hour data due to performance issue with old view
        long loadInterval = System.currentTimeMillis() - 3600000;
        List<LogData> log = SQLite.select()
                .from(LogData.class)
                .where(LogData_Table.timestamp.greaterThan(loadInterval))
                .orderBy(LogData_Table.timestamp, true)
                .queryList();
        purgeOldLog();
        //fetch last 100 records
        if (log != null && log.size() > 100) {
            return log.subList((log.size() - 100), log.size());
        } else {
            return log;
        }
    }

    /**
     * List all interfaces via "ifconfig -a"
     *
     * @param ctx      application context
     * @param callback Callback for completion status
     */
    public static void runIfconfig(Context ctx, RootCommand callback) {
        callback.run(ctx, getBusyBoxPath(ctx, true) + " ifconfig -a");
    }

    public static void runNetworkInterface(Context ctx, RootCommand callback) {
        callback.run(ctx, getBusyBoxPath(ctx, true) + " ls /sys/class/net");
    }

   /* public boolean isSuPackage(PackageManager pm, String suPackage) {
        boolean found = false;
        try {
            PackageInfo info = pm.getPackageInfo(suPackage, 0);
            if (info.applicationInfo != null) {
                found = true;
            }
            //found = s + " v" + info.versionName;
        } catch (NameNotFoundException e) {
        }
        return found;
    }*/


    /**
     * @param ctx application context (mandatory)
     * @return a list of applications
     */
    public static List<PackageInfoData> getApps(Context ctx, GetAppList appList) {

        initSpecial();
        if (applications != null && applications.size() > 0) {
            // return cached instance
            return applications;
        }

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String savedPkg_wifi_uid = prefs.getString(PREF_WIFI_PKG_UIDS, "");
        String savedPkg_3g_uid = prefs.getString(PREF_3G_PKG_UIDS, "");
        String savedPkg_roam_uid = prefs.getString(PREF_ROAMING_PKG_UIDS, "");
        String savedPkg_vpn_uid = prefs.getString(PREF_VPN_PKG_UIDS, "");
        String savedPkg_lan_uid = prefs.getString(PREF_LAN_PKG_UIDS, "");

        List<Integer> selected_wifi;
        List<Integer> selected_3g;
        List<Integer> selected_roam = new ArrayList<>();
        List<Integer> selected_vpn = new ArrayList<>();
        List<Integer> selected_lan = new ArrayList<>();


        selected_wifi = getListFromPref(savedPkg_wifi_uid);
        selected_3g = getListFromPref(savedPkg_3g_uid);

        if (G.enableRoam()) {
            selected_roam = getListFromPref(savedPkg_roam_uid);
        }
        if (G.enableVPN()) {
            selected_vpn = getListFromPref(savedPkg_vpn_uid);
        }
        if (G.enableLAN()) {
            selected_lan = getListFromPref(savedPkg_lan_uid);
        }
        //revert back to old approach

        //always use the defaul preferences to store cache value - reduces the application usage size
        SharedPreferences cachePrefs = ctx.getSharedPreferences(DEFAULT_PREFS_NAME, Context.MODE_PRIVATE);

        int count = 0;
        try {
            PackageManager pkgmanager = ctx.getPackageManager();
            List<ApplicationInfo> installed = pkgmanager.getInstalledApplications(PackageManager.GET_META_DATA);
            SparseArray<PackageInfoData> syncMap = new SparseArray<>();
            Editor edit = cachePrefs.edit();
            boolean changed = false;
            String name = null;
            String cachekey = null;
            String cacheLabel = "cache.label.";
            PackageInfoData app = null;
            ApplicationInfo apinfo = null;

            Date install = new Date();
            install.setTime(System.currentTimeMillis() - (120000));

            for (int i = 0; i < installed.size(); i++) {
                //for (ApplicationInfo apinfo : installed) {
                count = count + 1;
                apinfo = installed.get(i);

                if (appList != null) {
                    appList.doProgress(count);
                }

                boolean firstseen = false;
                app = syncMap.get(apinfo.uid);
                // filter applications which are not allowed to access the Internet
                if (app == null && PackageManager.PERMISSION_GRANTED != pkgmanager.checkPermission(Manifest.permission.INTERNET, apinfo.packageName)) {
                    continue;
                }
                // try to get the application label from our cache - getApplicationLabel() is horribly slow!!!!
                cachekey = cacheLabel + apinfo.packageName;
                name = prefs.getString(cachekey, "");
                if (name.length() == 0 || isRecentlyInstalled(apinfo.packageName)) {
                    // get label and put on cache
                    name = pkgmanager.getApplicationLabel(apinfo).toString();
                    edit.putString(cachekey, name);
                    changed = true;
                    firstseen = true;
                }
                if (app == null) {
                    app = new PackageInfoData();
                    app.uid = apinfo.uid;
                    app.installTime = new File(apinfo.sourceDir).lastModified();
                    app.names = new ArrayList<String>();
                    app.names.add(name);
                    app.appinfo = apinfo;
                    app.pkgName = apinfo.packageName;
                    syncMap.put(apinfo.uid, app);
                } else {
                    app.names.add(name);
                }
                app.firstseen = firstseen;
                // check if this application is selected
                if (!app.selected_wifi && Collections.binarySearch(selected_wifi, app.uid) >= 0) {
                    app.selected_wifi = true;
                }
                if (!app.selected_3g && Collections.binarySearch(selected_3g, app.uid) >= 0) {
                    app.selected_3g = true;
                }
                if (G.enableRoam() && !app.selected_roam && Collections.binarySearch(selected_roam, app.uid) >= 0) {
                    app.selected_roam = true;
                }
                if (G.enableVPN() && !app.selected_vpn && Collections.binarySearch(selected_vpn, app.uid) >= 0) {
                    app.selected_vpn = true;
                }
                if (G.enableLAN() && !app.selected_lan && Collections.binarySearch(selected_lan, app.uid) >= 0) {
                    app.selected_lan = true;
                }

            }

            List<PackageInfoData> specialData = new ArrayList<>();
            specialData.add(new PackageInfoData(SPECIAL_UID_ANY, ctx.getString(R.string.all_item), "dev.afwall.special.any"));
            specialData.add(new PackageInfoData(SPECIAL_UID_KERNEL, ctx.getString(R.string.kernel_item), "dev.afwall.special.kernel"));
            specialData.add(new PackageInfoData(SPECIAL_UID_TETHER, ctx.getString(R.string.tethering_item), "dev.afwall.special.tether"));
            specialData.add(new PackageInfoData(SPECIAL_UID_NTP, ctx.getString(R.string.ntp_item), "dev.afwall.special.ntp"));
            for (String acct : specialAndroidAccounts) {
                String dsc = getSpecialDescription(ctx, acct);
                String pkg = "dev.afwall.special." + acct;
                specialData.add(new PackageInfoData(acct, dsc, pkg));
            }

            if (specialApps == null) {
                specialApps = new HashMap<String, Integer>();
            }
            for (int i = 0; i < specialData.size(); i++) {
                app = specialData.get(i);
                specialApps.put(app.pkgName, app.uid);
                //default DNS/NTP
                if (app.uid != -1 && syncMap.get(app.uid) == null) {
                    // check if this application is allowed
                    if (!app.selected_wifi && Collections.binarySearch(selected_wifi, app.uid) >= 0) {
                        app.selected_wifi = true;
                    }
                    if (!app.selected_3g && Collections.binarySearch(selected_3g, app.uid) >= 0) {
                        app.selected_3g = true;
                    }
                    if (G.enableRoam() && !app.selected_roam && Collections.binarySearch(selected_roam, app.uid) >= 0) {
                        app.selected_roam = true;
                    }
                    if (G.enableVPN() && !app.selected_vpn && Collections.binarySearch(selected_vpn, app.uid) >= 0) {
                        app.selected_vpn = true;
                    }
                    if (G.enableLAN() && !app.selected_lan && Collections.binarySearch(selected_lan, app.uid) >= 0) {
                        app.selected_lan = true;
                    }
                    syncMap.put(app.uid, app);
                }
            }

            if (changed) {
                edit.commit();
            }
            /* convert the map into an array */
            applications = Collections.synchronizedList(new ArrayList<PackageInfoData>());
            for (int i = 0; i < syncMap.size(); i++) {
                applications.add(syncMap.valueAt(i));
            }

            return applications;
        } catch (Exception e) {
            //toast(ctx, ctx.getString(R.string.error_common) + e);
        }
        return null;
    }

    private static boolean isRecentlyInstalled(String packageName) {
        boolean isRecent = false;
        try {
            if (recentlyInstalled != null && recentlyInstalled.contains(packageName)) {
                isRecent = true;
                recentlyInstalled.remove(packageName);
            }
        } catch (Exception e) {
        }
        return isRecent;
    }

    private static List<Integer> getListFromPref(String savedPkg_uid) {
        StringTokenizer tok = new StringTokenizer(savedPkg_uid, "|");
        List<Integer> listUids = new ArrayList<Integer>();
        while (tok.hasMoreTokens()) {
            String uid = tok.nextToken();
            if (!uid.equals("")) {
                try {
                    listUids.add(Integer.parseInt(uid));
                } catch (Exception ex) {

                }
            }
        }
        // Sort the array to allow using "Arrays.binarySearch" later
        Collections.sort(listUids);
        return listUids;
    }

    public static void removeNotification(Context context) {

        final int NOTIF_ID = 33341;
        String notificationText = "";

        NotificationManager mNotificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.cancel(NOTIF_ID);
    }

    public static boolean isAppAllowed(Context context, ApplicationInfo applicationInfo, SharedPreferences pPrefs) {
        InterfaceDetails details = InterfaceTracker.getCurrentCfg(context);
        //allow webview to download since webview requires INTERNET permission
        if (applicationInfo.packageName.equals("com.android.webview") || applicationInfo.packageName.equals("com.google.android.webview")) {
            return true;
        }
        if (details != null && details.netEnabled) {
            String mode = pPrefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST);
            Log.i(TAG, "Calling isAppAllowed method from DM with Mode: " + mode);
            switch ((details.netType)) {
                case ConnectivityManager.TYPE_WIFI:
                    final String savedPkg_wifi_uid = pPrefs.getString(PREF_WIFI_PKG_UIDS, "");
                    Log.i(TAG, "DM check for UID: " + applicationInfo.uid);
                    Log.i(TAG, "DM allowed UIDs: " + savedPkg_wifi_uid);
                    if (mode.equals(Api.MODE_WHITELIST) && savedPkg_wifi_uid.contains(applicationInfo.uid + "")) {
                        return true;
                    } else if (mode.equals(Api.MODE_BLACKLIST) && !savedPkg_wifi_uid.contains(applicationInfo.uid + "")) {
                        return true;
                    } else {
                        return false;
                    }

                case ConnectivityManager.TYPE_MOBILE:
                    String savedPkg_3g_uid = pPrefs.getString(PREF_3G_PKG_UIDS, "");
                    if (details.isRoaming) {
                        savedPkg_3g_uid = pPrefs.getString(PREF_ROAMING_PKG_UIDS, "");
                    }
                    Log.i(TAG, "DM check for UID: " + applicationInfo.uid);
                    Log.i(TAG, "DM allowed UIDs: " + savedPkg_3g_uid);
                    if (mode.equals(Api.MODE_WHITELIST) && savedPkg_3g_uid.contains(applicationInfo.uid + "")) {
                        return true;
                    } else if (mode.equals(Api.MODE_BLACKLIST) && !savedPkg_3g_uid.contains(applicationInfo.uid + "")) {
                        return true;
                    } else {
                        return false;
                    }
            }
        }

        return true;
    }


    private static class RunCommand extends AsyncTask<Object, List<String>, Integer> {

        private int exitCode = -1;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Integer doInBackground(Object... params) {
            @SuppressWarnings("unchecked")
            List<String> commands = (List<String>) params[0];
            StringBuilder res = (StringBuilder) params[1];
            try {
                if (!SU.available())
                    return exitCode;
                if (commands != null && commands.size() > 0) {
                    List<String> output = SU.run(commands);
                    if (output != null) {
                        exitCode = 0;
                        if (output.size() > 0) {
                            for (String str : output) {
                                res.append(str);
                                res.append("\n");
                            }
                        }
                    } else {
                        exitCode = 1;
                    }
                }
            } catch (Exception ex) {
                if (res != null)
                    res.append("\n" + ex);
            }
            return exitCode;
        }


    }

    /**
     * Runs a script as root (multiple commands separated by "\n")
     *
     * @param ctx    mandatory context
     * @param script the script to be executed
     * @param res    the script output response (stdout + stderr)
     * @return the script exit code
     * @throws IOException on any error executing the script, or writing it to disk
     */
    public static int runScriptAsRoot(Context ctx, List<String> script, StringBuilder res) throws IOException {
        int returnCode = -1;

        if ((Looper.myLooper() != null) && (Looper.myLooper() == Looper.getMainLooper())) {
            Log.e(TAG, "runScriptAsRoot should not be called from the main thread\nCall Trace:\n");
            for (StackTraceElement e : new Throwable().getStackTrace()) {
                Log.e(TAG, e.toString());
            }
        }

        try {
            returnCode = new RunCommand().execute(script, res, ctx).get();
        } catch (RejectedExecutionException r) {
            Log.e(TAG, "runScript failed: " + r.getLocalizedMessage());
        } catch (InterruptedException e) {
            Log.e(TAG, "Caught InterruptedException");
        } catch (ExecutionException e) {
            Log.e(TAG, "runScript failed: " + e.getLocalizedMessage());
        } catch (Exception e) {
            Log.e(TAG, "runScript failed: " + e.getLocalizedMessage());
        }

        return returnCode;
    }

    private static boolean installBinary(Context ctx, int resId, String filename) {
        try {
            File f = new File(ctx.getDir("bin", 0), filename);
            if (f.exists()) {
                f.delete();
            }
            copyRawFile(ctx, resId, f, "0755");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "installBinary failed: " + e.getLocalizedMessage());
            return false;
        }
    }

    private static boolean migrateSettings(Context ctx, int lastVer, int currentVer) {
        if (lastVer <= 138) {
            // migrate busybox/iptables path settings from <= 1.2.7-BETA
            if (G.bb_path().equals("1")) {
                G.bb_path("system");
            } else if (G.bb_path().equals("2")) {
                G.bb_path("builtin");
            }
            if (G.ip_path().equals("1")) {
                G.ip_path("system");
            } else if (G.ip_path().equals("2")) {
                G.ip_path("auto");
            }
        }
        return true;
    }

    /**
     * Asserts that the binary files are installed in the cache directory.
     *
     * @param ctx        context
     * @param showErrors indicates if errors should be alerted
     * @return false if the binary files could not be installed
     */
    public static boolean assertBinaries(Context ctx, boolean showErrors) {
        int currentVer = -1, lastVer = -1;

        try {
            currentVer = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
            lastVer = G.appVersion();
            if (lastVer == currentVer) {
                return true;
            }
        } catch (NameNotFoundException e) {
            Log.e(TAG, "packageManager can't look up versionCode");
        }

        final String[] abis;
        if (Build.VERSION.SDK_INT > 21) {
            abis = Build.SUPPORTED_ABIS;
        } else {
            abis = new String[]{Build.CPU_ABI, Build.CPU_ABI2};
        }

        boolean ret = false;

        for (String abi : abis) {
            if (abi.startsWith("x86")) {
                ret = installBinary(ctx, R.raw.busybox_x86, "busybox") &&
                        installBinary(ctx, R.raw.iptables_x86, "iptables") &&
                        installBinary(ctx, R.raw.ip6tables_x86, "ip6tables") &&
                        installBinary(ctx, R.raw.nflog_x86, "nflog") &&
                        installBinary(ctx, R.raw.run_pie_x86, "run_pie");
            } else if (abi.startsWith("mips")) {
                ret = installBinary(ctx, R.raw.busybox_mips, "busybox") &&
                        installBinary(ctx, R.raw.iptables_mips, "iptables") &&
                        installBinary(ctx, R.raw.ip6tables_mips, "ip6tables") &&
                        installBinary(ctx, R.raw.nflog_mips, "nflog") &&
                        installBinary(ctx, R.raw.run_pie_mips, "run_pie");
            } else {
                // default to ARM
                ret = installBinary(ctx, R.raw.busybox_arm, "busybox") &&
                        installBinary(ctx, R.raw.iptables_arm, "iptables") &&
                        installBinary(ctx, R.raw.ip6tables_arm, "ip6tables") &&
                        installBinary(ctx, R.raw.nflog_arm, "nflog") &&
                        installBinary(ctx, R.raw.run_pie_arm, "run_pie");
            }
            Log.d(TAG, "binary installation for " + abi + (ret ? " succeeded" : " failed"));
        }

        // arch-independent scripts
        ret &= installBinary(ctx, R.raw.afwallstart, "afwallstart");
        //Log.d(TAG, "binary installation for " + abi + (ret ? " succeeded" : " failed"));

        if (showErrors) {
            if (ret) {
                toast(ctx, ctx.getString(R.string.toast_bin_installed));
            } else {
                toast(ctx, ctx.getString(R.string.error_binary));
            }
        }

        if (currentVer > 0) {
            if (migrateSettings(ctx, lastVer, currentVer) == false && showErrors) {
                toast(ctx, ctx.getString(R.string.error_migration));
            }
        }

        if (ret == true && currentVer > 0) {
            // this indicates that migration from the old version was successful.
            G.appVersion(currentVer);
        }

        return ret;
    }

	/*public static void displayToasts(Context context, int id, int length) {
        Toast.makeText(context, context.getString(id), length).show();
	}

	public static void displayToasts(Context context, String text, int length) {
		Toast.makeText(context, text, length).show();
	}*/

    /**
     * Check if the firewall is enabled
     *
     * @param ctx mandatory context
     * @return boolean
     */
    public static boolean isEnabled(Context ctx) {
        if (ctx == null) return false;
        boolean flag = ctx.getSharedPreferences(PREF_FIREWALL_STATUS, Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false);
        //Log.d(TAG, "Checking for IsEnabled, Flag:" + flag);
        return flag;
    }

    /**
     * Defines if the firewall is enabled and broadcasts the new status
     *
     * @param ctx     mandatory context
     * @param enabled enabled flag
     */
    public static void setEnabled(Context ctx, boolean enabled, boolean showErrors) {
        if (ctx == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_FIREWALL_STATUS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_ENABLED, false) == enabled) {
            return;
        }
        rulesUpToDate = false;

        Editor edit = prefs.edit();
        edit.putBoolean(PREF_ENABLED, enabled);
        if (!edit.commit()) {
            if (showErrors) toast(ctx, ctx.getString(R.string.error_write_pref));
            return;
        }

        if (G.activeNotification()) {
            showNotification(Api.isEnabled(ctx), ctx);
        }

		/* notify */
        Intent message = new Intent(Api.STATUS_CHANGED_MSG);
        message.putExtra(Api.STATUS_EXTRA, enabled);
        ctx.sendBroadcast(message);
    }


    private static boolean removePackageRef(Context ctx, String pkg, int pkgRemoved, Editor editor, String store) {
        StringBuilder newuids = new StringBuilder();
        StringTokenizer tok = new StringTokenizer(pkg, "|");
        boolean changed = false;
        String uid_str = pkgRemoved + "";
        while (tok.hasMoreTokens()) {
            String token = tok.nextToken();
            if (uid_str.equals(token)) {
                changed = true;
            } else {
                if (newuids.length() > 0)
                    newuids.append('|');
                newuids.append(token);
            }
        }
        if (changed) {
            editor.putString(store, newuids.toString());
        }
        return changed;
    }

    /**
     * Remove the cache.label key from preferences, so that next time the app appears on the top
     *
     * @param pkgName
     * @param ctx
     */
    public static void removeCacheLabel(String pkgName, Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("AFWallPrefs", Context.MODE_PRIVATE);
        try {
            prefs.edit().remove("cache.label." + pkgName).commit();
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }

    /**
     * Cleansup the uninstalled packages from the cache - will have slight performance
     *
     * @param ctx
     */
    public static void removeAllUnusedCacheLabel(Context ctx) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences("AFWallPrefs", Context.MODE_PRIVATE);
            final String cacheLabel = "cache.label.";
            String pkgName;
            String cacheKey;
            PackageManager pm = ctx.getPackageManager();
            Map<String, ?> keys = prefs.getAll();
            for (Map.Entry<String, ?> entry : keys.entrySet()) {
                if (entry.getKey().startsWith(cacheLabel)) {
                    cacheKey = entry.getKey();
                    pkgName = entry.getKey().replace(cacheLabel, "");
                    if (prefs.getString(cacheKey, "").length() > 0 && !isPackageExists(pm, pkgName)) {
                        prefs.edit().remove(cacheKey).commit();
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    /**
     * Cleanup the cache from profiles - Improve performance.
     *
     * @param pm
     * @param targetPackage
     */

    public static boolean isPackageExists(PackageManager pm, String targetPackage) {
        try {
            pm.getPackageInfo(targetPackage, PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            return false;
        }
        return true;
    }

    public static PackageInfo getPackageDetails(Context ctx, String targetPackage) {
        try {
            final PackageManager pm = ctx.getPackageManager();
            return pm.getPackageInfo(targetPackage, PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    public static PackageInfo getPackageDetails(Context ctx, int uid) {
        try {
            final PackageManager pm = ctx.getPackageManager();
            String[] packages = pm.getPackagesForUid(uid);
            if (packages != null && packages.length > 0) {
                return pm.getPackageInfo(packages[0], PackageManager.GET_META_DATA);
            } else {
                return null;
            }
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Called when an application in removed (un-installed) from the system.
     * This will look for that application in the selected list and update the persisted values if necessary
     *
     * @param ctx mandatory app context
     */
    public static void applicationRemoved(Context ctx, int pkgRemoved) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Editor editor = prefs.edit();
        // allowed application names separated by pipe '|' (persisted)
        String savedPks_wifi = prefs.getString(PREF_WIFI_PKG_UIDS, "");
        String savedPks_3g = prefs.getString(PREF_3G_PKG_UIDS, "");
        String savedPks_roam = prefs.getString(PREF_ROAMING_PKG_UIDS, "");
        String savedPks_vpn = prefs.getString(PREF_VPN_PKG_UIDS, "");
        String savedPks_lan = prefs.getString(PREF_LAN_PKG_UIDS, "");
        boolean wChanged, rChanged, gChanged, vChanged = false;
        // look for the removed application in the "wi-fi" list
        wChanged = removePackageRef(ctx, savedPks_wifi, pkgRemoved, editor, PREF_WIFI_PKG_UIDS);
        // look for the removed application in the "3g" list
        gChanged = removePackageRef(ctx, savedPks_3g, pkgRemoved, editor, PREF_3G_PKG_UIDS);
        // look for the removed application in roaming list
        rChanged = removePackageRef(ctx, savedPks_roam, pkgRemoved, editor, PREF_ROAMING_PKG_UIDS);
        //  look for the removed application in vpn list
        vChanged = removePackageRef(ctx, savedPks_vpn, pkgRemoved, editor, PREF_VPN_PKG_UIDS);
        //  look for the removed application in lan list
        vChanged = removePackageRef(ctx, savedPks_lan, pkgRemoved, editor, PREF_LAN_PKG_UIDS);

        if (wChanged || gChanged || rChanged || vChanged) {
            editor.commit();
            if (isEnabled(ctx)) {
                // .. and also re-apply the rules if the firewall is enabled
                applySavedIptablesRules(ctx, false, new RootCommand());
            }
        }

    }

    public static boolean checkMD5(String md5, File updateFile) {
        if (md5.isEmpty() || updateFile == null) {
            dev.ukanth.ufirewall.log.Log.e(TAG, "MD5 string empty or updateFile null");
            return false;
        }

        String calculatedDigest = calculateMD5(updateFile);
        if (calculatedDigest == null) {
            dev.ukanth.ufirewall.log.Log.e(TAG, "calculatedDigest null");
            return false;
        }

        return calculatedDigest.equalsIgnoreCase(md5);
    }

    private static String calculateMD5(File updateFile) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Exception while getting digest", e);
            return null;
        }

        InputStream is;
        try {
            is = new FileInputStream(updateFile);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Exception while getting FileInputStream", e);
            return null;
        }

        byte[] buffer = new byte[8192];
        int read;
        try {
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] md5sum = digest.digest();
            BigInteger bigInt = new BigInteger(1, md5sum);
            String output = bigInt.toString(16);
            // Fill to 32 chars
            output = String.format("%32s", output).replace(' ', '0');
            return output;
        } catch (IOException e) {
            throw new RuntimeException("Unable to process file for MD5", e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                Log.e(TAG, "Exception on closing MD5 input stream", e);
            }
        }
    }

    public static void donateDialog(final Context ctx, boolean showToast) {
        if (showToast) {
            Toast.makeText(ctx, ctx.getText(R.string.donate_only), Toast.LENGTH_LONG).show();
        } else {
            try {
                new MaterialDialog.Builder(ctx).cancelable(false)
                        .title(R.string.buy_donate)
                        .content(R.string.donate_only)
                        .positiveText(R.string.buy_donate)
                        .negativeText(R.string.close)
                        .icon(ctx.getResources().getDrawable(R.drawable.ic_launcher))
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setData(Uri.parse("market://search?q=pub:ukpriya"));
                                ctx.startActivity(intent);
                            }
                        })

                        .onNegative(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                dialog.cancel();
                                G.isDo(false);
                            }
                        })
                        .show();
            } catch (Exception e) {
                Toast.makeText(ctx, ctx.getText(R.string.donate_only), Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Small structure to hold an application info
     */
    public static final class PackageInfoData {
        /**
         * linux user id
         */
        public int uid;
        /**
         * application names belonging to this user id
         */
        public List<String> names;
        /**
         * rules saving & load
         **/
        public String pkgName;
        /**
         * indicates if this application is selected for wifi
         */
        public boolean selected_wifi;
        /**
         * indicates if this application is selected for 3g
         */
        public boolean selected_3g;
        /**
         * indicates if this application is selected for roam
         */
        public boolean selected_roam;
        /**
         * indicates if this application is selected for vpn
         */
        public boolean selected_vpn;
        /**
         * indicates if this application is selected for lan
         */
        public boolean selected_lan;
        /**
         * toString cache
         */
        public String tostr;
        /**
         * application info
         */
        public ApplicationInfo appinfo;
        /**
         * cached application icon
         */
        public Drawable cached_icon;
        /**
         * indicates if the icon has been loaded already
         */
        public boolean icon_loaded;

        /* install time */
        public long installTime;

        /**
         * first time seen?
         */
        public boolean firstseen;

        public PackageInfoData() {
        }

        public PackageInfoData(int uid, String name, String pkgNameStr) {
            this.uid = uid;
            this.names = new ArrayList<String>();
            this.names.add(name);
            this.pkgName = pkgNameStr;
        }

        public PackageInfoData(String user, String name, String pkgNameStr) {
            this(android.os.Process.getUidForName(user), name, pkgNameStr);
        }

        /**
         * Screen representation of this application
         */
        @Override
        public String toString() {
            if (tostr == null) {
                StringBuilder s = new StringBuilder();
                //if (uid > 0) s.append(uid + ": ");
                for (int i = 0; i < names.size(); i++) {
                    if (i != 0) s.append(", ");
                    s.append(names.get(i));
                }
                s.append("\n");
                tostr = s.toString();
            }
            return tostr;
        }

        public String toStringWithUID() {
            if (tostr == null) {
                StringBuilder s = new StringBuilder();
                s.append("[ ");
                s.append(uid);
                s.append(" ] ");
                for (int i = 0; i < names.size(); i++) {
                    if (i != 0) s.append(", ");
                    s.append(names.get(i));
                }
                s.append("\n");
                tostr = s.toString();
            }
            return tostr;
        }

    }

    public static void exportRulesToFileConfirm(final Context ctx) {
        String fileName = "afwall-backup-" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()) + ".json";
        if (exportRules(ctx, fileName)) {
            Api.toast(ctx, ctx.getString(R.string.export_rules_success) + " " + Environment.getExternalStorageDirectory().getPath() + "/afwall/" + fileName);
        } else {
            Api.toast(ctx, ctx.getString(R.string.export_rules_fail));
        }
    }

    public static void exportAllPreferencesToFileConfirm(final Context ctx) {
        String fileName = "afwall-backup-all-" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()) + ".json";
        if (exportAll(ctx, fileName)) {
            Api.toast(ctx, ctx.getString(R.string.export_rules_success) + " " + Environment.getExternalStorageDirectory().getPath() + "/afwall/");
        } else {
            Api.toast(ctx, ctx.getString(R.string.export_rules_fail));
        }
    }

    private static void updateExportPackage(Map<String, JSONObject> exportMap, String packageName, int identifier) throws JSONException {
        JSONObject obj;
        if (packageName != null) {
            if (exportMap.containsKey(packageName)) {
                obj = exportMap.get(packageName);
                obj.put(identifier + "", true);
            } else {
                obj = new JSONObject();
                obj.put(identifier + "", true);
                exportMap.put(packageName, obj);
            }
        }

    }

    private static void updatePackage(Context ctx, String savedPkg_uid, Map<String, JSONObject> exportMap, int identifier) throws JSONException {
        StringTokenizer tok = new StringTokenizer(savedPkg_uid, "|");
        while (tok.hasMoreTokens()) {
            String uid = tok.nextToken();
            if (!uid.equals("")) {
                String packageName = ctx.getPackageManager().getNameForUid(Integer.parseInt(uid));
                updateExportPackage(exportMap, packageName, identifier);
            }
        }
    }

    private static Map<String, JSONObject> getCurrentRulesAsMap(Context ctx) {
        List<PackageInfoData> apps = getApps(ctx, null);
        // Builds a pipe-separated list of names
        Map<String, JSONObject> exportMap = new HashMap<>();
        try {
            for (int i = 0; i < apps.size(); i++) {
                if (apps.get(i).selected_wifi) {
                    updateExportPackage(exportMap, apps.get(i).pkgName, WIFI_EXPORT);
                }
                if (apps.get(i).selected_3g) {
                    updateExportPackage(exportMap, apps.get(i).pkgName, DATA_EXPORT);
                }
                if (apps.get(i).selected_roam) {
                    updateExportPackage(exportMap, apps.get(i).pkgName, ROAM_EXPORT);
                }
                if (apps.get(i).selected_vpn) {
                    updateExportPackage(exportMap, apps.get(i).pkgName, VPN_EXPORT);
                }
                if (apps.get(i).selected_lan) {
                    updateExportPackage(exportMap, apps.get(i).pkgName, LAN_EXPORT);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return exportMap;
    }

    public static boolean exportAll(Context ctx, final String fileName) {
        boolean res = false;
        File sdCard = Environment.getExternalStorageDirectory();
        if (isExternalStorageWritable()) {
            File dir = new File(sdCard.getAbsolutePath() + "/afwall/");
            dir.mkdirs();
            File file = new File(dir, fileName);

            try {
                FileOutputStream fOut = new FileOutputStream(file);
                OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);

                JSONObject exportObject = new JSONObject();
                //if multiprofile is enabled
                if (G.enableMultiProfile()) {
                    if (!G.isProfileMigrated()) {
                        JSONObject profileObject = new JSONObject();
                        //store all the profile settings
                        for (String profile : G.profiles) {
                            profileObject.put(profile, new JSONObject(getRulesForProfile(ctx, profile)));
                        }
                        exportObject.put("profiles", profileObject);
                        //if any additional profiles
                        //int defaultProfileCount = 3;
                        JSONObject addProfileObject = new JSONObject();
                        for (String profile : G.getAdditionalProfiles()) {
                            addProfileObject.put(profile, new JSONObject(getRulesForProfile(ctx, profile)));
                        }
                        //support for new profiles
                        exportObject.put("additional_profiles", addProfileObject);
                    } else {
                        JSONObject profileObject = new JSONObject();
                        //add default profile
                        String profileName = "AFWallPrefs";
                        profileObject.put(profileName, new JSONObject(getRulesForProfile(ctx, profileName)));
                        //update for new profile logic
                        List<ProfileData> profileDataList = ProfileHelper.getProfiles();
                        //store all the profile settings
                        for (ProfileData profile : profileDataList) {
                            profileName = profile.getName();
                            if (profile.getIdentifier().startsWith("AFWallProfile")) {
                                profileName = profile.getIdentifier();
                            }
                            profileObject.put(profile.getName(), new JSONObject(getRulesForProfile(ctx, profileName)));
                        }
                        exportObject.put("_profiles", profileObject);
                    }


                } else {
                    //default Profile - current one
                    JSONObject obj = new JSONObject(getCurrentRulesAsMap(ctx));
                    exportObject.put("default", obj);
                }

                //now gets all the preferences
                exportObject.put("prefs", getAllAppPreferences(ctx, G.gPrefs));

                myOutWriter.append(exportObject.toString());
                res = true;
                myOutWriter.close();
                fOut.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, e.getLocalizedMessage());
            } catch (IOException e) {
                Log.d(TAG, e.getLocalizedMessage());
            } catch (JSONException e) {
                Log.d(TAG, e.getLocalizedMessage());
            } catch (Exception e) {
                Log.d(TAG, e.getLocalizedMessage());
            }
        }

        return res;
    }

    private static Map<String, JSONObject> getRulesForProfile(Context ctx, String profile) throws JSONException {
        Map<String, JSONObject> exportMap = new HashMap<>();
        SharedPreferences prefs = ctx.getSharedPreferences(profile, Context.MODE_PRIVATE);
        updatePackage(ctx, prefs.getString(PREF_WIFI_PKG_UIDS, ""), exportMap, WIFI_EXPORT);
        updatePackage(ctx, prefs.getString(PREF_3G_PKG_UIDS, ""), exportMap, DATA_EXPORT);
        updatePackage(ctx, prefs.getString(PREF_ROAMING_PKG_UIDS, ""), exportMap, ROAM_EXPORT);
        updatePackage(ctx, prefs.getString(PREF_VPN_PKG_UIDS, ""), exportMap, VPN_EXPORT);
        updatePackage(ctx, prefs.getString(PREF_LAN_PKG_UIDS, ""), exportMap, LAN_EXPORT);
        return exportMap;
    }

    private static JSONArray getAllAppPreferences(Context ctx, SharedPreferences gPrefs) throws JSONException {
        Map<String, ?> keys = gPrefs.getAll();
        JSONArray arr = new JSONArray();
        for (Map.Entry<String, ?> entry : keys.entrySet()) {
            JSONObject obj = new JSONObject();
            obj.put(entry.getKey(), entry.getValue().toString());
            arr.put(obj);
        }
        return arr;
    }

    public static boolean exportRules(Context ctx, final String fileName) {
        boolean res = false;
        File sdCard = Environment.getExternalStorageDirectory();
        if (isExternalStorageWritable()) {
            File dir = new File(sdCard.getAbsolutePath() + "/afwall/");
            dir.mkdirs();
            File file = new File(dir, fileName);
            try {
                FileOutputStream fOut = new FileOutputStream(file);
                OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);

                //default Profile - current one
                JSONObject obj = new JSONObject(getCurrentRulesAsMap(ctx));
                JSONArray jArray = new JSONArray("[" + obj.toString() + "]");

                myOutWriter.append(jArray.toString());
                res = true;
                myOutWriter.close();
                fOut.close();
            } catch (FileNotFoundException e) {
                Log.e(TAG, e.getLocalizedMessage());
            } catch (JSONException e) {
                Log.e(TAG, e.getLocalizedMessage());
            } catch (IOException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }

        return res;
    }

    private static boolean importRules(Context ctx, File file, StringBuilder msg) {
        boolean returnVal = false;
        BufferedReader br = null;
        try {
            StringBuilder text = new StringBuilder();
            br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line);
            }
            String data = text.toString();
            JSONArray array = new JSONArray(data);
            updateRulesFromJson(ctx, (JSONObject) array.get(0), PREFS_NAME);
            returnVal = true;
        } catch (FileNotFoundException e) {
            msg.append(ctx.getString(R.string.import_rules_missing));
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
        } catch (JSONException e) {
            Log.e(TAG, e.getLocalizedMessage());
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    Log.e(TAG, e.getLocalizedMessage());
                }
            }
        }
        return returnVal;
    }


    private static void updateRulesFromJson(Context ctx, JSONObject object, String preferenceName) throws JSONException {
        final StringBuilder wifi_uids = new StringBuilder();
        final StringBuilder data_uids = new StringBuilder();
        final StringBuilder roam_uids = new StringBuilder();
        final StringBuilder vpn_uids = new StringBuilder();
        final StringBuilder lan_uids = new StringBuilder();

        Map<String, Object> json = JsonHelper.toMap(object);
        final PackageManager pm = ctx.getPackageManager();

        for (Map.Entry<String, Object> entry : json.entrySet()) {
            String pkgName = entry.getKey();
            if (pkgName.contains(":")) {
                pkgName = pkgName.split(":")[0];
            }

            JSONObject jsonObj = (JSONObject) JsonHelper.toJSON(entry.getValue());
            Iterator<?> keys = jsonObj.keys();
            while (keys.hasNext()) {
                //get wifi/data/lan etc
                String key = (String) keys.next();
                switch (Integer.parseInt(key)) {
                    case WIFI_EXPORT:
                        if (wifi_uids.length() != 0) {
                            wifi_uids.append('|');
                        }
                        if (pkgName.startsWith("dev.afwall.special")) {
                            wifi_uids.append(specialApps.get(pkgName));
                        } else {
                            try {
                                wifi_uids.append(pm.getApplicationInfo(pkgName, 0).uid);
                            } catch (NameNotFoundException e) {

                            }
                        }
                        break;
                    case DATA_EXPORT:
                        if (data_uids.length() != 0) {
                            data_uids.append('|');
                        }
                        if (pkgName.startsWith("dev.afwall.special")) {
                            data_uids.append(specialApps.get(pkgName));
                        } else {
                            try {
                                data_uids.append(pm.getApplicationInfo(pkgName, 0).uid);
                            } catch (NameNotFoundException e) {

                            }
                        }
                        break;
                    case ROAM_EXPORT:
                        if (roam_uids.length() != 0) {
                            roam_uids.append('|');
                        }
                        if (pkgName.startsWith("dev.afwall.special")) {
                            roam_uids.append(specialApps.get(pkgName));
                        } else {
                            try {
                                roam_uids.append(pm.getApplicationInfo(pkgName, 0).uid);
                            } catch (NameNotFoundException e) {

                            }
                        }
                        break;
                    case VPN_EXPORT:
                        if (vpn_uids.length() != 0) {
                            vpn_uids.append('|');
                        }
                        if (pkgName.startsWith("dev.afwall.special")) {
                            vpn_uids.append(specialApps.get(pkgName));
                        } else {
                            try {
                                vpn_uids.append(pm.getApplicationInfo(pkgName, 0).uid);
                            } catch (NameNotFoundException e) {

                            }
                        }
                        break;
                    case LAN_EXPORT:
                        if (lan_uids.length() != 0) {
                            lan_uids.append('|');
                        }
                        if (pkgName.startsWith("dev.afwall.special")) {
                            lan_uids.append(specialApps.get(pkgName));
                        } else {
                            try {
                                lan_uids.append(pm.getApplicationInfo(pkgName, 0).uid);
                            } catch (NameNotFoundException e) {

                            }
                        }
                        break;
                }

            }
        }
        final SharedPreferences prefs = ctx.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
        final Editor edit = prefs.edit();
        edit.putString(PREF_WIFI_PKG_UIDS, wifi_uids.toString());
        edit.putString(PREF_3G_PKG_UIDS, data_uids.toString());
        edit.putString(PREF_ROAMING_PKG_UIDS, roam_uids.toString());
        edit.putString(PREF_VPN_PKG_UIDS, vpn_uids.toString());
        edit.putString(PREF_LAN_PKG_UIDS, lan_uids.toString());

        edit.commit();

    }

    private static boolean importAll(Context ctx, File file, StringBuilder msg) {
        boolean returnVal = false;
        BufferedReader br = null;

        try {
            StringBuilder text = new StringBuilder();
            br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line);
            }
            String data = text.toString();
            JSONObject object = new JSONObject(data);
            String[] ignore = {"appVersion", "fixLeak", "enableLogService", "sort", "storedProfile", "hasRoot", "logChains", "kingDetect", "fingerprintEnabled"};
            String[] intType = {"logPingTime", "customDelay", "patternMax", "widgetX", "widgetY", "notification_priority"};
            List<String> ignoreList = Arrays.asList(ignore);
            List<String> intList = Arrays.asList(intType);
            JSONArray prefArray = (JSONArray) object.get("prefs");
            for (int i = 0; i < prefArray.length(); i++) {
                JSONObject prefObj = (JSONObject) prefArray.get(i);
                Iterator<?> keys = prefObj.keys();

                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    String value = (String) prefObj.get(key);
                    if (!ignoreList.contains(key)) {
                        //boolean type values
                        if (value.equals("true") || value.equals("false")) {
                            G.gPrefs.edit().putBoolean(key, Boolean.parseBoolean(value)).commit();
                        } else {
                            try {
                                //handle Long
                                if (key.equals("multiUserId")) {
                                    G.gPrefs.edit().putLong(key, Long.parseLong(value)).commit();
                                } else if (intList.contains(key)) {
                                    G.gPrefs.edit().putString(key, value).commit();
                                } else {
                                    Integer intValue = Integer.parseInt(value);
                                    G.gPrefs.edit().putInt(key, intValue).commit();
                                }
                            } catch (NumberFormatException e) {
                                G.gPrefs.edit().putString(key, value).commit();
                            }
                        }
                    }
                }
            }
            if (G.enableMultiProfile()) {
                if (G.isProfileMigrated()) {
                    JSONObject profileObject = object.getJSONObject("_profiles");
                    Iterator<?> keys = profileObject.keys();
                    while (keys.hasNext()) {
                        String key = (String) keys.next();
                        String identifier = key.replaceAll("\\s+", "");
                        ProfileData profileData = new ProfileData(key, identifier);
                        profileData.save();
                        try {
                            JSONObject obj = profileObject.getJSONObject(key);
                            updateRulesFromJson(ctx, obj, key);
                        } catch (JSONException e) {
                            if (e.getMessage().contains("No value")) {
                                continue;
                            }
                        }
                    }
                } else {
                    JSONObject profileObject = object.getJSONObject("profiles");
                    Iterator<?> keys = profileObject.keys();
                    while (keys.hasNext()) {
                        String key = (String) keys.next();
                        try {
                            JSONObject obj = profileObject.getJSONObject(key);
                            updateRulesFromJson(ctx, obj, key);
                        } catch (JSONException e) {
                            if (e.getMessage().contains("No value")) {
                                continue;
                            }
                        }
                    }
                    //handle custom/additional profiles
                    JSONObject customProfileObject = object.getJSONObject("additional_profiles");
                    keys = customProfileObject.keys();
                    while (keys.hasNext()) {
                        String key = (String) keys.next();
                        try {
                            JSONObject obj = customProfileObject.getJSONObject(key);
                            updateRulesFromJson(ctx, obj, key);
                        } catch (JSONException e) {
                            if (e.getMessage().contains("No value")) {
                                continue;
                            }
                        }
                    }
                }
            } else {
                //now restore the default profile
                JSONObject defaultRules = object.getJSONObject("default");
                updateRulesFromJson(ctx, defaultRules, PREFS_NAME);
            }
            returnVal = true;
        } catch (FileNotFoundException e) {
            msg.append(ctx.getString(R.string.import_rules_missing));
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
        } catch (JSONException e) {
            Log.e(TAG, e.getLocalizedMessage());
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    Log.e(TAG, e.getLocalizedMessage());
                }
            }
        }
        return returnVal;
    }

    @SuppressWarnings("unchecked")
    public static boolean loadSharedPreferencesFromFile(Context ctx, StringBuilder builder, String fileName, boolean loadAll) {
        boolean res = false;
        File file = new File(fileName);
        if (file.exists()) {
            if (loadAll) {
                res = importAll(ctx, file, builder);
            } else {
                res = importRules(ctx, file, builder);
            }
        }
        return res;
    }

    /*public static List<String> interfaceInfo(boolean showMatches) {
        List<String> ret = new ArrayList<String>();
        try {
            for (File f : new File("/sys/class/net").listFiles()) {
                String name = f.getName();

                if (!showMatches) {
                    ret.add(name);
                } else {
                    if (InterfaceTracker.matchName(InterfaceTracker.ITFS_WIFI, name) != null) {
                        ret.add(name + ": wifi");
                    } else if (InterfaceTracker.matchName(InterfaceTracker.ITFS_3G, name) != null) {
                        ret.add(name + ": 3G");
                    } else if (InterfaceTracker.matchName(InterfaceTracker.ITFS_VPN, name) != null) {
                        ret.add(name + ": VPN");
                    } else {
                        ret.add(name + ": unknown");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "can't list network interfaces: " + e.getLocalizedMessage());
        }
        return ret;
    }*/

    private static class LogProbeCallback extends RootCommand.Callback {
        private Context ctx;

        public void cbFunc(RootCommand state) {
            if (state.exitCode != 0) {
                G.enableLogService(false);
                return;
            }
            boolean logSet = false;
            for (String str : state.res.toString().split("\n")) {
                if (str.equals("LOG")) {
                    G.logTarget("LOG");
                    logSet = true;
                    Log.d(TAG, "logging using LOG target");
                    break;
                } else if (str.equals("NFLOG")) {
                    G.logTarget("NFLOG");
                    logSet = true;
                    Log.d(TAG, "logging using NFLOG target");
                    break;
                }
            }

            if (!logSet) {
                Log.i(TAG, "could not find LOG or NFLOG target");
                //displayToasts(ctx, R.string.log_target_failed, Toast.LENGTH_SHORT);
                G.logTarget("");
                G.enableLogService(false);
                return;
            }
            G.enableLogService(true);
            updateLogRules(ctx, new RootCommand()
                    .setReopenShell(true)
                    .setSuccessToast(R.string.log_was_enabled)
                    .setFailureToast(R.string.log_target_failed));
        }
    }


    public static void setLogTarget(final Context ctx, boolean isEnabled) {
        if (!isEnabled) {
            // easy case: just disable
            G.enableLogService(false);
            updateLogRules(ctx, new RootCommand()
                    .setReopenShell(true)
                    .setSuccessToast(R.string.log_was_disabled)
                    .setFailureToast(R.string.log_toggle_failed));
            return;
        }

        if (G.logTarget() == null || G.logTarget().isEmpty()) {
            LogProbeCallback cb = new LogProbeCallback();
            cb.ctx = ctx;
            // probe for LOG/NFLOG targets (unfortunately the file must be read by root)
            //check for ip6 enabled from preference and check against the same
            if (G.enableIPv6()) {
                new RootCommand()
                        .setReopenShell(true)
                        .setFailureToast(R.string.log_toggle_failed)
                        .setCallback(cb)
                        .setLogging(true)
                        .run(ctx, "cat /proc/net/ip6_tables_targets");
            }
            new RootCommand()
                    .setReopenShell(true)
                    .setFailureToast(R.string.log_toggle_failed)
                    .setCallback(cb)
                    .setLogging(true)
                    .run(ctx, "cat /proc/net/ip_tables_targets");
        } else {
            G.enableLogService(true);
            updateLogRules(ctx, new RootCommand()
                    .setReopenShell(true)
                    .setSuccessToast(R.string.log_was_enabled)
                    .setFailureToast(R.string.log_target_failed));
        }
    }

    @SuppressLint("InlinedApi")
    public static void showInstalledAppDetails(Context context, String packageName) {
        final String SCHEME = "package";
        final String APP_PKG_NAME_21 = "com.android.settings.ApplicationPkgName";
        final String APP_PKG_NAME_22 = "pkg";
        final String APP_DETAILS_PACKAGE_NAME = "com.android.settings";
        final String APP_DETAILS_CLASS_NAME = "com.android.settings.InstalledAppDetails";

        Intent intent = new Intent();
        final int apiLevel = Build.VERSION.SDK_INT;
        if (apiLevel >= 9) { // above 2.3
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Uri uri = Uri.fromParts(SCHEME, packageName, null);
            intent.setData(uri);
        } else { // below 2.3
            final String appPkgName = (apiLevel == 8 ? APP_PKG_NAME_22
                    : APP_PKG_NAME_21);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClassName(APP_DETAILS_PACKAGE_NAME,
                    APP_DETAILS_CLASS_NAME);
            intent.putExtra(appPkgName, packageName);
        }
        context.startActivity(intent);
    }

	/*public static void showAlertDialogActivity(Context ctx,String title, String message) {
        Intent dialog = new Intent(ctx,AlertDialogActivity.class);
		dialog.putExtra("title", title);
		dialog.putExtra("message", message);
		dialog.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		ctx.startActivity(dialog);
	}*/

    public static boolean isNetfilterSupported() {
        if ((new File("/proc/config.gz")).exists() == false) {
            if ((new File("/proc/net/netfilter")).exists() == false)
                return false;
            if ((new File("/proc/net/ip_tables_targets")).exists() == false)
                return false;
        } else {
            if (!hasKernelFeature("CONFIG_NETFILTER=") ||
                    !hasKernelFeature("CONFIG_IP_NF_IPTABLES=") ||
                    !hasKernelFeature("CONFIG_NF_NAT"))
                return false;
        }
        return true;
    }

    public static boolean hasKernelFeature(String feature) {
        try {
            File cfg = new File("/proc/config.gz");
            if (cfg.exists() == false) {
                return true;
            }
            FileInputStream fis = new FileInputStream(cfg);
            GZIPInputStream gzip = new GZIPInputStream(fis);
            BufferedReader in = null;
            String line = "";
            in = new BufferedReader(new InputStreamReader(gzip));
            while ((line = in.readLine()) != null) {
                if (line.startsWith(feature)) {
                    gzip.close();
                    return true;
                }
            }
            gzip.close();
        } catch (IOException e) {
            //e.printStackTrace();
        }
        return false;
    }

    private static void initSpecial() {
        if (specialApps == null || specialApps.size() == 0) {
            specialApps = new HashMap<String, Integer>();
            specialApps.put("dev.afwall.special.any", SPECIAL_UID_ANY);
            specialApps.put("dev.afwall.special.kernel", SPECIAL_UID_KERNEL);
            specialApps.put("dev.afwall.special.tether", SPECIAL_UID_TETHER);
            //specialApps.put("dev.afwall.special.dnsproxy",SPECIAL_UID_DNSPROXY);
            specialApps.put("dev.afwall.special.ntp", SPECIAL_UID_NTP);
            for (String acct : specialAndroidAccounts) {
                String pkg = "dev.afwall.special." + acct;
                int uid = android.os.Process.getUidForName(acct);
                specialApps.put(pkg, uid);
            }
        }
    }

    public static void updateLanguage(Context context, String lang) {
        if (!"".equals(lang)) {
            Locale locale = new Locale(lang);
            if (lang.contains("_")) {
                locale = new Locale(lang.split("_")[0], lang.split("_")[1]);
            }
            Resources res = context.getResources();
            DisplayMetrics dm = res.getDisplayMetrics();
            Configuration conf = res.getConfiguration();
            conf.locale = locale;
            res.updateConfiguration(conf, dm);
        }
    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static void setUserOwner(Context context) {
        if (supportsMultipleUsers(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                try {
                    Method getUserHandle = UserManager.class.getMethod("getUserHandle");
                    int userHandle = (Integer) getUserHandle.invoke(context.getSystemService(Context.USER_SERVICE));
                    G.setMultiUserId(userHandle);
                } catch (Exception ex) {
                    Log.e(TAG, "Exception on setUserOwner " + ex.getMessage());
                }
            }
        }
    }

    @SuppressLint("NewApi")
    public static boolean supportsMultipleUsers(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            final UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
            try {
                Method supportsMultipleUsers = UserManager.class.getMethod("supportsMultipleUsers");
                return (Boolean) supportsMultipleUsers.invoke(um);
            } catch (Exception ex) {
                return false;
            }
        }
        return false;
    }


    public static String loadData(final Context context,
                                  final String resourceName) throws IOException {
        int resourceIdentifier = context
                .getApplicationContext()
                .getResources()
                .getIdentifier(resourceName, "raw",
                        context.getApplicationContext().getPackageName());
        if (resourceIdentifier != 0) {
            InputStream inputStream = context.getApplicationContext()
                    .getResources().openRawResource(resourceIdentifier);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    inputStream, "UTF-8"));
            String line;
            StringBuffer data = new StringBuffer();
            while ((line = reader.readLine()) != null) {
                data.append(line);
            }
            reader.close();
            return data.toString();
        }
        return null;
    }


    /* Checks if external storage is available for read and write */
    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    public static boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)
                || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }


    /**
     * Encrypt the password
     *
     * @param key
     * @param data
     * @return
     */
    public static String hideCrypt(String key, String data) {
        if (key == null || data == null)
            return null;
        String encodeStr = null;
        try {
            DESKeySpec desKeySpec = new DESKeySpec(key.getBytes(charsetName));
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(algorithm);
            SecretKey secretKey = secretKeyFactory.generateSecret(desKeySpec);
            byte[] dataBytes = data.getBytes(charsetName);
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            encodeStr = Base64.encodeToString(cipher.doFinal(dataBytes), base64Mode);

        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return encodeStr;
    }

    /**
     * Decrypt the password
     *
     * @param key
     * @param data
     * @return
     */
    public static String unhideCrypt(String key, String data) {
        if (key == null || data == null)
            return null;

        String decryptStr = null;
        try {
            byte[] dataBytes = Base64.decode(data, base64Mode);
            DESKeySpec desKeySpec = new DESKeySpec(key.getBytes(charsetName));
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(algorithm);
            SecretKey secretKey = secretKeyFactory.generateSecret(desKeySpec);
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] dataBytesDecrypted = (cipher.doFinal(dataBytes));
            decryptStr = new String(dataBytesDecrypted);
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return decryptStr;
    }

	/*public static void killLogProcess(final Context ctx,final String klogPath){
        Thread thread = new Thread(){
		    @Override
		    public void run() {
				//use built-in busybox to kill the process
				try {
					new RootCommand().run(ctx, Api.getBusyBoxPath(ctx, false) + " pkill klogripper");
				}catch(Exception e) {
					//another attempt to use killall command from system busybox
					try {
						new RootCommand().run(ctx, Api.getBusyBoxPath(ctx,true) + " killall klogripper");
					}catch(Exception ee) {
						// what if this also failed ? try using normal android way
						new RootCommand().run(ctx, "echo $(ps | grep klogripper) | cut -d' ' -f2 | xargs kill");
						Log.e(TAG,ee.getMessage());
					}
				}

		    }
		};
		thread.start();
	}*/

    public static boolean isMobileNetworkSupported(final Context ctx) {
        boolean hasMobileData = true;
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            if (cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
                hasMobileData = false;
            }
        }
        return hasMobileData;
    }

    public static String getCurrentPackage(Context ctx) {
        PackageInfo pInfo = null;
        try {
            pInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
        } catch (NameNotFoundException e) {
            Log.e(Api.TAG, "Package not found", e);
        }
        return pInfo.packageName;
    }


    public static void showNotification(boolean status, Context context) {

        if (G.activeNotification()) {
            final int NOTIFICATION_ID = 33341;
            String notificationText = "";

            NotificationManager mNotificationManager = (NotificationManager) context
                    .getSystemService(Context.NOTIFICATION_SERVICE);

            //refresh notification on profile switch
            if (G.enableMultiProfile()) {
                mNotificationManager.cancel(NOTIFICATION_ID);
            }


            NotificationCompat.Builder builder = new NotificationCompat.Builder(context);

            Intent appIntent = new Intent(context, MainActivity.class);

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
            stackBuilder.addParentStack(MainActivity.class);
            stackBuilder.addNextIntent(appIntent);

            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setContentIntent(resultPendingIntent);

            int icon;

            if (status) {
                if (G.enableMultiProfile()) {
                    String profile = "";
                    switch (G.storedProfile()) {
                        case "AFWallPrefs":
                            profile = G.gPrefs.getString("default", context.getString(R.string.defaultProfile));
                            break;
                        case "AFWallProfile1":
                            profile = G.gPrefs.getString("profile1", context.getString(R.string.profile1));
                            break;
                        case "AFWallProfile2":
                            profile = G.gPrefs.getString("profile2", context.getString(R.string.profile2));
                            break;
                        case "AFWallProfile3":
                            profile = G.gPrefs.getString("profile3", context.getString(R.string.profile3));
                            break;
                        default:
                            profile = G.storedProfile();
                            break;
                    }
                    notificationText = context.getString(R.string.active) + " (" + profile + ")";
                } else {
                    notificationText = context.getString(R.string.active);
                }
                //notificationText = context.getString(R.string.active);
                icon = R.drawable.notification;
            } else {
                notificationText = context.getString(R.string.inactive);
                icon = R.drawable.notification_error;
            }

            //TODO: Action button's on notification
            //Intent deleteIntent = new Intent(context, BootBroadcast.class);
            //PendingIntent pendingIntentCancel = PendingIntent.getBroadcast(context, 0, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);


            builder.setSmallIcon(icon).setOngoing(true)
                    .setAutoCancel(false)
                    .setContentTitle(context.getString(R.string.app_name))
                    //keep the priority as low ,so it's not visible on lockscreen
                    .setTicker(context.getString(R.string.app_name))
                    .setPriority(G.getNotificationPriority())
                    //.addAction(R.drawable.apply, "", pendingIntentCancel)
                    //.addAction(R.drawable.exit, "", pendingIntentCancel)
                    .setContentText(notificationText);

            Notification notification = builder.build();
            //notification.flags = Notification.FLAG_ONGOING_EVENT;

			/*if(G.lockNotification() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.setVisibility(NotificationCompat.PRIORITY_LOW);
			}*/
            //builder.setContentIntent(in);
            mNotificationManager.notify(NOTIFICATION_ID, notification);
        }


    }


    public static void allowDefaultChains(Context ctx) {
        List<String> cmds = new ArrayList<String>();
        cmds.add("-P INPUT ACCEPT");
        cmds.add("-P FORWARD ACCEPT");
        cmds.add("-P OUTPUT ACCEPT ");
        applyQuick(ctx, cmds, new RootCommand());
        applyIPv6Quick(ctx, cmds, new RootCommand());
    }

    /**
     * Delete all firewall rules.  For diagnostic purposes only.
     *
     * @param ctx      application context
     * @param callback callback for completion
     */
    public static void flushOtherRules(Context ctx, RootCommand callback) {
        List<String> cmds = new ArrayList<String>();
        cmds.add("-F firewall");
        cmds.add("-X firewall");
        apply46(ctx, cmds, callback);
    }

    public static boolean hasRoot() {
        final boolean[] hasRoot = new boolean[1];
        Thread t = new Thread() {
            @Override
            public void run() {
                hasRoot[0] = Shell.SU.available();
            }
        };
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
        }
        return hasRoot[0];
    }

    // Clipboard
    public static void copyToClipboard(Context context, String val) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("label", val);
        clipboard.setPrimaryClip(clip);
    }


}
