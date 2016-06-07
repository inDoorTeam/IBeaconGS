package gs.ibeacon.fcu.ibeacongs;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.Vibrator;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.sails.engine.LocationRegion;
import com.sails.engine.SAILS;
import com.sails.engine.SAILSMapView;
import com.sails.engine.overlay.Marker;

import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class iBeaconGS_Main extends Activity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks,BeaconConsumer {


    private String address = "140.134.226.181";
    private int port = 8766;

    static SAILS mSails;
    static SAILSMapView mSailsMapView;
    ImageView lockcenter;
    ActionBar actionBar;
    Vibrator mVibrator;
    //Spinner floorList;
    private List<LocationRegion> locationRegions = null;
    private BeaconManager beaconManager;
    private TextView homeText;
    private TextView RssiText,UuidText,MajorText,MinorText;
    private Button stopButton;
    private Button findFriendButton;
    private ImageView locationButton;
    private Handler mHandler;
    private int Rssi, Major, Minor;
    private String Uuid;
    public int PreviousMajor = 0,PreviousMinor = 0;
    public int PreviousRssi = -1000;
    private ProgressDialog msgLoading ;
    private AlertDialog.Builder msgLoadSuccess ;
    private AlertDialog.Builder msgLogin ;
    private AlertDialog.Builder friendList;
    private TextView userEditText;
    private TextView pwdEditText;
    private MenuItem loginItem;
    private boolean isLogin = false;
    private boolean isGuiding = false;
    private ArrayAdapter<String> friendListAdapter = null;
    Socket clientSocket = new Socket();
    private String myLocation ;
    DataOutputStream outToServer;
    DataInputStream sendFromServer;
    Thread thread;

    private NavigationDrawerFragment mNavigationDrawerFragment;
    private CharSequence mTitle;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ibeacongs_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

        actionBar = getActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        mVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        //floorList = (Spinner) findViewById(R.id.spinner);
        lockcenter = (ImageView) findViewById(R.id.lockcenter);

        //ibeacon scan
        (new Thread(connecttoServer)).start();
        mHandler = new Handler();
        RssiText = (TextView) findViewById(R.id.RssiText);
        UuidText = (TextView) findViewById(R.id.UuidText);
        MajorText = (TextView) findViewById(R.id.MajorText);
        MinorText = (TextView) findViewById(R.id.MinorText);
        homeText = (TextView) findViewById(R.id.homeText);

        stopButton = (Button) findViewById(R.id.stopButton);
        findFriendButton = (Button) findViewById(R.id.FindFriendButton);
        locationButton = (ImageView) findViewById(R.id.LocationButton);
        msgLoading = new ProgressDialog(this);
        msgLoadSuccess = new AlertDialog.Builder(this);
        msgLogin = new AlertDialog.Builder(this);
        friendList = new AlertDialog.Builder(this);
        friendListAdapter = new ArrayAdapter<String>(this, android.R.layout.select_dialog_singlechoice);

        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
        //beaconManager.setBackgroundScanPeriod(500);
        //beaconManager.bind(this);

        mSails = new SAILS(this);
        mSails.setMode(SAILS.BLE_GFP_IMU);

        mSailsMapView = new SAILSMapView(this);
        ((FrameLayout) findViewById(R.id.SAILSMap)).addView(mSailsMapView);


    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.cons, PlaceholderFragment.newInstance(position + 1))
                .commit();
    }

    public void onSectionAttached(int number) {
        switch (number) {
            case 1:
                mTitle = getString(R.string.title_section1);
                homeText.setVisibility(View.VISIBLE);
                break;
            case 2:
                startScan();
                mTitle = getString(R.string.title_section2);
                homeText.setVisibility(View.INVISIBLE);
                locationButton.setVisibility(View.VISIBLE);
                msgLoading.setTitle("Loading Map");
                msgLoading.setMessage("Waiting ...");

                msgLoadSuccess.setMessage("Load successfully");
                msgLoadSuccess.setTitle("Loading Map");
                msgLoadSuccess.setCancelable(true);
                msgLoadSuccess.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });

                stopButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mSailsMapView.getRoutingManager().disableHandler();
                        stopGuiding();
                    }
                });

                locationButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (locationRegions != null){
                            mSailsMapView.getMarkerManager().clear();
                            mSailsMapView.getRoutingManager().setStartRegion(locationRegions.get(0));
                            mSailsMapView.getMarkerManager().setLocationRegionMarker(locationRegions.get(0), Marker.boundCenter(getResources().getDrawable(R.drawable.start_point)));
                            mSailsMapView.getRoutingManager().setStartMakerDrawable(Marker.boundCenter(getResources().getDrawable(R.drawable.start_point)));
                        }
                    }
                });
                mSailsMapView.post(new Runnable() {
                    @Override
                    public void run() {
                        msgLoading.show();
                        mSails.loadCloudBuilding("ad8538700fd94717bbeda154b2a1c584", "5705e42055cce32e10002a2d", new SAILS.OnFinishCallback() {
                            @Override
                            public void onSuccess(String response) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        msgLoading.dismiss();
                                        msgLoadSuccess.show();

                                        mSailsMapView.setSAILSEngine(mSails);
                                        mSailsMapView.setLocationMarker(R.drawable.circle, R.drawable.arrow, null, 35);
                                        mSailsMapView.setLocatorMarkerVisible(true);
                                        mSailsMapView.loadFloorMap(mSails.getFloorNameList().get(0));
                                        actionBar.setTitle("資電學院");
                                        mSailsMapView.autoSetMapZoomAndView();


                                        mSailsMapView.setOnRegionLongClickListener(new SAILSMapView.OnRegionLongClickListener() {
                                            @Override
                                            public void onLongClick(List<LocationRegion> locationRegions) {
                                                if (mSails.isLocationEngineStarted())
                                                    return;
                                                mVibrator.vibrate(70);
                                                mSailsMapView.getMarkerManager().clear();
                                                mSailsMapView.getRoutingManager().setStartRegion(locationRegions.get(0));
                                                mSailsMapView.getMarkerManager().setLocationRegionMarker(locationRegions.get(0), Marker.boundCenter(getResources().getDrawable(R.drawable.start_point)));
                                            }
                                        });

                                        mSailsMapView.setOnRegionClickListener(new SAILSMapView.OnRegionClickListener() {
                                            @Override
                                            public void onClick(List<LocationRegion> locationRegions) {
                                                if (mSailsMapView.getRoutingManager().getStartRegion() != null) {
                                                    LocationRegion lr = locationRegions.get(0);
                                                    mSailsMapView.getRoutingManager().setTargetMakerDrawable(Marker.boundCenterBottom(getDrawable(R.drawable.map_destination)));
                                                    mSailsMapView.getRoutingManager().getPathPaint().setColor(0xFF85b038);
                                                    mSailsMapView.getRoutingManager().setTargetRegion(lr);
                                                    mSailsMapView.getRoutingManager().enableHandler();
                                                    //startGuiding();
                                                }
                                            }
                                        });
                                    }
                                });

                            }


                            public void onFailed(String response) {
//                                msgLoadSuccess.setMessage("Load Failed");
//                                msgLoadSuccess.show();
                            }
                        });
                    }
                });
                break;
            case 3:
                mTitle = getString(R.string.title_section3);
                findFriendButton.setVisibility(View.VISIBLE);
                findFriendButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        JSONObject findFriendJSONObject = new JSONObject();
                        try {
                            findFriendJSONObject.put(JSON.KEY_STATE, JSON.STATE_FIND_FRIEND);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        if(isLogin)
                            sendtoServer(findFriendJSONObject);
                    }
                });
                homeText.setVisibility(View.INVISIBLE);
                break;
