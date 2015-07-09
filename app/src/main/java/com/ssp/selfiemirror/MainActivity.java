package com.ssp.selfiemirror;

import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {

    // Default values
    private String  mServerIp   = "192.168.178.34";  // Default Server IP address
    private Integer mServerPort = 8000;              // Default Server IP Port
    private final String mImageLocation = "/sdcard/DCIM/SelfieMirror.jpg";

    // Views
    private TextView     mTextViewServerIp;
    private TextView     mTextViewClientStatus;
    private ImageView    mImageViewPicture;
    private EditText     mEditTextServerPort;
    private ToggleButton mToggleButtonConnection;

    // Socket
    private Handler      mSocketHandler = new Handler();
    private ServerSocket mServerSocket;
    private boolean      mEnableConnection = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set view shortcuts
        mTextViewServerIp        = (TextView)  findViewById(R.id.textView_serverIp);
        mTextViewClientStatus    = (TextView)  findViewById(R.id.textView_clientStatus);
        mImageViewPicture        = (ImageView) findViewById(R.id.imageView_receivedPicture);
        mEditTextServerPort      = (EditText)  findViewById(R.id.editText_serverPort);
        mToggleButtonConnection  = (ToggleButton) findViewById(R.id.toggleButton_connection);

        // Get LAN address which will be used as the socket's server IP
        mServerIp = getLocalIpAddress();
        if (mServerIp == null)
        {
            mTextViewClientStatus.setText(getString(R.string.status_no_wifi));
        }
        else
        {
            mTextViewServerIp.setText(getString(R.string.server_ip) + " " + mServerIp + ":" + mServerPort);
        }

         mEditTextServerPort.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    mServerPort = Integer.parseInt(s.toString());
                    if (mServerIp == null) {
                        mServerIp = getLocalIpAddress();
                    }
                    if (mServerIp == null) {
                        mTextViewClientStatus.setText(getString(R.string.status_no_wifi));
                    }
                    else
                    {
                        mTextViewServerIp.setText(getString(R.string.server_ip) + " " + mServerIp + ":" + mServerPort);
                    }
                } catch(NumberFormatException nfe) {
                    mTextViewClientStatus.setText(getString(R.string.status_invalid_port));
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        mToggleButtonConnection.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                stopConnection();

                if (isChecked) {
                    // update Server IP
                    mServerIp = getLocalIpAddress();
                    if (mServerIp == null) {
                        // Report error when no WIFI is available
                        mTextViewClientStatus.setText(getString(R.string.status_no_wifi));
                        mToggleButtonConnection.setChecked(false);
                        return;
                    }

                    mTextViewServerIp.setText(getString(R.string.server_ip) + " " + mServerIp + ":" + mServerPort);
                    mEnableConnection = true;

                    // Start socket thread
                    Thread serverThread = new Thread(new ServerThread());
                    serverThread.start();
                }

                // Disable PORT edit field in case connection is activated
                mEditTextServerPort.setActivated(!isChecked);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            // always close the socket upon exiting
            mServerSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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



    /**** IP Operations ****/

    // Get the IP address of the phone's network
    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    // For now only return IPv4 addresses
                    if (!inetAddress.isLoopbackAddress()  && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("getLocalIpAddress()", ex.toString());
        }
        return null;
    }

    private void stopConnection()
    {
        if (mEnableConnection) {
            // stop old server thread
            mEnableConnection = false;
            if (!mServerSocket.isClosed()) {
                try {
                    // always close the socket upon exiting
                    mServerSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //Toast.makeText(getApplicationContext(), "Stopped connection", Toast.LENGTH_SHORT).show();
            }
            mTextViewClientStatus.setText(getString(R.string.status_disabled));
            //mToggleButtonConnection.setChecked(false);
        }
    }

    public class ServerThread implements Runnable {

        public void run() {
            try {
                if (mEnableConnection) {
                    // Start new socket
                    mSocketHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mTextViewClientStatus.setText(getString(R.string.status_disconnected));
                        }
                    });
                    mServerSocket = new ServerSocket(mServerPort);

                    while (mEnableConnection) {
                        // waiting for client connection
                        final Socket client;
                        try {
                             client = mServerSocket.accept();
                        } catch (Exception e) {
                            // Socket closed early before connection with client was done
                            break;
                        }
                        mSocketHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mTextViewClientStatus.setText(getString(R.string.status_connected));
                            }
                        });

                        try {
                            BufferedInputStream buffer = new BufferedInputStream(client.getInputStream());
                            // Store received picture picture
                            final OutputStream file = new FileOutputStream(mImageLocation);
                            int payload;
                            int numBytes = 0;
                            while ((payload = buffer.read()) != -1) {
                                //Log.d("ServerActivity", String.valueOf(payload));
                                file.write(payload);
                                numBytes++;

                                // Check if aborted early
                                if (!mEnableConnection) {
                                    numBytes = 0;
                                    break;
                                }

                                if (numBytes % 10240 == 0) // Print info every 10kB
                                {
                                    final int pictureSize = numBytes/1024; // convert Byte to kByte
                                    mSocketHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            mTextViewClientStatus.setText(getString(R.string.status_receiving) + " (" + pictureSize + "kB).");
                                            File imgFile = new  File(mImageLocation);
                                            mImageViewPicture.setImageURI(Uri.fromFile(imgFile));
                                        }
                                    });
                                }
                            }
                            file.close();

                            // Show picture if received successfully
                            if (numBytes > 0) {
                                final int pictureSize = numBytes / 1024; // convert Byte to kByte
                                mSocketHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        File imgFile = new File(mImageLocation);
                                        mImageViewPicture.setImageURI(null);
                                        mImageViewPicture.setImageURI(Uri.fromFile(imgFile));
                                        mTextViewClientStatus.setText(getString(R.string.status_received) + " (" + pictureSize + "kB).");
                                    }
                                });
                            }
                            //break;
                        } catch (Exception e) {
                            mSocketHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mTextViewClientStatus.setText(getString(R.string.status_interruption));
                                }
                            });
                            e.printStackTrace();
                        }
                    }
                } else {
                    mSocketHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mTextViewClientStatus.setText(getString(R.string.status_no_wifi));
                        }
                    });
                }
            } catch (Exception e) {
                mSocketHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTextViewClientStatus.setText(getString(R.string.status_error));
                    }
                });
                e.printStackTrace();
            }
            // Close socket after usage
            stopConnection();
        }
    }
}
