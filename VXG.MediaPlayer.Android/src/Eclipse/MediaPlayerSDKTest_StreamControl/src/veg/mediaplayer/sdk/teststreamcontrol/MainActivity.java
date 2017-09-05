/*
 *
  * Copyright (c) 2011-2017 VXG Inc.
 *
 */

package veg.mediaplayer.sdk.teststreamcontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Button;
import android.widget.TextView;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView.OnEditorActionListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.preference.PreferenceManager;
import veg.mediaplayer.sdk.M3U8;
//import veg.mediaplayer.sdk.M3U8;
import veg.mediaplayer.sdk.MediaPlayer;
import veg.mediaplayer.sdk.MediaPlayer.PlayerNotifyCodes;
import veg.mediaplayer.sdk.MediaPlayer.PlayerRecordFlags;
import veg.mediaplayer.sdk.MediaPlayer.PlayerState;
import veg.mediaplayer.sdk.MediaPlayer.VideoShot;
import veg.mediaplayer.sdk.MediaPlayerConfig;

public class MainActivity extends Activity implements OnClickListener, MediaPlayer.MediaPlayerCallback
{
    private static final String TAG 	 = "MediaPlayerTest";
    
	public  static AutoCompleteTextView	edtIpAddress;
	public  static ArrayAdapter<String> edtIpAddressAdapter;
	public  static Set<String>			edtIpAddressHistory;
	private Button						btnConnect;
	private Button						btnHistory;
	private Button						btnShot;
	private Button						btnRecord;
	private boolean						is_record = false;

	private StatusProgressTask 			mProgressTask = null;
	
	private SharedPreferences 			settings;
    private SharedPreferences.Editor 	editor;

    private boolean 					playing = false;
    private MediaPlayer 				player = null;
    private MainActivity 				mthis = null;

    private RelativeLayout 				playerStatus = null;
    private TextView 					playerStatusText = null;
    private TextView 					playerHwStatus = null;
    private TextView 					playerFpsStatus = null;
    
	public ScaleGestureDetector 		detectors = null;	

    private MulticastLock multicastLock = null;
    
	private enum PlayerStates
	{
	  	Busy,
	  	ReadyForUse
	};

    private enum PlayerConnectType
	{
	  	Normal,
	  	Reconnecting
	};
    
	private Object waitOnMe = new Object();
	private PlayerStates player_state = PlayerStates.ReadyForUse; 
	private PlayerConnectType reconnect_type = PlayerConnectType.Normal;
	private int mOldMsg = 0;

	private Toast toastShot = null;
	private Timer playerFpsTimer = null;
	