//            case 4:
//                mTitle = getString(R.string.title_section4);
//                homeText.setVisibility(View.INVISIBLE);
//                break;
//            case 5:
//                mTitle = getString(R.string.title_section5);
//                homeText.setVisibility(View.INVISIBLE);
//                break;
        }
    }
    public void startScan() {
        beaconManager.bind(this);
    }
    public void startGuiding(){
        isGuiding = true;
        RssiText.setVisibility(View.VISIBLE);
        UuidText.setVisibility(View.VISIBLE);
        MajorText.setVisibility(View.VISIBLE);
        MinorText.setVisibility(View.VISIBLE);
        stopButton.setVisibility(View.VISIBLE);
    }
    public void stopGuiding(){
        isGuiding = false;
        RssiText.setVisibility(View.INVISIBLE);
        UuidText.setVisibility(View.INVISIBLE);
        MajorText.setVisibility(View.INVISIBLE);
        MinorText.setVisibility(View.INVISIBLE);
        stopButton.setVisibility(View.INVISIBLE);
    }

    public void restoreActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            loginItem = menu.findItem(R.id.login);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        }
        switch (id) {
            case R.id.ConnecttoServer:
                if ( !clientSocket.isConnected() ){
                    (new Thread(connecttoServer)).start();
                }
                break;
            case R.id.login:
                if(!isLogin) {
                    LayoutInflater factory = LayoutInflater.from(this);
                    final View view = factory.inflate(R.layout.login, null);
                    AlertDialog.Builder loginDialog = new AlertDialog.Builder(this);
                    //loginDialog.setTitle("Login");
                    loginDialog.setView(view);
                    loginDialog.setPositiveButton("Login", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {

                            JSONObject loginJSONObject = new JSONObject();
                            userEditText = (TextView) view.findViewById(R.id.usr);
                            pwdEditText = (TextView) view.findViewById(R.id.pwd);

                            try {
                                loginJSONObject.put(JSON.KEY_USER_NAME, userEditText.getText());
                                loginJSONObject.put(JSON.KEY_USER_PWD, pwdEditText.getText());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            sendtoServer(loginJSONObject);

                        }
                    });
                    loginDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });
                    loginDialog.show();
                }
