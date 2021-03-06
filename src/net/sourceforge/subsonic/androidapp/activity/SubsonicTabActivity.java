/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package net.sourceforge.subsonic.androidapp.activity;

import java.io.File;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.TextView;
import net.sourceforge.subsonic.androidapp.R;
import net.sourceforge.subsonic.androidapp.domain.MusicDirectory;
import net.sourceforge.subsonic.androidapp.service.DownloadService;
import net.sourceforge.subsonic.androidapp.service.DownloadServiceImpl;
import net.sourceforge.subsonic.androidapp.service.MusicService;
import net.sourceforge.subsonic.androidapp.service.MusicServiceFactory;
import net.sourceforge.subsonic.androidapp.util.Constants;
import net.sourceforge.subsonic.androidapp.util.ImageLoader;
import net.sourceforge.subsonic.androidapp.util.ModalBackgroundTask;
import net.sourceforge.subsonic.androidapp.util.Util;

/**
 * @author Sindre Mehus
 */
public class SubsonicTabActivity extends Activity implements OnClickListener{
    private static final String TAG = SubsonicTabActivity.class.getSimpleName();
    private static ImageLoader IMAGE_LOADER;
    private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;

    private boolean destroyed;
    private View homeButton;
    private View musicButton;
    private View searchButton;
    private View playlistButton;
    private View nowPlayingButton;
    
    //private boolean shortPress = false;
    
    private GestureDetector gestureDetector;
    View.OnTouchListener gestureListener;
    