	private Handler handler = new Handler() 
    {
		String strText = "Connecting";
		
		@Override
	    public void handleMessage(Message msg) 
	    {
	    	PlayerNotifyCodes status = (PlayerNotifyCodes) msg.obj;
	        switch (status) 
	        {
	        	case CP_CONNECT_STARTING:
	        		if (reconnect_type == PlayerConnectType.Reconnecting)
	        			strText = "Reconnecting";
	        		else
	        			strText = "Connecting";
	        			
	        		startProgressTask(strText);
	        		
	        		player_state = PlayerStates.Busy;
	    			showStatusView();
	    			
	    			reconnect_type = PlayerConnectType.Normal;
	    			setHideControls();
	    			break;
	                
		    	case VRP_NEED_SURFACE:
		    		player_state = PlayerStates.Busy;
		    		showVideoView();
			        //synchronized (waitOnMe) { waitOnMe.notifyAll(); }
					break;
	
		    	case PLP_PLAY_SUCCESSFUL:
		    		player_state = PlayerStates.ReadyForUse;
	        		stopProgressTask();
		     		
		    		setTitle(R.string.app_name);
		    		
		    		playerFpsTimer = new Timer();
		    		playerFpsTimer.schedule(new TimerTask() 
		    	    {          
		    	        @Override
		    	        public void run() 
		    	        {
		    	        	if (player != null &&
		    	        			player.getState() == PlayerState.Started)
		    	        	{
		    					runOnUiThread(new Runnable() 
						        {
						            @Override
						            public void run(){
				    	        		Log.i(TAG, "FPS: " + player.GetStatFPS());
				    	        		playerFpsStatus.setText("" + player.GetStatFPS() + " fps");
						            }
						        });
		    	        	}
		    	        }
		    
		    	    }, 0, 1000);
		    		
			        break;
	                
	        	case PLP_CLOSE_STARTING:
	        		player_state = PlayerStates.Busy;
	        		stopProgressTask();
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			setUIDisconnected();
	    			
	    			if (playerFpsTimer != null)
	    			{
	    				playerFpsTimer.cancel();
	    				playerFpsTimer.purge();
	    				playerFpsTimer = null;
	    			}
	    			break;
	                
	        	case PLP_CLOSE_SUCCESSFUL:
	        		player_state = PlayerStates.ReadyForUse;
	        		stopProgressTask();
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			System.gc();
	    			setShowControls();
	    			setUIDisconnected();
	                break;
	                
	        	case PLP_CLOSE_FAILED:
	        		player_state = PlayerStates.ReadyForUse;
	        		stopProgressTask();
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			setShowControls();
	    			setUIDisconnected();
	   			break;
	               
	        	case CP_CONNECT_FAILED:
	        		player_state = PlayerStates.ReadyForUse;
	        		stopProgressTask();
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			setShowControls();
	    			setUIDisconnected();
	    			break;
	                
	            case PLP_BUILD_FAILED:
	            	player_state = PlayerStates.ReadyForUse;
	        		stopProgressTask();
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			setShowControls();
	    			setUIDisconnected();
	    			break;
	                
	            case PLP_PLAY_FAILED:
	            	player_state = PlayerStates.ReadyForUse;
	        		stopProgressTask();
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			setShowControls();
	    			setUIDisconnected();
	    			break;
	                
	            case PLP_ERROR:
	            	player_state = PlayerStates.ReadyForUse;
	        		stopProgressTask();
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			setShowControls();
	    			setUIDisconnected();
	    			break;
	                
	            case CP_INTERRUPTED:
	            	player_state = PlayerStates.ReadyForUse;
	        		stopProgressTask();
	    			playerStatusText.setText("Disconnected");
	    			showStatusView();
	    			setShowControls();
	    			setUIDisconnected();
	    			break;
	            case CP_RECORD_STARTED:
	            	Log.v(TAG, "=handleMessage CP_RECORD_STARTED");
	            	{
	            		String sFile = player.RecordGetFileName(1);
	            		Toast.makeText(getApplicationContext(),"Record Started. File "+sFile, Toast.LENGTH_LONG).show();
	            	}
	            	break;

	            case CP_RECORD_STOPPED:
	            	Log.v(TAG, "=handleMessage CP_RECORD_STOPPED");
	            	{
	            		String sFile = player.RecordGetFileName(0);
	            		Toast.makeText(getApplicationContext(),"Record Stopped. File "+sFile, Toast.LENGTH_LONG).show();
	            	}
	            	break;

	            //case CONTENT_PROVIDER_ERROR_DISCONNECTED:
	            case CP_STOPPED:
	            case VDP_STOPPED:
	            case VRP_STOPPED:
	            case ADP_STOPPED:
	            case ARP_STOPPED:
	            	if (player_state != PlayerStates.Busy)
	            	{
		        		stopProgressTask();
	            		player_state = PlayerStates.Busy;
						if (toastShot != null)
							toastShot.cancel();
	            		player.Close();
	        			playerStatusText.setText("Disconnected");
		    			showStatusView();
		    			player_state = PlayerStates.ReadyForUse;
		    			setShowControls();
		    			setUIDisconnected();
	            	}
	                break;
	
	            case CP_ERROR_DISCONNECTED:
	            	if (player_state != PlayerStates.Busy)
	            	{
	            		player_state = PlayerStates.Busy;
						if (toastShot != null)
							toastShot.cancel();
	            		player.Close();

	        			playerStatusText.setText("Disconnected");
		    			showStatusView();
		    			player_state = PlayerStates.ReadyForUse;
		    			setUIDisconnected();
	            		
						Toast.makeText(getApplicationContext(), "Demo Version!",
								   Toast.LENGTH_SHORT).show();
						
	            	}
	                break;
	                
	            default:
	            	player_state = PlayerStates.Busy;
	        }
	    }
	};

	// callback from Native Player 
	@Override
	public int OnReceiveData(ByteBuffer buffer, int size, long pts) 
	{
		Log.e(TAG, "Form Native Player OnReceiveData: size: " + size + ", pts: " + pts);
		return 0;
	}
    
