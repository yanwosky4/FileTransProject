/**
 * 文件传输
 *
 * */

package com.yh.filestrans;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.yh.MainActivity;
import com.yh.R;
import com.yh.connect.Constant;
import com.yh.offlinefiles.Offline_Files_Choose_Activity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Files_Trans_Activity extends Activity {

    private long mExitTime;
    private TextView tvMsg;

    private TextView txtIP;

    private Button btnSend;
    private Button btnExit;

    private Handler handler3;
    private ServerSocket server;
    private Socket_Manager socket_Manager;
    private ProgressDialog progressDialog;
    private ProgressDialog recevieProgressDialog;
    private boolean out_recieve = true;
    private String File_Name = null;

    //判断文件是否发送成功
    public boolean fileTransTrueOrFalse = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.files_trans_main);
        FilesTransActivityContent.mContent = Files_Trans_Activity.this;
        out_recieve = true;
        tvMsg = (TextView) findViewById(R.id.tvMsg);
        txtIP = (TextView) findViewById(R.id.txtIP);

        //将对方的IP地址直接写入txtIP这个EditText控件
        txtIP.setText(MainActivity.IP_DuiFangde);

        btnSend = (Button) findViewById(R.id.btnSend);
        btnExit = (Button) findViewById(R.id.btnExit);

        btnSend.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), Offline_Files_Choose_Activity.class);// 启动文件管理
                startActivityForResult(intent, 0);
            }
        });
        btnExit.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder=new AlertDialog.Builder(Files_Trans_Activity.this);
                builder.setTitle("提示");
                builder.setMessage("退出后将与当前相连的客户端 断开连接");
                builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                });
                builder.setNegativeButton("取消",null);
                builder.create().show();
            }
        });


        //打开进度条
        handler3 = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    //跳出进度条
                    case 0:
                        showRecieveProgressDialog();
                        break;
                    case 1:
                        tvMsg.setText(msg.obj.toString());
                        break;
                    case 2:
                        Toast.makeText(getApplicationContext(), msg.obj.toString(), Toast.LENGTH_SHORT).show();
                        break;
                    //发送结束后返回3，进度条显示完成，去除误差
                    case 3:
                        recevieProgressDialog.setProgress(100);
                        recevieProgressDialog.getButton(DialogInterface.BUTTON_POSITIVE).setText("完成");
                        recevieProgressDialog.getButton(DialogInterface.BUTTON_POSITIVE).setClickable(true);
                        break;
                }
            }

        };
        Thread listener = new Thread(new Runnable() {
            @Override
            public void run() {
                // 绑定端口
                int port = 9999;
                while (port > 9000) {
                    try {
                        server = new ServerSocket(port);// 初始化server
                        break;
                    } catch (Exception e) {
                        port--;
                    }
                }

                if (server != null) { // 如果server不空
                    socket_Manager = new Socket_Manager(server);// 初始化socketManager

                    boolean isServer=Files_Trans_Activity.this.getIntent().getBooleanExtra("isServer",false);
                    if (isServer)
                    {
                        socket_Manager.acceptHeartJump();
                    }
                    else
                    {
                        socket_Manager.sendHeartJump();
                    }

                    Message.obtain(handler3, 1, "本机IP地址：" + GetIpAddress()/* + " 监听端口:" + port */).sendToTarget();// 不知道这句干嘛
                    while (true) { // 接收文件，死循环
//							if (!out_recieve){
//								out_recieve=true;
//								break;
//							}
                        socket_Manager.ReceiveFile();// 定义一个字符串response

                        // Message.obtain(handler3, 0, response).sendToTarget();
                    }
                }
                else {
                    Message.obtain(handler3, 1, "未能绑定端口").sendToTarget();
                }
            }
        });
        listener.start();

    }

    @Override
    public void onBackPressed() {
        //super.onBackPressed();
        btnExit.performClick();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        socket_Manager.isLooper=false;
        socket_Manager.closeSeverSicket();
    }

    @Override
    public void finish() {

        //退出这个页面时，清空IP_DuiFangde的值
        MainActivity.IP_DuiFangde = null;

        super.finish();


    }

    //存储传输文件的名字以及后缀名
    public static String Trans_File_Name = "";
    public static String Trans_File_Type = "";
    public static int Trans_File_Size;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 选择了文件发送

        if (resultCode == RESULT_OK) {

            final String fileName = data.getStringExtra("FileName");
            final String path = data.getStringExtra("FilePath");

            File_Name=fileName;
            showProgressDialog();// 显示进度条

            Trans_File_Name = fileName;
            if ((fileName.indexOf(".")) > 0) {
                Trans_File_Type = fileName.substring(fileName.indexOf(".") + 1, fileName.length());
            }

            final String ipAddress = txtIP.getText().toString();
            final int port = Integer.parseInt("9999");

            String response = socket_Manager.SendFile(fileName, path, ipAddress, port);
        }
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
     * 进度条对话框
     * 显示发送当前进度
     */
    protected void showProgressDialog() {

        progressDialog = new ProgressDialog(FilesTransActivityContent.mContent);
        progressDialog.setCancelable(false);
        progressDialog.setTitle("正在发送:   "+File_Name);
        // 设置进度条样式
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        // 设置进度条最大值
        progressDialog.setMax(100);
        // 完成按钮
        progressDialog.setButton(DialogInterface.BUTTON_POSITIVE, "发送中...", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which)
            {

            }
        });
        if (!FilesTransActivityContent.mContent.isFinishing()) {
            progressDialog.show();
            progressDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        }


    }

    //显示接收进度
    protected void showRecieveProgressDialog() {

        recevieProgressDialog = new ProgressDialog(FilesTransActivityContent.mContent);
        recevieProgressDialog.setCancelable(false);
        recevieProgressDialog.setTitle("正在接收:   "+File_Name);
        // 设置进度条样式
        recevieProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        // 设置进度条最大值
        recevieProgressDialog.setMax(100);
        // 完成按钮
        recevieProgressDialog.setButton(DialogInterface.BUTTON_POSITIVE, "接受中...", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which)
            {

            }
        });
        if (!FilesTransActivityContent.mContent.isFinishing()) {
            recevieProgressDialog.show();
            recevieProgressDialog.getButton(DialogInterface.BUTTON_POSITIVE).setClickable(false);
        }

    }

    private class Socket_Manager
    {

        private ServerSocket fileServer;
        private ServerSocket heartJumpServer;

        private int currentProcess;
        private int pgs;
        private int length;
        private double sumL;
        private byte[] sendBytes;
        private Socket socket;
        private DataOutputStream dos;
        private FileInputStream fis;
        private boolean bool;

        private long heartJumpLastTime=0;
        private boolean isLooper=true;

        public Socket_Manager(ServerSocket server)
        {
            isLooper=true;
            this.fileServer = server;

            checkIsHeartJump();
        }

        public void checkIsHeartJump()
        {
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    boolean isLocalLooper=true;
                    heartJumpLastTime=System.currentTimeMillis()+5000;

                    while (isLocalLooper&&isLooper)
                    {
//                        Log.i("main","checkIsHeartJump   distance=******"+(System.currentTimeMillis()-heartJumpLastTime));
//                        Log.i("main","checkIsHeartJump   heartJumpLastTime=******"+heartJumpLastTime);
                        //对方挂掉了
                        if (System.currentTimeMillis()-heartJumpLastTime>1000*3+500)
                        {
                            isLocalLooper=false;

                            Files_Trans_Activity.this.finish();
                            Files_Trans_Activity.this.runOnUiThread(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    Toast.makeText(Files_Trans_Activity.this,"对方客户端已掉线",Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                        else
                        {
                            try
                            {
                                Thread.sleep(2000);
                            } catch (InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }).start();
        }

        //接收心跳
        public void acceptHeartJump()
        {
            new Thread(new Runnable() {
                @Override
                public void run()
                {
                    while (isLooper)
                    {
                        Socket socket=null;
                        InputStream inputStream=null;
                        OutputStream outputStream = null;
                        try
                        {
                            if (heartJumpServer==null)
                            {
                                heartJumpServer=new ServerSocket(Constant.HEART_JUMP_PORT);
                            }
                            socket = heartJumpServer.accept();

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
                            if (stringBuffer.toString().equals(Constant.APP_CLIENT_HEARTJUMP_FLAG))
                            {
                                heartJumpLastTime=System.currentTimeMillis();

                                outputStream=socket.getOutputStream();
                                outputStream.write(Constant.APP_SRVER_HEARTJUMP_FLAG.getBytes());
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

//                            if (serverSocket != null)
//                            {try {serverSocket.close();} catch (IOException e) {e.printStackTrace();}}
                        }
                    }

                }
            }).start();



        }

        //发送心跳
        public void sendHeartJump()
        {
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    while (isLooper)
                    {
                        Socket socket = null;
                        OutputStream outputStream = null;
                        InputStream inputStream = null;

                        try
                        {
                            socket=new Socket(txtIP.getText().toString(),Constant.HEART_JUMP_PORT);

                            outputStream = socket.getOutputStream();
                            // 3.具体的输出过程
                            outputStream.write(Constant.APP_CLIENT_HEARTJUMP_FLAG.getBytes());
                            socket.shutdownOutput();

                            inputStream=socket.getInputStream();
                            byte[] b = new byte[20];
                            int len;
                            StringBuffer stringBuffer=new StringBuffer();
                            while((len = inputStream.read(b)) != -1){
                                String str = new String(b,0,len);
                                stringBuffer.append(str);
                            }
                            if (stringBuffer.toString().equals(Constant.APP_SRVER_HEARTJUMP_FLAG))
                            {
                                heartJumpLastTime=System.currentTimeMillis();
                            }

                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
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

                        try
                        {
                            Thread.sleep(1500);
                        }
                        catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
        }

        // 接收文件
        public void ReceiveFile() {

            try {
                // 接收文件名
                Socket socket = fileServer.accept();
                //接受到的文件存放在SD卡文件快传目录下面
                String pathdir = Environment.getExternalStorageDirectory().getPath() + "/"+ Constant.SAVEFILE_DIR;
                byte[] inputByte = null;
                long length = 0;
                DataInputStream dis = null;
                FileOutputStream fos = null;
                String filePath;
                long L;

                try {


                    dis = new DataInputStream(socket.getInputStream());
                    File f = new File(pathdir);
                    if (!f.exists()) {
                        f.mkdir();
                    }
                    File_Name = dis.readUTF();
                    filePath = pathdir + "/" + File_Name;

                    fos = new FileOutputStream(new File(filePath));
                    inputByte = new byte[1024];
                    L = f.length();
                    System.out.println("文件路径：" + filePath);
                    // System.out.println(dis.readLong());
                    double rfl = 0;
                    L = dis.readLong();
                    System.out.println("文件长度" + L + "kB");
                    System.out.println("开始接收数据...");
                    //弹出进度条信号
                    handler3.sendEmptyMessage(0);

                    while ((length = dis.read(inputByte, 0, inputByte.length)) > 0) {
                        rfl += length;
                        fos.write(inputByte, 0, (int) length);
                        pgs = (int) (rfl * 100 / 1024.0 / L);
                        //实时更新进度条
                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                recevieProgressDialog.setProgress(pgs);

                            }
                        });


                        System.out.println("rfl:" + rfl);
                        System.out.println("psg:" + pgs);
                        fos.flush();

                    }
                    fos.close();
                    dis.close();
                    socket.close();
                    System.out.println("完成接收：" + filePath);
                    //接受完成信号
                    handler3.sendEmptyMessage(3);
                    pgs = 0;
                    // return "完成接收：" + filePath;


                } catch (Exception e) {
                    e.printStackTrace();
                }
                // return "完成接收：" + dis.readUTF();
            } catch (Exception e) {
                e.printStackTrace();
                // return "接收错误";
            }

        }

        public String SendFile(String fileName, final String path, final String ipAddress, final int port) {

            length = 0;
            sumL = 0;
            sendBytes = null;
            socket = null;
            dos = null;
            fis = null;
            bool = false;
            new Thread(new Runnable() {
                public void run() {
                    try {
                        File file = new File(path); // 要传输的文件路径
                        long l = file.length();
                        socket = new Socket();
                        socket.connect(new InetSocketAddress(ipAddress, port));
                        dos = new DataOutputStream(socket.getOutputStream());
                        fis = new FileInputStream(path);
                        sendBytes = new byte[1024];

                        dos.writeUTF(file.getName());// 传递文件名

                        dos.flush();
                        dos.writeLong((long) file.length() / 1024 + 1);
                        dos.flush();

                        while ((length = fis.read(sendBytes, 0, sendBytes.length)) > 0) {
                            sumL += length;
                            currentProcess = (int) ((sumL / l) * 100);
                            runOnUiThread(new Runnable() {

                                @Override
                                public void run()
                                {
                                    progressDialog.setProgress(currentProcess);
                                    if (progressDialog.getProgress()==progressDialog.getMax())
                                    {
                                        progressDialog.getButton(DialogInterface.BUTTON_POSITIVE).setText("完成");
                                        progressDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                                    }
                                }
                            });
                            System.out.println("currentProcess" + currentProcess);
                            System.out.println("已传输：" + ((sumL / l) * 100) + "%");
                            dos.write(sendBytes, 0, length);
                            dos.flush();
                        }
                        //记录文件的大小
                        Trans_File_Size = (int) sumL;
                        //更改判断文件发送成功与否的标志位
                        fileTransTrueOrFalse = true;

                        /*************************************文件发送成功后，向数据库里面记录一条数据******************************/
                        if (fileTransTrueOrFalse) {
                        }


                        // 虽然数据类型不同，但JAVA会自动转换成相同数据类型后在做比较
                        if (sumL == l) {
                            bool = true;
                        }

                    } catch (Exception e) {
                        System.out.println("客户端文件传输异常");
                        bool = false;
                        e.printStackTrace();
                    } finally {
                        if (dos != null)
                            try {
                                dos.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        if (fis != null)
                            try {
                                fis.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        if (socket != null)
                            try {
                                socket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                    }
                }

            }).start();
            System.out.println(bool ? "成功" : "失败");
            return fileName + " 发送完成";


        }

        public void closeSeverSicket()
        {
            if (fileServer!=null)
            {
                try
                {
                    fileServer.close();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
            if (heartJumpServer!=null)
            {
                try
                {
                    heartJumpServer.close();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }

    }

}
