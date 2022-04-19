package com.zyprex.texttransfer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;


public class MainActivity extends AppCompatActivity {
    private static String ipAddress;
    private static String ipAddressString;
    private static int port = 8080;
    private static String accessPath;

    private static String textFormHTML = null;
    private static String fileListHTML = null;
    private static String uploadFormHTML = null;

    WebView webView;
    View rootView;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            MainService.MyBinder myBinder = (MainService.MyBinder) iBinder;
            myBinder.startService();
            webView.loadUrl(ipAddressString);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootView = findViewById(android.R.id.content).getRootView();

        /* use the toolbar replace actionbar */
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        SwitchMaterial switcher = findViewById(R.id.action_switch);

        webView = findViewById(R.id.web_view);
        webView.setWebViewClient(new WebViewClient());

        webView.loadUrl("file:///android_asset/help.html");

        switcher.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (compoundButton.isChecked()) {
                    switchOn();
                } else {
                    switchOff();
                }
            }
        });

        readSavedOption();

        initIPAddress();
    }

    private void readSavedOption() {
        SharedPreferences pref =  getSharedPreferences("data", MODE_PRIVATE);
        setPort(pref.getInt("port", 8080));
        setAccessPath(pref.getString("access_path",
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString()));
    }

    private void initIPAddress() {
/*
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (!wm.isWifiEnabled()) {
            Toast.makeText(this, "Please Enable the Wi-Fi first", Toast.LENGTH_SHORT).show();
        }
        int ipAddressInt = wm.getConnectionInfo().getIpAddress();
        ipAddress =  String.format(Locale.getDefault(), "%d.%d.%d.%d",
                (ipAddressInt & 0xff),
                (ipAddressInt >> 8 & 0xff),
                (ipAddressInt >> 16 & 0xff),
                (ipAddressInt >> 24 & 0xff));
        ipAddressString = "http://" + ipAddress + ":" + port;
*/
        ipAddress = "0.0.0.0";
        try {
            for (Enumeration<NetworkInterface> enNetI = NetworkInterface.getNetworkInterfaces(); enNetI.hasMoreElements(); ) {
                NetworkInterface netI = enNetI.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = netI
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress()) {
                        if (netI.getDisplayName().equals("wlan0")) {
                            ipAddress = inetAddress.getHostAddress();
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        ipAddressString = "http://" + ipAddress + ":" + port;
    }

    private void getAssetsHTML(){
        if (textFormHTML == null)
            setTextFormHTML(getTextFromAssets("textform.html"));
        if (fileListHTML == null)
            setFileListHTML(getTextFromAssets("filelist.html"));
        if (uploadFormHTML == null)
            setUploadFormHTML(getTextFromAssets("uploadform.html"));
    }

    private void switchOn() {
        getAssetsHTML();
        Intent bindIntent = new Intent(this, MainService.class);
        bindService(bindIntent, connection, BIND_AUTO_CREATE);
    }

    private void switchOff() {
        unbindService(connection);
        webView.loadUrl("file:///android_asset/help.html");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.about) {
           openAbout();
        }
        if (id == R.id.open_in_browser) {
            openInBrowser();
        }
        if (id == R.id.server_port) {
            changeServerPort();
        }
        if (id == R.id.access_path) {
            changeAccessPath();
        }
        return super.onOptionsItemSelected(item);
    }

    private void openInBrowser() {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(ipAddressString));
        startActivity(browserIntent);
    }

    private void openAbout() {
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View popView = inflater.inflate(R.layout.pop, (ViewGroup) rootView, false);
        PopupWindow popWin = new PopupWindow(popView, rootView.getWidth() / 2,rootView.getHeight() / 2, true);
        popWin.showAtLocation(this.rootView, Gravity.CENTER, 0, 0);
        popView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                popWin.dismiss();
                return true;
            }
        });
    }

    private void changeServerPort() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
                .setTitle("HTTP server port")
                .setMessage("Possible range from 1025 to 49151, current is " + MainActivity.port)
                .setView(input)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        int portNum = Integer.parseInt(input.getText().toString());
                        if (portNum < 49152 && portNum > 1024) {
                            port = portNum;
                            // save port number
                            SharedPreferences.Editor ed = getSharedPreferences("data", MODE_PRIVATE).edit();
                            ed.putInt("port", portNum);
                            ed.apply();
                            ipAddressString = "http://" + ipAddress + ":" + port;
                            Toast.makeText(MainActivity.this, "please restart the server to apply port change",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // do nothing
                    }
                }).show().getCurrentFocus();
    }

    private void changeAccessPath() {
        final EditText input = new EditText(this);
        new AlertDialog.Builder(this)
                .setTitle("Access Path")
                .setMessage("Change access path, this path will be use in file list view" +
                        " and your upload file will save in here, current path is " + MainActivity.accessPath)
                .setView(input)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                       String newAccessPath = input.getText().toString();
                       File dir =  new File(newAccessPath);
                       if (dir.exists() && dir.isDirectory()) {
                           accessPath = newAccessPath;
                           // save access path
                           SharedPreferences.Editor ed = getSharedPreferences("data", MODE_PRIVATE).edit();
                           ed.putString("access_path", newAccessPath);
                           ed.apply();
                           Toast.makeText(MainActivity.this, "access path changed",
                                   Toast.LENGTH_SHORT).show();
                       }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                }).show();
    }

    public String getTextFromAssets(String filename) {
        StringBuilder sb = new StringBuilder();
        try {
            InputStream is = getAssets().open(filename);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String str;
            while ((str = br.readLine()) != null) {
                sb.append(str);
            }
            br.close();
        } catch (IOException e){
            e.printStackTrace();
        }
        return sb.toString();
    }

    public static String getIpAddress() {
        return ipAddress;
    }

    public static String getIpAddressString() {
        return ipAddressString;
    }

    public static int getPort() {
        return port;
    }

    public static void setPort(int port) {
        MainActivity.port = port;
    }

    public static String getAccessPath() {
        return accessPath;
    }

    public static void setAccessPath(String accessPath) {
        MainActivity.accessPath = accessPath;
    }

    public static String getTextFormHTML() {
        return textFormHTML;
    }

    public static String getFileListHTML() {
        return fileListHTML;
    }

    public static String getUploadFormHTML() {
        return uploadFormHTML;
    }

    public static void setTextFormHTML(String textFormHTML) {
        MainActivity.textFormHTML = textFormHTML;
    }

    public static void setFileListHTML(String fileListHTML) {
        MainActivity.fileListHTML = fileListHTML;
    }

    public static void setUploadFormHTML(String uploadFormHTML) {
        MainActivity.uploadFormHTML = uploadFormHTML;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
    }
}