//                else if(isLogin) {
//                    AlertDialog.Builder logoutDialog = new AlertDialog.Builder(this);
//                    logoutDialog.setMessage("確定登出 ? ");
//                    logoutDialog.setPositiveButton("Logout", new DialogInterface.OnClickListener() {
//                        public void onClick(DialogInterface dialog, int id) {
//                            JSONObject logoutJSONObject = new JSONObject();
//                            try {
//                                logoutJSONObject.put(JSON.KEY_STATE, JSON.STATE_LOGOUT);
//                                sendtoServer(logoutJSONObject);
//                                //loginItem.setTitle("Login");
//                                isLogin = false;
//                            } catch (JSONException e) {
//                                e.printStackTrace();
//                            }
//
//                        }
//                    });
//                    logoutDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//                        public void onClick(DialogInterface dialog, int id) {
//                        }
//                    });
//                    logoutDialog.show();
//                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((iBeaconGS_Main) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        beaconManager.unbind(this);
    }
    @Override
    public void onBeaconServiceConnect() {
        try {
            //beaconManager.startMonitoringBeaconsInRegion(new Region("all-beacons-region", null, null, null ));
            beaconManager.startRangingBeaconsInRegion(new Region("ibeaconscan", null, null, null ));
        }
        catch (RemoteException e) {
            e.printStackTrace();
        }
        //beaconManager.setMonitorNotifier(this);
        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<org.altbeacon.beacon.Beacon> beacons, Region region) {
                if (beacons.size() > 0) {
                    org.altbeacon.beacon.Beacon beacon = beacons.iterator().next();

                    Rssi = beacon.getRssi();
                    Uuid = beacon.getId1().toUuidString();
                    Major = beacon.getId2().toInt();
                    Minor = beacon.getId3().toInt();
                    mHandler.post(scanRunnable);
                }

            }
        });

    }

    public Runnable serverhandler = new Runnable(){
        @Override
        public void run() {
            try {
                while (true) {
                    final JSONObject receiveObject;
                    if (clientSocket.isConnected()) {
                        String receiveMessage = null;
                        receiveMessage = sendFromServer.readUTF();
                        if (receiveMessage != null) {
                            receiveObject = new JSONObject(receiveMessage);
                            int state = receiveObject.getInt(JSON.KEY_STATE);
                            switch(state){
                                case JSON.STATE_WHOAMI:
                                    String name = receiveObject.getString(JSON.KEY_USER_NAME);
                                    System.out.println("You are :" + name);
                                    break;
                                case JSON.STATE_FIND_FRIEND:
                                    System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
                                    System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
                                    System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
                                    System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            JSONArray friendLocationJSONArray = null;
                                            final ArrayList<String> friendNameList = new ArrayList<String>();
                                            final ArrayList<String> friendLocList = new ArrayList<String>();
                                            friendListAdapter.clear();
                                            friendNameList.clear();
                                            friendLocList.clear();
                                            try {
                                                friendLocationJSONArray = receiveObject.getJSONArray(JSON.KEY_USER_LIST);
                                                for(int index = 0 ; index < friendLocationJSONArray.length() ; index ++){
                                                    friendListAdapter.add(friendLocationJSONArray.getJSONObject(index).getString(JSON.KEY_USER_NAME));
                                                    friendNameList.add(friendLocationJSONArray.getJSONObject(index).getString(JSON.KEY_USER_NAME));
                                                    friendLocList.add(friendLocationJSONArray.getJSONObject(index).getString(JSON.KEY_LOCATION));
                                                }
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                            friendList.setTitle("Friend List");
                                            friendList.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    dialog.dismiss();
                                                }
                                            });

                                            final JSONArray finalFriendLocationJSONArray = friendLocationJSONArray;
                                            friendList.setAdapter(friendListAdapter, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    String friendName = friendListAdapter.getItem(which);
                                                    String friendLocation = friendLocList.get(friendNameList.indexOf(friendName));
                                                    List<LocationRegion> locationRegions = null ;
                                                    locationRegions = mSails.findRegionByLabel(myLocation);
                                                    mSailsMapView.getRoutingManager().setStartRegion(locationRegions.get(0));
                                                    mSailsMapView.getMarkerManager().setLocationRegionMarker(locationRegions.get(0), Marker.boundCenter(getResources().getDrawable(R.drawable.start_point)));
                                                    mSailsMapView.getRoutingManager().setStartMakerDrawable(Marker.boundCenter(getResources().getDrawable(R.drawable.start_point)));

                                                    locationRegions = mSails.findRegionByLabel(friendLocation);
                                                    LocationRegion lr = locationRegions.get(0);
                                                    mSailsMapView.getRoutingManager().setTargetMakerDrawable(Marker.boundCenterBottom(getDrawable(R.drawable.map_destination)));
                                                    mSailsMapView.getRoutingManager().getPathPaint().setColor(0xFF85b038);
                                                    mSailsMapView.getRoutingManager().setTargetRegion(lr);
                                                    mSailsMapView.getRoutingManager().enableHandler();
                                                    //startGuiding();
                                                }
                                            });
                                            friendList.show();
                                        }
                                    });
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
    public Runnable connecttoServer = new Runnable() {
        @Override
        public void run() {
            try {
                clientSocket = new Socket(InetAddress.getByName(address), port);
                outToServer = new DataOutputStream( clientSocket.getOutputStream() );
                sendFromServer = new DataInputStream( clientSocket.getInputStream() );
                JSONObject receiveObject;
                if(!clientSocket.isInputShutdown()) {
                    String receiveMessage = sendFromServer.readUTF();
                    if(receiveMessage != null){
                        receiveObject = new JSONObject(receiveMessage);
                        isLogin = receiveObject.getBoolean(JSON.KEY_RESULT);
                        if(isLogin){
                            showToastFromBackground("Login Success");
                            //loginItem.setTitle("Logout");
                            //msgLogin.show();
                            (new Thread(serverhandler)).start();
                        }
                        else{
                            showToastFromBackground("Login Fail");
                        }
                    }

                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
    public void sendtoServer(JSONObject sendtoServer){
        if(clientSocket.isConnected()) {
            try {
                outToServer.writeUTF(sendtoServer.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            if( PreviousMajor != Major || PreviousMinor != Minor ) {
                //set start region
               // List<LocationRegion> locationRegions = null;
                JSONObject ibeaconJSONObject = new JSONObject();
                if (Major == 4369 && Minor == 8738) {
                    myLocation = "資電234 - 網際網路及軟體工程學程實驗室";
                    locationRegions = mSails.findRegionByLabel(myLocation);
                } else if(Major == 43690 && Minor == 65505){
                    myLocation = "資電201 - 資訊系辦公室";
                    locationRegions = mSails.findRegionByLabel(myLocation);
                } else if(Major == 257 && Minor == 65505){
                    myLocation = "資電222 - 第三國際會議廳";
                    locationRegions = mSails.findRegionByLabel(myLocation);
                }
                if (isGuiding) {
                    mSailsMapView.getRoutingManager().setStartRegion(locationRegions.get(0));
                    mSailsMapView.getMarkerManager().setLocationRegionMarker(locationRegions.get(0), Marker.boundCenter(getResources().getDrawable(R.drawable.start_point)));
                    mSailsMapView.getRoutingManager().setStartMakerDrawable(Marker.boundCenter(getResources().getDrawable(R.drawable.start_point)));
                }

                try {
                    ibeaconJSONObject.put(JSON.KEY_STATE, JSON.STATE_SEND_IBEACON);
                    ibeaconJSONObject.put(JSON.KEY_LOCATION, myLocation);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if(isLogin)
                    sendtoServer(ibeaconJSONObject);

            }
            PreviousRssi = Rssi;
            PreviousMajor = Major;
            PreviousMinor = Minor;

//            RssiText.setText( "Rssi  : " + Rssi);
//            UuidText.setText( "Uuid  : " + Uuid);
//            MajorText.setText("Major : " + Major);
//            MinorText.setText("Minor : " + Minor);
            if(myLocation != null){
                MinorText.setText( "You are at : " + myLocation);
            }
        }
    };


    public void showToastFromBackground(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast msgToast = Toast.makeText(iBeaconGS_Main.this, "", Toast.LENGTH_SHORT);
                msgToast.setText(msg);
                msgToast.show();
            }
        });
    }

    void mapViewInitial() {
        //establish a connection of SAILS engine into SAILS MapView.
        mSailsMapView.setSAILSEngine(mSails);

        //set location pointer icon.
        mSailsMapView.setLocationMarker(R.drawable.circle, R.drawable.arrow, null, 35);

        //set location marker visible.
        mSailsMapView.setLocatorMarkerVisible(true);

        //load first floor map in package.
        mSailsMapView.loadFloorMap(mSails.getFloorNameList().get(0));
        actionBar.setTitle("資電學院");

        //Auto Adjust suitable map zoom level and position to best view position.
        mSailsMapView.autoSetMapZoomAndView();

        /*
        //set start region
        List<LocationRegion> locationRegions;
        if( rssi < -50 ) {
            locationRegions = mSails.findRegionByLabel("資電222 - 第三國際會議廳");
            mVibrator.vibrate(70);
            mSailsMapView.getMarkerManager().clear();
            mSailsMapView.getRoutingManager().setStartRegion(locationRegions.get(0));
            mSailsMapView.getMarkerManager().setLocationRegionMarker(locationRegions.get(0), Marker.boundCenter(getResources().getDrawable(R.drawable.start_point)));
        }
        else{
            locationRegions = mSails.findRegionByLabel("資電201 - 資訊系辦公室");
            mVibrator.vibrate(70);
            mSailsMapView.getMarkerManager().clear();
            mSailsMapView.getRoutingManager().setStartRegion(locationRegions.get(0));
            mSailsMapView.getMarkerManager().setLocationRegionMarker(locationRegions.get(0), Marker.boundCenter(getResources().getDrawable(R.drawable.start_point)));
        }*/

        mSailsMapView.setOnRegionClickListener(new SAILSMapView.OnRegionClickListener() {
            @Override
            public void onClick(List<LocationRegion> locationRegions) {
                if (mSailsMapView.getRoutingManager().getStartRegion() != null) {
                    LocationRegion lr = locationRegions.get(0);
                    mSailsMapView.getRoutingManager().setTargetMakerDrawable(Marker.boundCenterBottom(getDrawable(R.drawable.map_destination)));
                    mSailsMapView.getRoutingManager().getPathPaint().setColor(0xFF85b038);
                    mSailsMapView.getRoutingManager().setTargetRegion(lr);
                    mSailsMapView.getRoutingManager().enableHandler();
                }
            }
        });

        /*
        List<LocationRegion> lr = mSails.findRegionByLabel("資電201 - 資訊系辦公室");
        mSailsMapView.getRoutingManager().setTargetMakerDrawable(Marker.boundCenterBottom(getDrawable(R.drawable.map_destination)));
        mSailsMapView.getRoutingManager().getPathPaint().setColor(0xFF85b038);
        mSailsMapView.getRoutingManager().setTargetRegion(lr.get(0));
        mSailsMapView.getRoutingManager().enableHandler();
        if (mSailsMapView.getRoutingManager().enableHandler())
            distanceView.setVisibility(View.VISIBLE);
      /*
        //set location region click call back.
        mSailsMapView.setOnRegionClickListener(new SAILSMapView.OnRegionClickListener() {
            @Override
            public void onClick(List<LocationRegion> locationRegions) {
                LocationRegion lr = locationRegions.get(0);
                //begin to routing
                if (mSails.isLocationEngineStarted()) {
                    //set routing start point to current user location.
                    mSailsMapView.getRoutingManager().setStartRegion(PathRoutingManager.MY_LOCATION);
                    //set routing end point marker icon.
                    mSailsMapView.getRoutingManager().setTargetMakerDrawable(Marker.boundCenterBottom(getDrawable(R.drawable.destination)));

                    //set routing path's color.
                    mSailsMapView.getRoutingManager().getPathPaint().setColor(0xFF35b3e5);

                    endRouteButton.setVisibility(View.VISIBLE);
                    currentFloorDistanceView.setVisibility(View.VISIBLE);
                    msgView.setVisibility(View.VISIBLE);

                } else {
                    mSailsMapView.getRoutingManager().setTargetMakerDrawable(Marker.boundCenterBottom(getDrawable(R.drawable.map_destination)));
                    mSailsMapView.getRoutingManager().getPathPaint().setColor(0xFF85b038);
                    if (mSailsMapView.getRoutingManager().getStartRegion() != null)
                        endRouteButton.setVisibility(View.VISIBLE);
                }

                //set routing end point location.
                mSailsMapView.getRoutingManager().setTargetRegion(lr);
                //begin to route.
                if (mSailsMapView.getRoutingManager().enableHandler())
                    distanceView.setVisibility(View.VISIBLE);
            }
        });

        mSailsMapView.getPinMarkerManager().setOnPinMarkerClickCallback(new PinMarkerManager.OnPinMarkerClickCallback() {
            @Override
            public void OnClick(MarkerManager.LocationRegionMarker locationRegionMarker) {
                Toast.makeText(getApplication(), "(" + Double.toString(locationRegionMarker.locationRegion.getCenterLatitude()) + "," +
                        Double.toString(locationRegionMarker.locationRegion.getCenterLongitude()) + ")", Toast.LENGTH_SHORT).show();
            }
        });


        //set location region long click call back.
        mSailsMapView.setOnRegionLongClickListener(new SAILSMapView.OnRegionLongClickListener() {
            @Override
            public void onLongClick(List<LocationRegion> locationRegions) {
                if (mSails.isLocationEngineStarted())
                    return;
                mVibrator.vibrate(70);
                mSailsMapView.getMarkerManager().clear();
                mSailsMapView.getRoutingManager().setStartRegion(locationRegions.get(0));
                mSailsMapView.getMarkerManager().setLocationRegionMarker(locationRegions.get(0), Marker.boundCenter(getResources().getDrawable(R.drawable.start_point)));
            }
        });

        //design some action in floor change call back.
        mSailsMapView.setOnFloorChangedListener(new SAILSMapView.OnFloorChangedListener() {
            @Override
            public void onFloorChangedBefore(String floorName) {
                //get current map view zoom level.
                zoomSav = mSailsMapView.getMapViewPosition().getZoomLevel();
            }

            @Override
            public void onFloorChangedAfter(final String floorName) {
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        //check is locating engine is start and current brows map is in the locating floor or not.
                        if (mSails.isLocationEngineStarted() && mSailsMapView.isInLocationFloor()) {
                            //change map view zoom level with animation.
                            mSailsMapView.setAnimationToZoom(zoomSav);
                        }
                    }
                };
                new Handler().postDelayed(r, 1000);

                int position = 0;
                for (String mS : mSails.getFloorNameList()) {
                    if (mS.equals(floorName))
                        break;
                    position++;
                }
                floorList.setSelection(position);
            }
        });

        //design some action in mode change call back.
        mSailsMapView.setOnModeChangedListener(new SAILSMapView.OnModeChangedListener() {
            @Override
            public void onModeChanged(int mode) {
                if (((mode & SAILSMapView.LOCATION_CENTER_LOCK) == SAILSMapView.LOCATION_CENTER_LOCK) && ((mode & SAILSMapView.FOLLOW_PHONE_HEADING) == SAILSMapView.FOLLOW_PHONE_HEADING)) {
                    lockcenter.setImageDrawable(getResources().getDrawable(R.drawable.center3));
                } else if ((mode & SAILSMapView.LOCATION_CENTER_LOCK) == SAILSMapView.LOCATION_CENTER_LOCK) {
                    lockcenter.setImageDrawable(getResources().getDrawable(R.drawable.center2));
                } else {
                    lockcenter.setImageDrawable(getResources().getDrawable(R.drawable.center1));
                }
            }
        });

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, mSails.getFloorDescList());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        floorList.setAdapter(adapter);
        floorList.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!mSailsMapView.getCurrentBrowseFloorName().equals(mSails.getFloorNameList().get(position)))
                    mSailsMapView.loadFloorMap(mSails.getFloorNameList().get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    */
    }


}