	@Override
	public int Status(int arg)
	{
		
		PlayerNotifyCodes status = PlayerNotifyCodes.forValue(arg);
		if (handler == null || status == null)
			return 0;
		
		Log.e(TAG, "Form Native Player status: " + arg);
	    switch (PlayerNotifyCodes.forValue(arg)) 
	    {
			// for asynchronus process
	        default:     
				Message msg = new Message();
				msg.obj = status;
				handler.removeMessages(mOldMsg);
				mOldMsg = msg.what;
				handler.sendMessage(msg);
	    }
	    
		return 0;
	}
	
    public String getRecordPath()
    {
    	File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
    		      Environment.DIRECTORY_DCIM), "RecordsMediaPlayer");
    	
	    if (! mediaStorageDir.exists()){
	        if (!(mediaStorageDir.mkdirs() || mediaStorageDir.isDirectory())){
	            Log.e(TAG, "<=getRecordPath() failed to create directory path="+mediaStorageDir.getPath());
	            return "";
	        }
	    }
	    return mediaStorageDir.getPath();
    }
	
    @Override
    public void onCreate(Bundle savedInstanceState) 
	{
		String  strUrl;

		setTitle(R.string.app_name);
		super.onCreate(savedInstanceState);

		WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		multicastLock = wifi.createMulticastLock("multicastLock");
		multicastLock.setReferenceCounted(true);
		multicastLock.acquire();
		
		getWindow().requestFeature(Window.FEATURE_PROGRESS);
		getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_VISIBILITY_ON);
		
		setContentView(R.layout.main);
		mthis = this;
		
		settings = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

		SharedSettings.getInstance(this).loadPrefSettings();
		SharedSettings.getInstance().savePrefSettings();
		
		playerStatus 		= (RelativeLayout)findViewById(R.id.playerStatus);
		playerStatusText 	= (TextView)findViewById(R.id.playerStatusText);
		playerHwStatus 		= (TextView)findViewById(R.id.playerHwStatus);
		playerFpsStatus 		= (TextView)findViewById(R.id.playerFpsStatus);
		
		player = (MediaPlayer)findViewById(R.id.playerView);


		strUrl = settings.getString("connectionUrl", "rtsp://184.72.239.149/vod/mp4:BigBuckBunny_115k.mov");
		
		SurfaceHolder sfhTrackHolder = player.getSurfaceView().getHolder();
		sfhTrackHolder.setFormat(PixelFormat.TRANSPARENT);
		
		HashSet<String> tempHistory = new HashSet<String>();
		tempHistory.add("rtsp://184.72.239.149/vod/mp4:BigBuckBunny_115k.mov");
		tempHistory.add("http://devimages.apple.com/iphone/samples/bipbop/gear1/prog_index.m3u8");
	
		player.setOnTouchListener(new View.OnTouchListener() 
		{
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) 
            {
                switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) 
                {
                    case MotionEvent.ACTION_DOWN:
                    {
                    	if (player.getState() == PlayerState.Paused)
                    		player.Play();
                    	else
                        	if (player.getState() == PlayerState.Started)
                        		player.Pause();
                    }
                }
            		
	        	return true;
            }
        });
		
			
		edtIpAddressHistory = settings.getStringSet("connectionHistory", tempHistory);

		this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN); 
	
		edtIpAddress = (AutoCompleteTextView)findViewById(R.id.edit_ipaddress);
		edtIpAddress.setText(strUrl);

		edtIpAddress.setOnEditorActionListener(new OnEditorActionListener() 
		{
			@Override
			public boolean onEditorAction(TextView v, int actionId,	KeyEvent event) 
			{
				if (event != null&& (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) 
				{
					InputMethodManager in = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					in.hideSoftInputFromWindow(edtIpAddress.getApplicationWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
					return true;
	
				}
				return false;
			}
		});

		btnHistory = (Button)findViewById(R.id.button_history);

		// Array of choices
		btnHistory.setOnClickListener(new View.OnClickListener() 
		{
			public void onClick(View v) 
			{
				InputMethodManager in = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				in.hideSoftInputFromWindow(MainActivity.edtIpAddress.getApplicationWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
				
				if (edtIpAddressHistory.size() <= 0)
					return;

				String urlHistory[] = {	};

				MainActivity.edtIpAddressAdapter = new ArrayAdapter<String>(MainActivity.this, R.layout.history_item, new ArrayList<String>(edtIpAddressHistory));
				MainActivity.edtIpAddress.setAdapter(MainActivity.edtIpAddressAdapter);
				MainActivity.edtIpAddress.showDropDown();
			}   
		});

		btnShot = (Button)findViewById(R.id.button_shot);

		// Array of choices
		btnShot.setOnClickListener(new View.OnClickListener() 
		{
			public void onClick(View v) 
			{
				if (player != null)
				{
					Log.e("SDL", "getVideoShot()");

	    	    	SharedSettings sett = SharedSettings.getInstance();
					
					//VideoShot frame = player.getVideoShot(200, 200);
					VideoShot frame = player.getVideoShot(-1, -1);
					if (frame == null)
						return;
					
					// get your custom_toast.xml ayout
					LayoutInflater inflater = getLayoutInflater();
	 
					View layout = inflater.inflate(R.layout.videoshot_view,
					  (ViewGroup) findViewById(R.id.videoshot_toast_layout_id));
	 
					ImageView image = (ImageView) layout.findViewById(R.id.videoshot_image);
					image.setImageBitmap(getFrameAsBitmap(frame.getData(), frame.getWidth(), frame.getHeight()));
					
					if (toastShot != null)
						toastShot.cancel();

					toastShot = new Toast(getApplicationContext());
					toastShot.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
					toastShot.setDuration(Toast.LENGTH_SHORT);
					toastShot.setView(layout);
					toastShot.show();
					
				}
			}   
		});

		btnConnect = (Button)findViewById(R.id.button_connect);
        btnConnect.setOnClickListener(this);
        
        btnRecord = (Button) findViewById(R.id.button_record);
        btnRecord.setOnClickListener( new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				is_record = !is_record;
				
				if(is_record){
					//start recording
					if(player != null){
						int record_flags = PlayerRecordFlags.forType(PlayerRecordFlags.PP_RECORD_AUTO_START) | PlayerRecordFlags.forType(PlayerRecordFlags.PP_RECORD_SPLIT_BY_TIME); //1 - auto start
						int rec_split_time = 30;	
						player.RecordSetup(getRecordPath(), record_flags, rec_split_time, 0, "");
						player.RecordStart();
					}
				}else{
					//stop recording
					if(player != null){
						player.RecordStop();
					}
				}
				
				ImageView ivLed  = (ImageView)findViewById(R.id.led);
				if(ivLed != null)
					ivLed.setImageResource( ( is_record ? R.drawable.led_red : R.drawable.led_green) ); 
				btnRecord.setText( is_record? "Stop Record":"Start Record" );
			}
        });
        
        
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.main_view);
        layout.setOnTouchListener(new OnTouchListener() 
		{
			public boolean onTouch(View v, MotionEvent event) 
			{
				InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				if (getWindow() != null && getWindow().getCurrentFocus() != null && getWindow().getCurrentFocus().getWindowToken() != null)
					inputManager.hideSoftInputFromWindow(getWindow().getCurrentFocus().getWindowToken(), 0);
				return true;
			}
		});
        
		playerStatusText.setText("VXG Stream Control Demo");
		setShowControls();
        
    }

    private int[] mColorSwapBuf = null;                        // used by saveFrame()
    public Bitmap getFrameAsBitmap(ByteBuffer frame, int width, int height)
    {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(frame);
        return bmp;
    }
    
    public void onClick(View v) 
	{
		SharedSettings.getInstance().loadPrefSettings();
		if (player != null)
		{
			if (!edtIpAddressHistory.contains(player.getConfig().getConnectionUrl()))
				edtIpAddressHistory.add(player.getConfig().getConnectionUrl());
			
			player.getConfig().setConnectionUrl(edtIpAddress.getText().toString());
			if (player.getConfig().getConnectionUrl().isEmpty())
				return;

			if (toastShot != null)
				toastShot.cancel();
			player.Close();
			if (playing)
			{
    			setUIDisconnected();
			}
			else
			{
				if (player.getConfig().getConnectionUrl().contains(".m3u8"))
				{
					asyncDownloadHLSStreams(player.getConfig().getConnectionUrl());
					return;
				}
				
				openPlayer(-1);
			}
		}
    }
 
	protected void onPause()
	{
		Log.e("SDL", "onPause()");
		super.onPause();

		editor = settings.edit();
		editor.putString("connectionUrl", edtIpAddress.getText().toString());

		editor.putStringSet("connectionHistory", edtIpAddressHistory);
		editor.commit();
		
		if (player != null)
			player.onPause();
	}

	@Override
  	protected void onResume() 
	{
		Log.e("SDL", "onResume()");
		super.onResume();
		if (player != null)
			player.onResume();
  	}

  	@Override
	protected void onStart() 
  	{
      	Log.e("SDL", "onStart()");
		super.onStart();
		if (player != null)
			player.onStart();
	}

  	@Override
	protected void onStop() 
  	{
  		Log.e("SDL", "onStop()");
		super.onStop();
		if (player != null)
			player.onStop();
		
		if (toastShot != null)
			toastShot.cancel();

	}

    @Override
    public void onBackPressed() 
    {
		if (toastShot != null)
			toastShot.cancel();
		
		player.Close();
		if (!playing)
		{
	  		super.onBackPressed();
	  		return;			
		}

		setUIDisconnected();
    }
  	
  	@Override
  	public void onWindowFocusChanged(boolean hasFocus) 
  	{
  		Log.e("SDL", "onWindowFocusChanged(): " + hasFocus);
  		super.onWindowFocusChanged(hasFocus);
		if (player != null)
			player.onWindowFocusChanged(hasFocus);
  	}

  	@Override
  	public void onLowMemory() 
  	{
  		Log.e("SDL", "onLowMemory()");
  		super.onLowMemory();
		if (player != null)
			player.onLowMemory();
  	}

  	@Override
  	protected void onDestroy() 
  	{
  		Log.e("SDL", "onDestroy()");
		if (toastShot != null)
			toastShot.cancel();
		
		if (player != null)
			player.onDestroy();
		
		stopProgressTask();
		System.gc();
		
		if (multicastLock != null) {
		    multicastLock.release();
		    multicastLock = null;
		}		
		super.onDestroy();
   	}	
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) 
    {
		super.onCreateOptionsMenu(menu);

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item)  
	{
		switch (item.getItemId())  
		{
			case R.id.main_opt_settings:   
		
				SharedSettings.getInstance().loadPrefSettings();

				Intent intentSettings = new Intent(MainActivity.this, PreferencesActivity.class);     
				startActivity(intentSettings);

				break;
			case R.id.main_opt_clearhistory:     
			
				new AlertDialog.Builder(this)
				.setTitle("Clear History")
				.setMessage("Do you really want to delete the history?")
				.setPositiveButton("Yes", new DialogInterface.OnClickListener() 
				{
					public void onClick(DialogInterface dialog, int which) 
					{

						edtIpAddressHistory.clear();

					}
				})
				.setNegativeButton("No", new DialogInterface.OnClickListener() 
				{
					public void onClick(DialogInterface dialog, int which) 
					{
						// do nothing
					}
				}).show();
				break;
			case R.id.main_opt_exit:     
				finish();
				break;

		}
		return true;
	}

    public void openPlayer(int idHLSStream) 
	{
		SharedSettings sett = SharedSettings.getInstance();

		
		final MediaPlayerConfig conf = new MediaPlayerConfig();
		
		if (idHLSStream >= 0)
			conf.setExtStream(idHLSStream);
						
		conf.setConnectionUrl(player.getConfig().getConnectionUrl());
		conf.setConnectionNetworkProtocol(sett.connectionProtocol);
		conf.setConnectionDetectionTime(sett.connectionDetectionTime);
		conf.setConnectionBufferingTime(sett.connectionBufferingTime);
		conf.setDecodingType(sett.decoderType);
		conf.setSynchroEnable(sett.synchroEnable);
		conf.setSynchroNeedDropVideoFrames(sett.synchroNeedDropVideoFrames);
		conf.setEnableAspectRatio(sett.rendererEnableAspectRatio);
		conf.setDataReceiveTimeout(30000);
		conf.setNumberOfCPUCores(2);
		
		//record config
		if(is_record){
			int record_flags = PlayerRecordFlags.forType(PlayerRecordFlags.PP_RECORD_AUTO_START); //1 - auto start					
			conf.setRecordPath(getRecordPath());
			conf.setRecordFlags(record_flags);
			conf.setRecordSplitTime(0);
			conf.setRecordSplitSize(0);
		}else{
			conf.setRecordPath("");
			conf.setRecordFlags(0);
			conf.setRecordSplitTime(0);
			conf.setRecordSplitSize(0);
		}
		Log.v(TAG, "conf record="+is_record);
        player.Open(conf, mthis);
		playing = true;
    }
	
	protected void setUIDisconnected()
	{
		setTitle(R.string.app_name);
		btnConnect.setText("Connect");
		playing = false;
	}

	protected void setHideControls()
	{
		btnShot.setVisibility(View.VISIBLE);
		edtIpAddress.setVisibility(View.GONE);
		btnHistory.setVisibility(View.GONE);
		btnConnect.setVisibility(View.GONE);
	}

	protected void setShowControls()
	{
		setTitle(R.string.app_name);
		
		btnShot.setVisibility(View.GONE);
		edtIpAddress.setVisibility(View.VISIBLE);
		btnHistory.setVisibility(View.VISIBLE);
		btnConnect.setVisibility(View.VISIBLE);
	}

	private void showStatusView() 
	{
		player.setVisibility(View.INVISIBLE);
		playerHwStatus.setVisibility(View.INVISIBLE);
		playerFpsStatus.setVisibility(View.INVISIBLE);
		playerStatus.setVisibility(View.VISIBLE);
		
	}
	
	private void showVideoView() 
	{
        playerStatus.setVisibility(View.INVISIBLE);
 		player.setVisibility(View.VISIBLE);
 		playerFpsStatus.setVisibility(View.VISIBLE);

 		SurfaceHolder sfhTrackHolder = player.getSurfaceView().getHolder();
		sfhTrackHolder.setFormat(PixelFormat.TRANSPARENT);
		
		setTitle("");
	}

	private void startProgressTask(String text)
	{
		stopProgressTask();
	    
	    mProgressTask = new StatusProgressTask(text);
	    executeAsyncTask(mProgressTask, text);
	}
	
	private void stopProgressTask()
	{
		playerStatusText.setText("");
		setTitle(R.string.app_name);
		
       	if (mProgressTask != null)
	    {
       		mProgressTask.stopTask();
	    	mProgressTask.cancel(true);
	    }
	}

	private class StatusProgressTask extends AsyncTask<String, Void, Boolean> 
    {
       	String strProgressTextSrc;
       	String strProgressText;
        Rect bounds = new Rect();
    	boolean stop = false;
      	
       	public StatusProgressTask(String text)
       	{
        	stop = false;
       		strProgressTextSrc = text;
       	}
       	
       	public void stopTask() { stop = true; }
       	
        @Override
        protected void onPreExecute() 
        {
        	super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(String... params) 
        {
            try 
            {
                if (stop) return true;

                String maxText = "Disconnected.....";//strProgressTextSrc + "....";
                int len = maxText.length();
            	playerStatusText.getPaint().getTextBounds(maxText, 0, len, bounds);

               	strProgressText = strProgressTextSrc + "...";
                
            	Runnable uiRunnable = null;
                uiRunnable = new Runnable()
                {
                    public void run()
                    {
                        if (stop) return;

    	                playerStatusText.setText(strProgressText);
    	            	
    	            	RelativeLayout.LayoutParams layoutParams = 
    	            		    (RelativeLayout.LayoutParams)playerStatusText.getLayoutParams();
    	           		
    	           		layoutParams.width = bounds.width();
    	           		playerStatusText.setLayoutParams(layoutParams);        	
    	            	playerStatusText.setGravity(Gravity.NO_GRAVITY);
    	            	
                        synchronized(this) { this.notify(); }
                    }
                };
                
               	int nCount = 4;
              	do
            	{
                    try
                    {
                    	Thread.sleep(300);
                    }
                    catch ( InterruptedException e ) { stop = true; }
                   
                    if (stop) break;
                    
                	if (nCount <= 3)
                	{
                		strProgressText = strProgressTextSrc;
                		for (int i = 0; i < nCount; i++)
                			strProgressText = strProgressText + ".";
                	}
                    
                    synchronized ( uiRunnable )
                    {
                    	runOnUiThread(uiRunnable);
                        try
                        {
                            uiRunnable.wait();
                        }
                        catch ( InterruptedException e ) { stop = true; }
                    }
                    
                    if (stop) break;
                    
                    nCount++;
                    if (nCount > 3)
                    {
                    	nCount = 1;
                    	strProgressText = strProgressTextSrc;
                    }
            	}
              	
            	while(!isCancelled());
            } 
            catch (Exception e) 
            {
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) 
        {
            super.onPostExecute(result);
            mProgressTask = null;
        }
        @Override
        protected void onCancelled() 
        {
            super.onCancelled();
        }
    }

	private DownloadAndParseHLSStream downloadHLSStreamsTask = null;
	List<M3U8.HLSStream> listStreams = null;

	private void asyncDownloadHLSStreams(String url) 
	{
	    if (downloadHLSStreamsTask == null)
		{
			//playerPanelControlTask.cancel(true);
			//playerPanelControlTask = null;
	    	downloadHLSStreamsTask = new DownloadAndParseHLSStream();
		    executeAsyncTask(downloadHLSStreamsTask, url);
		}
	}
	private void closeDownloadHLSStreams() 
	{
		if (downloadHLSStreamsTask != null)
		{
			downloadHLSStreamsTask.Stop();
			downloadHLSStreamsTask.cancel(true);
			downloadHLSStreamsTask = null;
		}
	}
	
	private class DownloadAndParseHLSStream extends AsyncTask<String, Void, Boolean> 
    {
	    private ProgressDialog dialog = new ProgressDialog(MainActivity.this);

       	public void Stop(){ stop = true; };
        private boolean stop = false;

       	@Override
        protected void onPreExecute() 
        {
            super.onPreExecute();
            dialog.setMessage("Download and parse HLS streams information...");
            dialog.setCancelable(false);
            dialog.show();
	        stop = false;
        }

        @Override
        protected Boolean doInBackground(String... params) 
        {
        	try 
            {
                do
            	{
					M3U8 m3u8 = new M3U8();
					m3u8.getDataAndParse(params[0]);
					listStreams = m3u8.getChannelList();
					Log.i(TAG, "M3U8 size:" + listStreams.size());
					for(int i = 0; i < listStreams.size(); i++)
					{
						Log.i(TAG, 
								"URL:" + listStreams.get(i).URL + " " +
								"ID:" + listStreams.get(i).ID + " " +
								"BANDWIDTH:" + listStreams.get(i).BANDWIDTH + " " +
								"CODECS:" + listStreams.get(i).CODECS + " " +
								"RESOLUTION:" + listStreams.get(i).RESOLUTION + " "
						);
					}
        			break;
            	}
            	while(!stop && !isCancelled());
            } 
            catch (Exception e) 
            {
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) 
        {
            super.onPostExecute(result);
	        if (dialog.isShowing()) 
	        	dialog.dismiss();

	        downloadHLSStreamsTask = null;
            stop = false;
            
            if (listStreams == null || listStreams.size() <= 0)
            {
            	openPlayer(-1);
            	return;
            }
            
            final Dialog list_dialog = new Dialog(MainActivity.this);
            list_dialog.setTitle("Select HLS stream:");
    		list_dialog.setContentView(R.layout.streams);
    		
    		final ListView listviewStreams = (ListView)list_dialog.findViewById(R.id.listview_streams);
    
    		listviewStreams.setVisibility(View.VISIBLE);
    
    		StreamsAdapter array_adapter = new StreamsAdapter(MainActivity.this, R.layout.streams, listStreams);
    		listviewStreams.setAdapter(array_adapter);
    		listviewStreams.setClickable(true);
    		listviewStreams.setOnItemClickListener(new AdapterView.OnItemClickListener() 
    		{
	    		@Override
	    		public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3)
	    		{
	    		  	if (list_dialog.isShowing()) 
	    		  		list_dialog.dismiss();
	    		  	
	    		  	openPlayer(position);
	    		}
    		}); 
    		list_dialog.show();		
        }

        @Override
        protected void onCancelled() 
        {
            super.onCancelled();
	        if (dialog.isShowing()) 
	        	dialog.dismiss();
	    
	        downloadHLSStreamsTask = null;
	        stop = false;
        }
    }
	
    static public <T> void executeAsyncTask(AsyncTask<T, ?, ?> task, T... params) 
    {
    	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) 
    	{
    		task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
    	}
    	else 
    	{
    		task.execute(params);
    	}
    }  
	

	
}
