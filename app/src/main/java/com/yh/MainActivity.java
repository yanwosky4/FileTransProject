/**
 * 这是APP的MainActivity，当然，前面还有启动界面
 */

package com.yh;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.yh.connect.Constant;
import com.yh.connect.WifiAdmin;
import com.yh.connect.WifiApAdmin;
import com.yh.filesmanage.Files_Manage_Activity;
import com.yh.filestrans.Files_Trans_Activity;
import com.yh.offlinefiles.Offline_Files_Choose_Activity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener
{
    public boolean UdpReceiveOut = true;//8秒后跳出udp接收线程
    /**FileTransAppRootFolder此程序自己的文件目录*/
    String FileTransAppRootFolder = "/sdcard/"+Constant.SAVEFILE_DIR+"/";
    /**点两次返回按键退出程序的时间*/
    private long mExitTime;


    private ServerSocket serverSocket=null;
    private Socket socket=null;

    private boolean isLooper=true;
    private NavigationView navigationView;

    private Button button_CreateConnection;
    private Button button_ScanToJoin;
    private Button button_lookFileTransRecord;

    private static String LOG_TAG = "WifiBroadcastActivity";
    private boolean wifiFlag = true;//扫描wifi的子线程的标志位，如果已经连接上正确的wifi热点，线程将结束
    private String address;
    private WifiAdmin wifiAdmin;
    private ArrayList<String> arraylist = new ArrayList<String>();
    private boolean update_wifi_flag = true;
    String ip;
    public static final int DEFAULT_PORT = 43708;
    private static final int MAX_DATA_PACKET_LENGTH = 40;
    private byte[] buffer = new byte[MAX_DATA_PACKET_LENGTH];
    public boolean run = false;//判断是否接收到TCP返回，若接收到则不再继续接受
    public boolean show = false;//判断是否是由于超时而退出线程，若是则显示dialog
    private static boolean tcpout = false;
    private boolean a = false;
    //开启wifi ... ...
    private WifiManager wifiManager = null;
    /**********************************************************************************************/
    private ImageView iv_scanning;
    private android.support.v4.widget.DrawerLayout rl_root;
    /*********************UdpReceive线程**********************/

    private boolean udpout = false;
    /*******************************************************/
    //用以存储传送到文件发送界面的IP，即接收方的IP
    public static String IP_DuiFangde;

    public static String Device_ID = "";

    /**
     * handler用于子线程更新
     */
    private Handler handler1 = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            super.handleMessage(msg);
            switch (msg.what) {
                case 1:
                    button_CreateConnection.setEnabled(true);
                    button_ScanToJoin.setEnabled(true);
                    break;
                case 2:
                    UdpReceiveOut = false;
                    if (!udpout) {
                        if (setWifiApEnabled(true)) {
                            //热点开启成功
                            //Toast.makeText(getApplicationContext(), "WIFI热点开启成功,热点名称为:" + Constant.HOST_SPOT_SSID + ",密码为：" + Constant.HOST_SPOT_PASS_WORD, Toast.LENGTH_LONG).show();
                            showPopupWindow("WIFI热点连接("+Constant.HOST_SPOT_SSID+")开启成功");
                            popupWindow.getContentView().findViewById(R.id.textView_show2).setVisibility(View.VISIBLE);

                            //这里可以设置为当用户连接到自己开的热点后，就跳转到文件发送界面，并将连接到自己热点设备的IP传过去
                            //getConnectDeviceIP()返回的值前面自带IP加一个回车 字样，如IP 192.168.0.111 所以需要截取一下才可以
                            button_CreateConnection.setEnabled(true);
                            button_ScanToJoin.setEnabled(true);
                            startNew startNew = new startNew();
                            startNew.start();

                        } else {
                            //热点开启失败
                            Toast.makeText(getApplicationContext(), "WIFI热点开启失败", Toast.LENGTH_LONG).show();
                        }
                    }
                    break;
                case 3:
                    if (!udpout) {
                        Toast.makeText(MainActivity.this, "局域网内未搜索到设备，将自动启用热点模式分享文件", Toast.LENGTH_SHORT).show();
                    }
                    break;
                default:

                    if (MainActivity.isIp((String) (msg.obj))) {
                        Toast.makeText(MainActivity.this, "这是一个标准IP，地址为：" + msg.obj, Toast.LENGTH_LONG).show();
                        IP_DuiFangde = (String) msg.obj;
                        //跳转到文件发送界面
                        Intent intent_filetrans = new Intent(MainActivity.this, Files_Trans_Activity.class);
                        startActivity(intent_filetrans);
                    } else {
                        Toast.makeText(MainActivity.this, "这不是一个标准IP，内容为：" + msg.obj, Toast.LENGTH_LONG).show();
                    }
                    break;
            }
        }
    };


    /**获取连接到手机热点设备的IP*/
    StringBuilder resultList;
    ArrayList<String> connectedIP;

    public String getConnectDeviceIP() {

        try {
            connectedIP = getConnectIp();
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        resultList = new StringBuilder();
        for (String ip : connectedIP) {
            resultList.append(ip);
            resultList.append("\n");
            try {
                connectedIP = getConnectIp();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        String textString = resultList.toString();
        return textString;

    }

    //从系统/proc/net/arp文件中读取出已连接的设备的信息
    //获取连接设备的IP
    private ArrayList<String> getConnectIp() throws Exception {
        ArrayList<String> connectIpList = new ArrayList<String>();
        BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"));
        String line;
        while ((line = br.readLine()) != null) {
            String[] splitted = line.split(" +");
            if (splitted != null && splitted.length >= 4) {
                String ip = splitted[0];
                connectIpList.add(ip);
            }
        }
        return connectIpList;
    }


    /** wifi热点开关的方法*/
    public boolean setWifiApEnabled(boolean enabled) {
        if (enabled) { // disable WiFi in any case
            //wifi和热点不能同时打开，所以打开热点的时候需要关闭wifi
            wifiManager.setWifiEnabled(false);
        }
        try {
            //热点的配置类
            WifiConfiguration apConfig = new WifiConfiguration();
            //配置热点的名称(可以在名字后面加点随机数什么的)
            apConfig.SSID = Constant.HOST_SPOT_SSID;
            //配置热点的密码
            apConfig.preSharedKey = Constant.HOST_SPOT_PASS_WORD;

            /***配置热点的其他信息  加密方式**/
            apConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            apConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            //用WPA密码方式保护
            apConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            apConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            apConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            apConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            apConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            apConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);

            //通过反射调用设置热点
            Method method = wifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, Boolean.TYPE);
            //返回热点打开状态
            return (Boolean) method.invoke(wifiManager, apConfig, enabled);
        } catch (Exception e) {
            return false;
        }
    }


    private Handler handler2 = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 7:
                    if (!a) {
                        Toast.makeText(MainActivity.this, "局域网内没有搜索到可用设备，正在热点模式下搜索设备", Toast.LENGTH_LONG).show();
                        tcpout = true;
                        //更新并显示WIFI列表，此时还需要判断WIFI是否打开，可以直接写在UpdateWifiList()里面
                        // UpdateWifiList(0);
                    }
                    a = false;
                    break;
                case 8:
                    button_ScanToJoin.setEnabled(true);
                    button_CreateConnection.setEnabled(true);
                    break;
                case 9:
                    UpdateWifiList(1);
                    break;
                case 10:
                    update_wifi_flag=false;
                    break;
                default:
                    tcpout = true;

                    if (isIp((String) (msg.obj))) {
                        Toast.makeText(MainActivity.this, "这是一个IP，地址为：" + (msg.obj), Toast.LENGTH_LONG).show();

                        IP_DuiFangde = (String) (msg.obj);
                        //跳转到文件发送界面
                        Intent intent_filetrans = new Intent(MainActivity.this, Files_Trans_Activity.class);
                        startActivity(intent_filetrans);

                    } else {
                        Toast.makeText(MainActivity.this, "这不是一个IP，内容为：" + msg.obj, Toast.LENGTH_LONG).show();
                    }
                    break;
            }
        }

    };


    /***************************************************************************************************************/
    private Handler handler4 = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 7:
                    if (!a) {
                        Toast.makeText(MainActivity.this, "局域网内没有搜索到可用设备，正在热点模式下搜索设备", Toast.LENGTH_LONG).show();
                        tcpout = true;
                        //更新并显示WIFI列表，此时还需要判断WIFI是否打开，可以直接写在UpdateWifiList()里面
                        // UpdateWifiList(0);
                    }
                    a = false;
                    break;
                case 8:
                    button_ScanToJoin.setEnabled(true);
                    button_CreateConnection.setEnabled(true);
                    break;
                case 9:
                    UpdateWifiList(1);
                    break;
                case 10:
                    update_wifi_flag=false;
                    break;
            }
        }

    };
    /*********************************************************************************************************************8/

/*******************************************************************app数据存储的核心代码*************************************************************************/

    /**
     * 获取系统当前的时间
     */
    public static String getCurrentTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date curDate = new Date();
        String str = format.format(curDate);
        return str;
    }

    /**
     * 获取设备唯一的标志码
     */
    public String getDeviceID() {
        TelephonyManager telephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String imei = telephonyManager.getDeviceId();
        return imei;
    }

    public String getAndroidVersion() {
        String AndroidVersion = android.os.Build.VERSION.RELEASE;
        return AndroidVersion;
    }

    public String getCpuName() {
        try {
            FileReader fr = new FileReader("/proc/cpuinfo");
            BufferedReader br = new BufferedReader(fr);
            String text = br.readLine();
            String[] array = text.split(":\\s+", 2);
            for (int i = 0; i < array.length; i++) {
            }
            return array[1];
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取安卓手机RAM
     */
    public String getTotalMemory() {
        String str1 = "/proc/meminfo";// 系统内存信息文件
        String str2;
        String[] arrayOfString;
        long initial_memory = 0;
        try {
            FileReader localFileReader = new FileReader(str1);
            BufferedReader localBufferedReader = new BufferedReader(localFileReader, 8192);
            str2 = localBufferedReader.readLine();// 读取meminfo第一行，系统总内存大小

            arrayOfString = str2.split("\\s+");
            for (String num : arrayOfString) {
                Log.i(str2, num + "\t");
            }

            initial_memory = Integer.valueOf(arrayOfString[1]).intValue() * 1024;// 获得系统总内存，单位是KB，乘以1024转换为Byte
            localBufferedReader.close();

        } catch (IOException e) {
        }
        return Formatter.formatFileSize(getBaseContext(), initial_memory);// Byte转换为KB或者MB，内存大小规格化
    }

    /**
     * 获取屏幕分辨率
     **/
    public String getScreenResolution() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        String strOpt = dm.widthPixels + " * " + dm.heightPixels;
        return strOpt;
    }


        @Override
        protected void onResume() {
            super.onResume();

            navigationView.getMenu().getItem(0).setChecked(true);
        }

        /***********************************************************************
     * app数据存储的核心代码
     **************************************************************************/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        isLooper=true;

        Device_ID = getDeviceID();


        /********************************************************数据库相关操作*********************************/

        /*****************************************************/

        wifiManager = (WifiManager) super.getSystemService(Context.WIFI_SERVICE);
        /*****************************************************/


        /*******************************************/
        //设备之间连接的两个fab的定义以及初始化
        button_CreateConnection = (Button) findViewById(R.id.button_CreateConnection);
        button_ScanToJoin = (Button) findViewById(R.id.button_ScanToJoin);
        button_lookFileTransRecord = (Button) findViewById(R.id.button_lookFileTransRecord);

        rl_root = (DrawerLayout) findViewById(R.id.drawer_layout);
        //初始化wifiAdmin
        wifiAdmin = new WifiAdmin(MainActivity.this);
        /**获取设备IP*/
        address = getLocalIPAddress();
        ip = address;
        /**点击开启UDP发送线程*/
        button_ScanToJoin.setOnClickListener(listener);
        /**点击跳转到开启接受UDP请求界面*/
        button_CreateConnection.setOnClickListener(listener);
        button_lookFileTransRecord.setOnClickListener(listener);


        /**当点开程序的时候，在SDcard目录下面新建一个名为Constant.SAVEFILE_DIR的文件夹用以存放程序接收到的文件*/
        createMkdir(FileTransAppRootFolder);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        isLooper=false;
        closeSocket();
    }

    private void closeSocket()
    {
        try
        {
            if (serverSocket!=null)
            {
                serverSocket.close();
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        try
        {
            if (socket!=null)
            {
                socket.close();
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    //type 0新建wifi列表
    //type 1动态更新wifi列表
    void UpdateWifiList(int type)
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                label:while (isLooper)
                {
                    wifiAdmin.startScan();
                    wifiAdmin.lookUpScan();
                    arraylist.clear();

                    for (ScanResult scanResult : wifiAdmin.getWifiList())
                    {
                        Log.i("main","scanResult.SSID="+scanResult.SSID);
                        if (scanResult.SSID.equals(Constant.HOST_SPOT_SSID))//如果热点名有Constant.HOST_SPOT_SSID且不为空且不重复
                        {
                            //关闭wifi列表更新
                            update_wifi_flag = false;
                            //这一段输入密码，现阶段设置为默认123456789
                            CreatConnection(Constant.HOST_SPOT_SSID, Constant.HOST_SPOT_PASS_WORD, 3);//这里输入密码
                            //更新这个IP地址
                            IP_DuiFangde = "192.168.43.1";
                            //Socket socket = null;
                            OutputStream outputStream = null;
                            InputStream inputStream = null;

                            try
                            {
                                while(GetIpAddress().equals("192.168.43.1"))
                                {
                                    Thread.sleep(500);
                                }
                                socket = new Socket(IP_DuiFangde, Constant.TEMP_PORT);
                                outputStream = socket.getOutputStream();
                                // 3.具体的输出过程
                                outputStream.write(Constant.APP_CLIENT_FLAG.getBytes());
                                socket.shutdownOutput();

                                inputStream=socket.getInputStream();
                                byte[] b = new byte[20];
                                int len;
                                StringBuffer stringBuffer=new StringBuffer();
                                while((len = inputStream.read(b)) != -1){
                                    String str = new String(b,0,len);
                                    stringBuffer.append(str);
                                }
                                if (stringBuffer.toString().equals(Constant.APP_SRVER_FLAG))
                                {
                                    MainActivity.this.runOnUiThread(new Runnable()
                                    {
                                        @Override
                                        public void run()
                                        {
                                            disappearPopupWindow();
                                        }
                                    });

                                    Intent intent_filetrans = new Intent(MainActivity.this, Files_Trans_Activity.class);
                                    intent_filetrans.putExtra("isServer",false);
                                    startActivity(intent_filetrans);
                                }
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                                continue label;
                            }
                            finally
                            {
                                if (outputStream != null)
                                {try {outputStream.close();}catch (IOException e) {e.printStackTrace();}}

                                if (inputStream != null)
                                {try {inputStream.close(); }catch (IOException e) {e.printStackTrace();}}

                                if (socket != null)
                                {try {socket.close();} catch (IOException e) {e.printStackTrace();}}
                            }
                            break label;
                        }
                    }
                }
            }
        }).start();// 空线程延时

    }

    void CreatConnection(final String name, final String key, final int type) {
        new Thread(new Runnable()//匿名内部类的调用方式
        {
            @Override
            public void run() {
                wifiAdmin.openWifi();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                wifiAdmin.addNetwork(wifiAdmin.CreateWifiInfo(name, key, type));
                wifiFlag = false;//关闭扫描wifi热点的子线程
            }
        }).start();// 建立链接线程

    }

    public String GetIpAddress() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int i = wifiInfo.getIpAddress();
        String a = (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "." + ((i >> 16) & 0xFF) + "." + ((i >> 24) & 0xFF);
        String b = "0.0.0.0";
        if (a.equals(b)) {
            a = "192.168.43.1";// 当手机当作WIFI热点的时候，自身IP地址为192.168.43.1
        }
        return a;
    }
    /**
     * 接受返回的TCP消息
     */
    private class TcpReceive implements Runnable {
        public void run() {
            while (isLooper) {
                tcpout = false;
                Socket socket = null;
                ServerSocket ss = null;
                BufferedReader in = null;
                try {
                    ss = new ServerSocket(8080);
                    socket = ss.accept();
                    if (socket != null) {
                        run = true;
                        a = true;
                        in = new BufferedReader(new InputStreamReader(
                                socket.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        sb.append(socket.getInetAddress().getHostAddress());
                        String line = null;
                        while ((line = in.readLine()) != null) {
                            sb.append(line);
                        }
                        final String ipString = sb.toString().trim();// "192.168.0.104:8731";
                        Message msg = new Message();
                        msg.obj = ipString;

                        tcpout = true;
                        //这里是IP地址
                        handler2.sendMessage(msg);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (in != null)
                            in.close();
                        if (socket != null)
                            socket.close();
                        if (ss != null)
                            ss.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (tcpout) {
                    break;
                }
            }
        }
    }


    /**************************************************************
     * 两个按钮的监听事件
     ***************************************************************/
    private View.OnClickListener listener = new View.OnClickListener() {

        @Override
        public void onClick(View v)
        {
            isLooper=true;

            if (v == button_ScanToJoin) {
                tcpout = false;
                update_wifi_flag=true;

                //显示雷达扫描界面
                showPopupWindow("正在搜索热点中。。。");

                //打开线程之前先判断热点是否是开的，如果热点是开的，就关掉热点，然后再开启wifi，如果热点本身是关的，就直接开启WIFI
                /***************以下的判断方法是有错误的，应该重写***********/
                if (WifiApAdmin.isWifiApEnabled(wifiManager))
                {
                    WifiApAdmin.closeWifiAp(wifiManager);
                }

                wifiManager.setWifiEnabled(true);

//                Thread thread = new Thread(new TcpReceive());
//                thread.start();
//                offline_trans_log.setText("正在发送UDP请求，若有连接将在此显示，若五秒钟后没有显示，可以点击再次搜索。。。" + "\n");
//                BroadCastUdp bcu = new BroadCastUdp(address);
//                bcu.start();

                UpdateWifiList(1);
                handler2.sendEmptyMessage(8);


                button_ScanToJoin.setEnabled(false);
                button_CreateConnection.setEnabled(false);

            }
            else if (v == button_CreateConnection)
            {

                if (WifiApAdmin.isWifiApEnabled(wifiManager)) {
                    WifiApAdmin.closeWifiAp(wifiManager);
                    //wifiManager.setWifiEnabled(true);
                }

                //显示雷达扫描界面
                showPopupWindow("正在创建热点中。。。");
                //点击创建链接的按钮之后隐藏Fab.
                button_ScanToJoin.setEnabled(false);
                button_CreateConnection.setEnabled(false);



                /** 开启UDP接受线程*/
                UdpReceive udpreceive = new UdpReceive();
                udpreceive.start();
            }
            if (v==button_lookFileTransRecord)
            {
                /**菜单中文件管理选项，跳转到文件管理的Activity进行文件管理的操作*/
                Intent intent = new Intent(MainActivity.this, Files_Manage_Activity.class);
                intent.putExtra("filePath", Environment.getExternalStorageDirectory().getPath()+"/"+Constant.SAVEFILE_DIR);
                startActivity(intent);
            }
        }
    };


    /**
     * 获取本机ip方法
     */
    private String getLocalIPAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(LOG_TAG, ex.toString());
        }
        return null;
    }

    /**
     * 监听有没有wifi接入到wifi热点的线程
     */
    public class startNew extends Thread {
        public void run()
        {
//            while (!(getConnectDeviceIP().length() > 6)) {
//                //上面getConnectDeviceIP().length() > 6 是用来判断getConnectDeviceIP()这个字符串是否获取了IP地址，不一定非要是6，其余合适的值都行
//                IP_DuiFangde = getConnectDeviceIP();
//
//
//            }

            //当获取到IP后跳出上面的循环，将IP值赋值给变量
            //IP_DuiFangde = getConnectDeviceIP().substring(3, getConnectDeviceIP().length() - 1);

            //跳转到文件发送界面
            label:while (isLooper)
            {
                Socket socket=null;
                //ServerSocket serverSocket=null;
                InputStream inputStream=null;
                OutputStream outputStream = null;
                try
                {
                    serverSocket=new ServerSocket(Constant.TEMP_PORT);
                    socket = serverSocket.accept();
                    // 3.调用Socket对象的getInputStream()获取一个从客户端发送过来的输入流
                    inputStream = socket.getInputStream();
                    // 4.对获取的输入流进行的操作
                    byte[] b = new byte[20];
                    int len;
                    StringBuffer stringBuffer=new StringBuffer();
                    while ((len = inputStream.read(b)) != -1) {
                        String str = new String(b, 0, len);
                        stringBuffer.append(str);
                    }
                    if (stringBuffer.toString().equals(Constant.APP_CLIENT_FLAG))
                    {
                        IP_DuiFangde=socket.getInetAddress().getHostAddress();

                        outputStream=socket.getOutputStream();
                        outputStream.write(Constant.APP_SRVER_FLAG.getBytes());

                        disappearPopupWindow();
                        //如果是一个IP就跳转到文件发送界面
                        Intent intent_filetrans2 = new Intent(MainActivity.this, Files_Trans_Activity.class);
                        intent_filetrans2.putExtra("isServer",true);
                        startActivity(intent_filetrans2);

                        break label;
                    }

                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    if (outputStream != null)
                    {try {outputStream.close(); }catch (IOException e) {e.printStackTrace();}}

                    if (inputStream != null)
                    {try {inputStream.close(); }catch (IOException e) {e.printStackTrace();}}

                    if (socket != null)
                    {try {socket.close();} catch (IOException e) {e.printStackTrace();}}

                    if (serverSocket != null)
                    {try {serverSocket.close();} catch (IOException e) {e.printStackTrace();}}
                }
            }

            //恢复按钮为可点击
            //设置按钮可点击}

        }

    }

    /**
     * UDP广播线程
     */
    public class BroadCastUdp extends Thread {
        private String dataString;
        private DatagramSocket udpSocket;
        public volatile boolean exit = false;

        public BroadCastUdp(String dataString) {
            this.dataString = dataString;
        }


        public void run() {

            show = false;
            /**计算时间标志*/
            long st = System.currentTimeMillis();
            while (!exit) {
                DatagramPacket dataPacket = null;
                try {

                    if(udpSocket==null){
                        udpSocket = new DatagramSocket(null);
                        udpSocket.setReuseAddress(true);
                        udpSocket.bind(new InetSocketAddress(DEFAULT_PORT));
                    }
                   // udpSocket = new DatagramSocket(DEFAULT_PORT);
                    dataPacket = new DatagramPacket(buffer, MAX_DATA_PACKET_LENGTH);
                    byte[] data = dataString.getBytes();
                    dataPacket.setData(data);
                    dataPacket.setLength(data.length);
                    dataPacket.setPort(DEFAULT_PORT);
                    InetAddress broadcastAddr;
                    broadcastAddr = InetAddress.getByName("255.255.255.255");
                    dataPacket.setAddress(broadcastAddr);
                } catch (Exception e) {
                    Log.e(LOG_TAG, e.toString());
                }
                try {
                    udpSocket.send(dataPacket);
                    sleep(10);
                } catch (Exception e) {
                    Log.e(LOG_TAG, e.toString());
                }
                udpSocket.close();
                /**计算时间标志*/

                long et = System.currentTimeMillis();
                /**8秒后次线程自动销毁*/
                if ((et - st) > 8000) {
                    show = true;
                    break;
                }
                /**tcp返回值后停止发送udp*/
                Log.i("tag", "show");
                if (run) {
                    run = false;
                    break;
                }
            }
            Log.i("tag", "show");
            if (show) {
                //tcpout = true;
                //Message message = new Message();
                show = false;
                //不再进行UDP发送与接收后，扫描并显示WIFI列表
                handler2.sendEmptyMessage(7);
                new Thread(new Runnable()//同时开启一个动态更新wifi列表的线程，直到标志位update_wifi_flag被赋值false
                {
                    @Override
                    public void run() {
                        long st = System.currentTimeMillis();

                        while (update_wifi_flag) {
                            long et = System.currentTimeMillis();
                            Log.i("TAG", "ssssssssssssssssssssssssssssssss"+st);
                            Log.i("TAG", "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"+et);
                            /**10秒后次线程自动销毁*/
                            if ((et - st) > 15000) {
                                handler2.sendEmptyMessage(10);

                            }
                            handler2.sendEmptyMessage(9);
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }).start();
            }
            handler2.sendEmptyMessage(8);

        }
    }

    /**************************************************************************************************************/
    /**
     * UDP接受线程类
     */
    public class UdpReceive extends Thread {
        public void run() {
//            udpout = false;   //判断是否退出UDP接收线程的标志位
//            byte[] data = new byte[256];
//            try {
//                udpSocket = new DatagramSocket(43708);
//                udpPacket = new DatagramPacket(data, data.length);
//            } catch (SocketException e1) {
//                e1.printStackTrace();
//            }


            /**8秒后发送消息更新UI*/
            //handler1.sendEmptyMessageDelayed(3, 7500);
            handler1.sendEmptyMessageDelayed(2, 0);    //0秒后执行case2 也就是自动开启WIFI热点

        }
    }




    /**
     * 判断一个字符串是否是标准的IPv4地址
     */
    public static boolean isIp(String IP) {
        boolean b = false;
        //去掉IP字符串前后所有的空格
        while (IP.startsWith(" ")) {
            IP = IP.substring(1, IP.length()).trim();
        }
        while (IP.endsWith(" ")) {
            IP = IP.substring(0, IP.length() - 1).trim();
        }

        //IP = this.deleteSpace(IP);
        if (IP.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            String s[] = IP.split("\\.");
            if (Integer.parseInt(s[0]) < 255)
                if (Integer.parseInt(s[1]) < 255)
                    if (Integer.parseInt(s[2]) < 255)
                        if (Integer.parseInt(s[3]) < 255)
                            b = true;
        }
        return b;
    }

    /**
     * 去除字符串前后的空格
     */
    public String deleteSpace(String IP) {//去掉IP字符串前后所有的空格
        while (IP.startsWith(" ")) {
            IP = IP.substring(1, IP.length()).trim();
        }
        while (IP.endsWith(" ")) {
            IP = IP.substring(0, IP.length() - 1).trim();
        }
        return IP;
    }


    /**
     * 重写onActivityResult()方法，获取选取要上传文件的文件路径
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //选择了文件发送
        if (resultCode == RESULT_OK) {
            String type = data.getStringExtra("Type");
            if (type.equals("bluetooth")) {
                String path = data.getStringExtra("FilePath");
                File file = new File(path);

                Uri uri = Uri.fromFile(file);

                //打开系统蓝牙模块并发送文件
                Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                sharingIntent.setType("*/*");
                sharingIntent.setPackage("com.android.bluetooth");
                sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
                startActivity(Intent.createChooser(sharingIntent, "Share"));

                Log.d("MainActivity", uri.getPath());//log打印返回的文件路径
            }
        }
    }


    /**
     * 创建文件夹的方法createMkdir()
     */
    public static void createMkdir(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists()) {
            folder.mkdir();
        }
    }


    @Override
    public void onBackPressed()
    {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        }
//        else if (popupWindow.isShowing())
//        {
//            disappearPopupWindow();
//
//            isLooper=false;
//            closeSocket();
//        }
        else if ((System.currentTimeMillis() - mExitTime) > 2000)
        {
            //点击两次退出程序
            Toast.makeText(this, "再按一次退出程序", Toast.LENGTH_SHORT).show();
            mExitTime = System.currentTimeMillis();
        }
        else
        {
            finish();
        }


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_bluetooth) {
            /**用系统的蓝牙模块来发送文件*/
            Intent intent = new Intent(getApplicationContext(), Offline_Files_Choose_Activity.class);
            intent.putExtra("Type", "bluetooth");
            startActivityForResult(intent, 0);

        }  else if (id == R.id.nav_filesmanage) {
            /**菜单中文件管理选项，跳转到文件管理的Activity进行文件管理的操作*/
            Intent intent = new Intent(MainActivity.this, Files_Manage_Activity.class);
            startActivity(intent);

        }
        else if (id==R.id.ic_menu_wlan_trans)
        {
            //........
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void finish() {
        if (WifiApAdmin.isWifiApEnabled(wifiManager)) {
            WifiApAdmin.closeWifiAp(wifiManager);

        }

        super.finish();
        android.os.Process.killProcess(android.os.Process.myPid()); /**杀死这个应用的全部进程*/

    }

    PopupWindow popupWindow;
    /**雷达扫面界面的显示方法*/
    private void showPopupWindow(final String textStr)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (popupWindow==null) {
                    View popView = View.inflate(getApplicationContext(), R.layout.layout_pop, null);
                    iv_scanning = (ImageView) popView.findViewById(R.id.iv_scanning);
                    initAnimation();
                    popupWindow = new PopupWindow(popView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true);
                    popupWindow.setBackgroundDrawable(new ColorDrawable(0xcc000000));

                    popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
                        @Override
                        public void onDismiss()
                        {
                            isLooper=false;
                            closeSocket();
                        }
                    });
                }

               if (!popupWindow.isShowing())
                {
                    popupWindow.showAtLocation(rl_root, Gravity.CENTER, 0, 0);
                    initAnimation();
                }

                TextView textView_show=(TextView) (popupWindow.getContentView().findViewById(R.id.textView_show));
                textView_show.setText(textStr);
            }
        });

    }

    private void disappearPopupWindow()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                popupWindow.getContentView().findViewById(R.id.textView_show2).setVisibility(View.GONE);
                if (popupWindow.isShowing())
                {
                    popupWindow.dismiss();
                }
            }
        });
    }

    /**雷达扫面界面的实现方法*/
    private void initAnimation() {
        RotateAnimation rotateAnimation = new RotateAnimation(
                0, 360,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setInterpolator(new LinearInterpolator());
        rotateAnimation.setDuration(1500);
        rotateAnimation.setRepeatCount(RotateAnimation.INFINITE);
        iv_scanning.startAnimation(rotateAnimation);

    }
}