	enum SwipeDirection
	{
		Left,
		Right
	};
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.common, menu);
    	
    	return true;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        setUncaughtExceptionHandler();
        applyTheme();
        super.onCreate(bundle);

        startService(new Intent(this, DownloadServiceImpl.class));
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        
        gestureDetector = new GestureDetector(new GestureActivity());
        gestureListener = new View.OnTouchListener() {
        	@Override
        	public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        };
    }

    @Override
    protected void onPostCreate(Bundle bundle) {
        super.onPostCreate(bundle);

        homeButton = findViewById(R.id.button_bar_home);
        homeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SubsonicTabActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                Util.startActivityWithoutTransition(SubsonicTabActivity.this, intent);
            }
        });
        
        musicButton = findViewById(R.id.button_bar_music);
        musicButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SubsonicTabActivity.this, SelectArtistActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                Util.startActivityWithoutTransition(SubsonicTabActivity.this, intent);
            }
        });

        searchButton = findViewById(R.id.button_bar_search);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SubsonicTabActivity.this, SearchActivity.class);
                intent.putExtra(Constants.INTENT_EXTRA_REQUEST_SEARCH, true);
                Util.startActivityWithoutTransition(SubsonicTabActivity.this, intent);
            }
        });

        playlistButton = findViewById(R.id.button_bar_playlists);
        playlistButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SubsonicTabActivity.this, SelectPlaylistActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                Util.startActivityWithoutTransition(SubsonicTabActivity.this, intent);
            }
        });

        nowPlayingButton = findViewById(R.id.button_bar_now_playing);
        nowPlayingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Util.startActivityWithoutTransition(SubsonicTabActivity.this, DownloadActivity.class);
            }
        });

        if (this instanceof MainActivity) {
            homeButton.setEnabled(false);
        } else if (this instanceof SelectAlbumActivity || this instanceof SelectArtistActivity) {
            musicButton.setEnabled(false);
        } else if (this instanceof SearchActivity) {
            searchButton.setEnabled(false);
        } else if (this instanceof SelectPlaylistActivity) {
            playlistButton.setEnabled(false);
        } else if (this instanceof DownloadActivity || this instanceof LyricsActivity) {
            nowPlayingButton.setEnabled(false);
        }

        updateButtonVisibility();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Util.registerMediaButtonEventReceiver(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.menu_exit:
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra(Constants.INTENT_EXTRA_NAME_EXIT, true);
                Util.startActivityWithoutTransition(this, intent);
                return true;

            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case R.id.menu_help:
                startActivity(new Intent(this, HelpActivity.class));
                return true;
        }

        return false;
    }

    @Override
    protected void onDestroy() {
    	Util.unregisterMediaButtonEventReceiver(this);
        super.onDestroy();
        destroyed = true;
        getImageLoader().clear();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean isVolumeDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN;
        boolean isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP;
        boolean isVolumeAdjust = isVolumeDown || isVolumeUp;
        boolean isJukebox = getDownloadService() != null && getDownloadService().isJukeboxEnabled();

        if (isVolumeAdjust && isJukebox) {
            getDownloadService().adjustJukeboxVolume(isVolumeUp);
            return true;
        }
        
//	    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
//	        if(event.getAction() == KeyEvent.ACTION_DOWN){
//	            event.startTracking();
//	            
//	            if(event.getRepeatCount() == 0){
//	                shortPress = true;
//	            }
//	            
//	            return true;
//	        }
//	    }
        
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void finish() {
        super.finish();
        Util.disablePendingTransition(this);
    }

    private void applyTheme() {
        String theme = Util.getTheme(this);
        if ("dark".equalsIgnoreCase(theme)) {
            setTheme(R.style.Dark);
        } else if ("light".equalsIgnoreCase(theme)) {
            setTheme(R.style.Light);
        } else if ("fullscreen".equalsIgnoreCase(theme)) {
            setTheme(R.style.Fullscreen);
        } else if ("fullscreenlight".equalsIgnoreCase(theme)) {
            setTheme(R.style.Fullscreenlight);
        }
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    private void updateButtonVisibility() {
        int visibility = Util.isOffline(this) ? View.GONE : View.VISIBLE;
        searchButton.setVisibility(visibility);
        playlistButton.setVisibility(visibility);
    }

    public void setProgressVisible(boolean visible) {
        View view = findViewById(R.id.tab_progress);
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void updateProgress(String message) {
        TextView view = (TextView) findViewById(R.id.tab_progress_message);
        if (view != null) {
            view.setText(message);
        }
    }

    public DownloadService getDownloadService() {
        // If service is not available, request it to start and wait for it.
        for (int i = 0; i < 5; i++) {
            DownloadService downloadService = DownloadServiceImpl.getInstance();
            if (downloadService != null) {
                return downloadService;
            }
            Log.w(TAG, "DownloadService not running. Attempting to start it.");
            startService(new Intent(this, DownloadServiceImpl.class));
            Util.sleepQuietly(50L);
        }
        return DownloadServiceImpl.getInstance();
    }

    protected void warnIfNetworkOrStorageUnavailable() {
        if (!Util.isExternalStoragePresent()) {
            Util.toast(this, R.string.select_album_no_sdcard);
        } else if (!Util.isOffline(this) && !Util.isNetworkConnected(this)) {
            Util.toast(this, R.string.select_album_no_network);
        }
    }

    protected synchronized ImageLoader getImageLoader() {
        if (IMAGE_LOADER == null) {
            IMAGE_LOADER = new ImageLoader(this);
        }
        return IMAGE_LOADER;
    }

    protected void downloadRecursively(final String id, final boolean save, final boolean append, final boolean autoplay) {
        ModalBackgroundTask<List<MusicDirectory.Entry>> task = new ModalBackgroundTask<List<MusicDirectory.Entry>>(this, false) {

            private static final int MAX_SONGS = 500;

            @Override
            protected List<MusicDirectory.Entry> doInBackground() throws Throwable {
                MusicService musicService = MusicServiceFactory.getMusicService(SubsonicTabActivity.this);
                MusicDirectory root = musicService.getMusicDirectory(id, false, SubsonicTabActivity.this, this);
                List<MusicDirectory.Entry> songs = new LinkedList<MusicDirectory.Entry>();
                getSongsRecursively(root, songs);
                return songs;
            }

            private void getSongsRecursively(MusicDirectory parent, List<MusicDirectory.Entry> songs) throws Exception {
                if (songs.size() > MAX_SONGS) {
                    return;
                }

                for (MusicDirectory.Entry song : parent.getChildren(false, true)) {
                    if (!song.isVideo()) {
                        songs.add(song);
                    }
                }
                for (MusicDirectory.Entry dir : parent.getChildren(true, false)) {
                    MusicService musicService = MusicServiceFactory.getMusicService(SubsonicTabActivity.this);
                    getSongsRecursively(musicService.getMusicDirectory(dir.getId(), false, SubsonicTabActivity.this, this), songs);
                }
            }

            @Override
            protected void done(List<MusicDirectory.Entry> songs) {
                DownloadService downloadService = getDownloadService();
                if (!songs.isEmpty() && downloadService != null) {
                    if (!append) {
                        downloadService.clear();
                    }
                    warnIfNetworkOrStorageUnavailable();
                    downloadService.download(songs, save, autoplay, false);
                    Util.startActivityWithoutTransition(SubsonicTabActivity.this, DownloadActivity.class);
                }
            }
        };

        task.execute();
    }

    private void setUncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        if (!(handler instanceof SubsonicUncaughtExceptionHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new SubsonicUncaughtExceptionHandler(this));
        }
    }

    /**
     * Logs the stack trace of uncaught exceptions to a file on the SD card.
     */
    private static class SubsonicUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

        private final Thread.UncaughtExceptionHandler defaultHandler;
        private final Context context;

        private SubsonicUncaughtExceptionHandler(Context context) {
            this.context = context;
            defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        }

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            File file = null;
            PrintWriter printWriter = null;
            try {

                PackageInfo packageInfo = context.getPackageManager().getPackageInfo("net.sourceforge.subsonic.androidapp", 0);
                file = new File(Environment.getExternalStorageDirectory(), "subsonic-stacktrace.txt");
                printWriter = new PrintWriter(file);
                printWriter.println("Android API level: " + Build.VERSION.SDK_INT);
                printWriter.println("Subsonic version name: " + packageInfo.versionName);
                printWriter.println("Subsonic version code: " + packageInfo.versionCode);
                printWriter.println();
                throwable.printStackTrace(printWriter);
                Log.i(TAG, "Stack trace written to " + file);
            } catch (Throwable x) {
                Log.e(TAG, "Failed to write stack trace to " + file, x);
            } finally {
                Util.close(printWriter);
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }

            }
        }
    }

	@Override
	public void onClick(View arg0) {
		// TODO Auto-generated method stub
		
	}
	
	class GestureActivity extends SimpleOnGestureListener {
	    @Override
	    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
	        try {
	            if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
	                return false;
	            
	            SwipeDirection swipe = null;
	            
	            // right to left swipe
	            if(e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
	            	swipe = SwipeDirection.Left;
	            }  else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
	            	swipe = SwipeDirection.Right;
	            }
	            
		        String name = SubsonicTabActivity.this.getClass().getSimpleName();
		        
		        switch (swipe)
		        {
		        	case Right:
		        		if (name.equalsIgnoreCase("MainActivity"))
		        		{
		        			Intent intent = new Intent(SubsonicTabActivity.this, DownloadActivity.class);
		        			SubsonicTabActivity.this.startActivity(intent);
		        		}
		        		else if (name.equalsIgnoreCase("SelectArtistActivity") || name.equalsIgnoreCase("SelectAlbumActivity"))
		        		{
			                Intent intent = new Intent(SubsonicTabActivity.this, MainActivity.class);
			                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			                SubsonicTabActivity.this.startActivity(intent);
		        		}
		        		else if (name.equalsIgnoreCase("SearchActivity"))
		        		{
			                Intent intent = new Intent(SubsonicTabActivity.this, SelectArtistActivity.class);
			                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			                SubsonicTabActivity.this.startActivity(intent);
		        		}
		        		else if (name.equalsIgnoreCase("SelectPlaylistActivity"))
		        		{
			                Intent intent = new Intent(SubsonicTabActivity.this, SearchActivity.class);
			                intent.putExtra(Constants.INTENT_EXTRA_REQUEST_SEARCH, true);
			                SubsonicTabActivity.this.startActivity(intent);
		        		}
		        		else if (name.equalsIgnoreCase("DownloadActivity") || name.equalsIgnoreCase("LyricsActivity"))
		        		{
			                Intent intent = new Intent(SubsonicTabActivity.this, SelectPlaylistActivity.class);
			                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			                SubsonicTabActivity.this.startActivity(intent);
		        		}
		        				
		        		break;
		        	case Left:
		        		if (name.equalsIgnoreCase("MainActivity"))
		        		{
			                Intent intent = new Intent(SubsonicTabActivity.this, SelectArtistActivity.class);
			                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			                SubsonicTabActivity.this.startActivity(intent);
		        		}
		        		if (name.equalsIgnoreCase("SelectArtistActivity") || name.equalsIgnoreCase("SelectAlbumActivity"))
		        		{
			                Intent intent = new Intent(SubsonicTabActivity.this, SearchActivity.class);
			                intent.putExtra(Constants.INTENT_EXTRA_REQUEST_SEARCH, true);
			                SubsonicTabActivity.this.startActivity(intent);
		        		}
		        		else if (name.equalsIgnoreCase("SearchActivity"))
		        		{
			                Intent intent = new Intent(SubsonicTabActivity.this, SelectPlaylistActivity.class);
			                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			                SubsonicTabActivity.this.startActivity(intent);
		        		}
		        		else if (name.equalsIgnoreCase("SelectPlaylistActivity"))
		        		{
		        			Intent intent = new Intent(SubsonicTabActivity.this, DownloadActivity.class);
		        			SubsonicTabActivity.this.startActivity(intent);
		        		}
		        		else if (name.equalsIgnoreCase("DownloadActivity") || name.equalsIgnoreCase("LyricsActivity"))
		        		{
			                Intent intent = new Intent(SubsonicTabActivity.this, MainActivity.class);
			                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			                SubsonicTabActivity.this.startActivity(intent);
		        		}
		        		
		        		break;
		        }
	        } catch (Exception e) {
	            // nothing
	        }
        
	        return false;
	    }
	}
	
//	@Override
//	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
//    	DownloadService service = getDownloadService();
//        int current = service.getCurrentPlayingIndex();
//        
//	    switch(keyCode){
//    		case KeyEvent.KEYCODE_VOLUME_UP:
//    			shortPress = false;
//    			
//    			if (current == -1) {
//    				service.play(0);
//    			} else {
//    				current++;
//    				service.play(current);
//    			}
//    			return true;
//    		case KeyEvent.KEYCODE_VOLUME_DOWN:
//    			shortPress = false;
//    			
//    			if (current == -1 || current == 0) {
//    				service.play(0);
//    			} else {
//    				current--;
//    				service.play(current);
//    			}
//    			return true;
//	    }
//	    
//	    return super.onKeyLongPress(keyCode, event);
//	}
//	
//	@Override
//	public boolean onKeyUp(int keyCode, KeyEvent event) {
//	    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
//	        shortPress = false;
//	        return true;
//	    } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP){
//	        shortPress = false;
//	        return true;
//	    }
//	    
//	    return super.onKeyUp(keyCode, event);
//	}
}

