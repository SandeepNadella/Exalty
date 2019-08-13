/*
 * Copyright (c) 2018 Spotify AB
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.rekam.exalty;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.ContentApi;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.android.appremote.api.error.AuthenticationFailedException;
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp;
import com.spotify.android.appremote.api.error.LoggedOutException;
import com.spotify.android.appremote.api.error.NotLoggedInException;
import com.spotify.android.appremote.api.error.OfflineModeException;
import com.spotify.android.appremote.api.error.SpotifyConnectionTerminatedException;
import com.spotify.android.appremote.api.error.SpotifyDisconnectedException;
import com.spotify.android.appremote.api.error.SpotifyRemoteServiceException;
import com.spotify.android.appremote.api.error.UnsupportedFeatureVersionException;
import com.spotify.android.appremote.api.error.UserNotAuthorizedException;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.client.ErrorCallback;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.Empty;
import com.spotify.protocol.types.Image;
import com.spotify.protocol.types.ListItem;
import com.spotify.protocol.types.PlaybackSpeed;
import com.spotify.protocol.types.PlayerContext;
import com.spotify.protocol.types.PlayerState;
import com.spotify.protocol.types.Repeat;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RemotePlayerActivity extends FragmentActivity {

    private static final String TAG = RemotePlayerActivity.class.getSimpleName();

    private static final String CLIENT_ID = "c68326b3dac74bc99cb02bc90019a7eb";
    private static final String REDIRECT_URI = "rekamspotify://callback";

    private static SpotifyAppRemote mSpotifyAppRemote;

    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    ImageView mCoverArtImageView;
    AppCompatImageButton mToggleShuffleButton;
    AppCompatImageButton mPlayPauseButton;
    AppCompatImageButton mToggleRepeatButton;
    AppCompatSeekBar mSeekBar;
    Button mPlayerStateButton;
    private static List<String> mLabels = new ArrayList<>();
    private static List<String> mUris = new ArrayList<>();
    private static List<String> mNames = new ArrayList<>();
    private static String mMood;
    List<View> mViews;
    TrackProgressBar mTrackProgressBar;


    private final ErrorCallback mErrorCallback = new ErrorCallback() {
        @Override
        public void onError(Throwable throwable) {
            RemotePlayerActivity.this.logError(throwable, "Something went wrong...");
            Log.println(Log.ERROR,"ErrorCallback",throwable.getMessage());
        }
    };

    Subscription<PlayerState> mPlayerStateSubscription;
    private String mSharePlaylistURL;

    public void onSubscribedToPlayerStateButtonClicked(View view) {

        if (mPlayerStateSubscription != null && !mPlayerStateSubscription.isCanceled()) {
            mPlayerStateSubscription.cancel();
            mPlayerStateSubscription = null;
        }

        mPlayerStateButton.setVisibility(View.VISIBLE);

        mPlayerStateSubscription = (Subscription<PlayerState>) mSpotifyAppRemote.getPlayerApi()
                .subscribeToPlayerState()
                .setEventCallback(mPlayerStateEventCallback)
                .setLifecycleCallback(new Subscription.LifecycleCallback() {
                    @Override
                    public void onStart() {
                        //logMessage("Event: start");
                    }

                    @Override
                    public void onStop() {
//                        logMessage("Event: end");
                    }
                })
                .setErrorCallback(throwable -> {
                    mPlayerStateButton.setVisibility(View.INVISIBLE);
                    logError(throwable, "Subscribed to PlayerContext failed!");
                });
    }

    @SuppressLint("SetTextI18n")
    private final Subscription.EventCallback<PlayerContext> mPlayerContextEventCallback = new Subscription.EventCallback<PlayerContext>() {
        @Override
        public void onEvent(PlayerContext playerContext) {
            mPlayerContextButton.setText(String.format(Locale.US, "%s\n%s", playerContext.title, playerContext.subtitle));
            mPlayerContextButton.setTag(playerContext);
        }
    };
    @SuppressLint("SetTextI18n")
    private final Subscription.EventCallback<PlayerState> mPlayerStateEventCallback = new Subscription.EventCallback<PlayerState>() {
        @Override
        public void onEvent(PlayerState playerState) {

            Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.mipmap.mediaservice_shuffle, getTheme());
            if (!playerState.playbackOptions.isShuffling) {
                mToggleShuffleButton.setImageDrawable(drawable);
                DrawableCompat.setTint(mToggleShuffleButton.getDrawable(), Color.WHITE);
            } else {
                mToggleShuffleButton.setImageDrawable(drawable);
                DrawableCompat.setTint(mToggleShuffleButton.getDrawable(), getResources().getColor(R.color.cat_medium_green));
            }

            if (playerState.playbackOptions.repeatMode == Repeat.ALL) {
                mToggleRepeatButton.setImageResource(R.mipmap.mediaservice_repeat_all);
                DrawableCompat.setTint(mToggleRepeatButton.getDrawable(), getResources().getColor(R.color.cat_medium_green));
            } else if (playerState.playbackOptions.repeatMode == Repeat.ONE) {
                mToggleRepeatButton.setImageResource(R.mipmap.mediaservice_repeat_one);
                DrawableCompat.setTint(mToggleRepeatButton.getDrawable(), getResources().getColor(R.color.cat_medium_green));
            } else {
                mToggleRepeatButton.setImageResource(R.mipmap.mediaservice_repeat_off);
                DrawableCompat.setTint(mToggleRepeatButton.getDrawable(), Color.WHITE);
            }

            mPlayerStateButton.setText(String.format(Locale.US, "%s\n%s", playerState.track.name, playerState.track.artist.name));
            mPlayerStateButton.setTag(playerState);

            // Update progressbar
            if (playerState.playbackSpeed > 0) {
                mTrackProgressBar.unpause();
            } else {
                mTrackProgressBar.pause();
            }

            // Invalidate play / pause
            if (playerState.isPaused) {
                mPlayPauseButton.setImageResource(R.drawable.ic_play);
            } else {
                mPlayPauseButton.setImageResource(R.drawable.ic_pause);
            }

            // Get image from track
            mSpotifyAppRemote.getImagesApi()
                    .getImage(playerState.track.imageUri, Image.Dimension.LARGE)
                    .setResultCallback(new CallResult.ResultCallback<Bitmap>() {
                        @Override
                        public void onResult(Bitmap bitmap) {
                            mCoverArtImageView.setImageBitmap(bitmap);
                        }
                    });

            // Invalidate seekbar length and position
            mSeekBar.setMax((int) playerState.track.duration);
            mTrackProgressBar.setDuration(playerState.track.duration);
            mTrackProgressBar.update(playerState.playbackPosition);

            mSeekBar.setEnabled(true);
        }
    };
    Button mPlayerContextButton;
    Subscription<PlayerContext> mPlayerContextSubscription;
    public void onSubscribedToPlayerContextButtonClicked(View view) {
        if (mPlayerContextSubscription != null && !mPlayerContextSubscription.isCanceled()) {
            mPlayerContextSubscription.cancel();
            mPlayerContextSubscription = null;
        }

        mPlayerContextSubscription = (Subscription<PlayerContext>) mSpotifyAppRemote.getPlayerApi()
                .subscribeToPlayerContext()
                .setEventCallback(mPlayerContextEventCallback)
                .setErrorCallback(throwable -> {
                    logError(throwable, "Subscribed to PlayerContext failed!");
                });
    }
    private void onConnected() {
        for (View input : mViews) {
            input.setEnabled(true);
        }

        onSubscribedToPlayerStateButtonClicked(null);
        onSubscribedToPlayerContextButtonClicked(null);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_remote_layout);

        mCoverArtImageView = findViewById(R.id.image);
        mPlayerStateButton = findViewById(R.id.current_track_label);
        mToggleRepeatButton = findViewById(R.id.toggle_repeat_button);
        mToggleShuffleButton = findViewById(R.id.toggle_shuffle_button);
        mPlayPauseButton = findViewById(R.id.play_pause_button);
        mPlayerContextButton = findViewById(R.id.current_context_label);

        mSeekBar = findViewById(R.id.seek_to);
        mSeekBar.setEnabled(false);
        mSeekBar.getProgressDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        mSeekBar.getIndeterminateDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);

        mTrackProgressBar = new TrackProgressBar(mSeekBar);

        mViews = Arrays.asList(
                mPlayPauseButton,
                findViewById(R.id.seek_forward_button),
                findViewById(R.id.seek_back_button),
                findViewById(R.id.skip_prev_button),
                findViewById(R.id.skip_next_button),
                mToggleRepeatButton,
                mToggleShuffleButton,
                mSeekBar);

        SpotifyAppRemote.setDebugMode(true);
        onDisconnected();
        onConnectAndAuthorizedClicked(null);
        mLabels = getIntent().getStringArrayListExtra("labels");
        mMood = getIntent().getStringExtra("mood");

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, "Sharing spotify playlist URL from Exalty");
                i.putExtra(Intent.EXTRA_TEXT, mSharePlaylistURL);
                startActivity(Intent.createChooser(i, "Share spotify playlist URL"));
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        SpotifyAppRemote.disconnect(mSpotifyAppRemote);
        onDisconnected();
    }

    private void onDisconnected() {
        for (View view : mViews) {
            view.setEnabled(false);
        }
        mCoverArtImageView.setImageResource(R.drawable.widget_placeholder);
        mPlayerStateButton.setText(R.string.title_current_track);
        mToggleRepeatButton.clearColorFilter();
        mToggleRepeatButton.setImageResource(R.drawable.btn_repeat);
        mToggleShuffleButton.clearColorFilter();
        mToggleShuffleButton.setImageResource(R.drawable.btn_shuffle);
        mPlayerStateButton.setVisibility(View.INVISIBLE);
    }

    public void onConnectAndAuthorizedClicked(View view) {
        connect(true);
    }

    private void connect(boolean showAuthView) {

        SpotifyAppRemote.disconnect(mSpotifyAppRemote);

        SpotifyAppRemote.connect(
                getApplication(),
                new ConnectionParams.Builder(CLIENT_ID)
                        .setRedirectUri(REDIRECT_URI)
                        .showAuthView(showAuthView)
                        .build(),
                new Connector.ConnectionListener() {
                    @Override
                    public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                        mSpotifyAppRemote = spotifyAppRemote;
                        RemotePlayerActivity.this.onConnected();
                        onRequestTokenClicked();
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        if (error instanceof SpotifyRemoteServiceException) {
                            if (error.getCause() instanceof SecurityException) {
                                logError(error, "SecurityException");
                            } else if (error.getCause() instanceof IllegalStateException) {
                                logError(error, "IllegalStateException");
                            }
                        } else if (error instanceof NotLoggedInException) {
                            logError(error, "NotLoggedInException");
                        } else if (error instanceof AuthenticationFailedException) {
                            logError(error, "AuthenticationFailedException");
                        } else if (error instanceof CouldNotFindSpotifyApp) {
                            logError(error, "CouldNotFindSpotifyApp");
                        } else if (error instanceof LoggedOutException) {
                            logError(error, "LoggedOutException");
                        } else if (error instanceof OfflineModeException) {
                            logError(error, "OfflineModeException");
                        } else if (error instanceof UserNotAuthorizedException) {
                            logError(error, "UserNotAuthorizedException");
                        } else if (error instanceof UnsupportedFeatureVersionException) {
                            logError(error, "UnsupportedFeatureVersionException");
                        } else if (error instanceof SpotifyDisconnectedException) {
                            logError(error, "SpotifyDisconnectedException");
                        } else if (error instanceof SpotifyConnectionTerminatedException) {
                            logError(error, "SpotifyConnectionTerminatedException");
                        } else {
                            logError(error, String.format("Connection failed: %s", error));
                        }
                        RemotePlayerActivity.this.onDisconnected();
                    }
                });
    }

    public void onImageClicked(final View view) {
        if (mSpotifyAppRemote != null) {
            mSpotifyAppRemote.getPlayerApi()
                    .getPlayerState()
                    .setResultCallback(new CallResult.ResultCallback<PlayerState>() {
                        @Override
                        public void onResult(final PlayerState playerState) {
                            PopupMenu menu = new PopupMenu(RemotePlayerActivity.this, view);

                            menu.getMenu().add(720, 720, 0, "Large (720px)");
                            menu.getMenu().add(480, 480, 1, "Medium (480px)");
                            menu.getMenu().add(360, 360, 2, "Small (360px)");
                            menu.getMenu().add(240, 240, 3, "X Small (240px)");
                            menu.getMenu().add(144, 144, 4, "Thumbnail (144px)");

                            menu.show();

                            menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                                @Override
                                public boolean onMenuItemClick(MenuItem item) {
                                    mSpotifyAppRemote.getImagesApi()
                                            .getImage(playerState.track.imageUri, Image.Dimension.values()[item.getOrder()])
                                            .setResultCallback(new CallResult.ResultCallback<Bitmap>() {
                                                @Override
                                                public void onResult(Bitmap bitmap) {
                                                    mCoverArtImageView.setImageBitmap(bitmap);
                                                }
                                            });
                                    return false;
                                }
                            });
                        }
                    })
                    .setErrorCallback(mErrorCallback);
        }
    }

    private void playUri(String uri) {
        mSpotifyAppRemote.getPlayerApi()
                .play(uri)
                .setResultCallback(new CallResult.ResultCallback<Empty>() {
                    @Override
                    public void onResult(Empty empty) {
//                        RemotePlayerActivity.this.logMessage("Play successful");
                    }
                })
                .setErrorCallback(mErrorCallback);
    }

    private void playUri(String uri, String name) {
        mSpotifyAppRemote.getPlayerApi()
                .play(uri)
                .setResultCallback(new CallResult.ResultCallback<Empty>() {
                    @Override
                    public void onResult(Empty empty) {
//                        RemotePlayerActivity.this.logMessage("Playing "+name);
                        showSnackBar(null, "Playing "+name);
                    }
                })
                .setErrorCallback(mErrorCallback);
    }

    private void playUris(List<String> uris, List<String> names) {
        if(!uris.isEmpty()) {
            Log.println(Log.ERROR,"PlayURI",uris.get(0));
            playUri(uris.get(0), names.get(0));
            if(uris.size()>1) {
                for (int i=1;i<uris.size();i++) {
                    Log.println(Log.ERROR,"QueueURI",uris.get(i));
                    queueUri(uris.get(i));
                }
            }
        }
    }
    private void playUris(List<String> uris) {
        if(!uris.isEmpty()) {
            Log.println(Log.ERROR,"PlayURI",uris.get(0));
            playUri(uris.get(0));
            if(uris.size()>1) {
                for (int i=1;i<uris.size();i++) {
                    Log.println(Log.ERROR,"QueueURI",uris.get(i));
                    queueUri(uris.get(i));
                }
            }
        }
    }
    private void queueUris(List<String> uris) {
        if(!uris.isEmpty()) {
                for (int i=0;i<uris.size();i++) {
                    Log.println(Log.ERROR,"QueueURI",uris.get(i));
                    queueUri(uris.get(i));
                }
        }
    }

    private void queueUri(String uri) {
        mSpotifyAppRemote.getPlayerApi()
                .queue(uri)
                .setResultCallback(new CallResult.ResultCallback<Empty>() {
                    @Override
                    public void onResult(Empty empty) {
//                        RemotePlayerActivity.this.logMessage("Play successful");
                    }
                })
                .setErrorCallback(mErrorCallback);
    }

    public void onToggleShuffleButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .toggleShuffle()
                .setResultCallback(new CallResult.ResultCallback<Empty>() {
                    @Override
                    public void onResult(Empty empty) {
//                        RemotePlayerActivity.this.logMessage("Toggle shuffle successful");
                    }
                })
                .setErrorCallback(mErrorCallback);
    }

    public void onToggleRepeatButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .toggleRepeat()
                .setResultCallback(new CallResult.ResultCallback<Empty>() {
                    @Override
                    public void onResult(Empty empty) {
//                        RemotePlayerActivity.this.logMessage("Toggle repeat successful");
                    }
                })
                .setErrorCallback(mErrorCallback);
    }

    public void onSetShuffleTrueButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .setShuffle(true)
                .setResultCallback(new CallResult.ResultCallback<Empty>() {
                    @Override
                    public void onResult(Empty empty) {
//                        RemotePlayerActivity.this.logMessage("Set shuffle true successful");
                    }
                })
                .setErrorCallback(mErrorCallback);
    }

    public void onSetRepeatAllButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .setRepeat(Repeat.ALL)
                .setResultCallback(new CallResult.ResultCallback<Empty>() {
                    @Override
                    public void onResult(Empty empty) {
//                        RemotePlayerActivity.this.logMessage("Set repeat ALL successful");
                    }
                })
                .setErrorCallback(mErrorCallback);
    }

    public void onSkipPreviousButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .skipPrevious()
                .setResultCallback(new CallResult.ResultCallback<Empty>() {
                    @Override
                    public void onResult(Empty empty) {
//                        RemotePlayerActivity.this.logMessage("Skip previous successful");
                    }
                })
                .setErrorCallback(mErrorCallback);
    }

    public void onPlayPauseButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi().getPlayerState().setResultCallback(new CallResult.ResultCallback<PlayerState>() {
            @Override
            public void onResult(PlayerState playerState) {
                if (playerState.isPaused) {
                    mSpotifyAppRemote.getPlayerApi()
                            .resume()
                            .setResultCallback(new CallResult.ResultCallback<Empty>() {
                                @Override
                                public void onResult(Empty empty) {
//                                    RemotePlayerActivity.this.logMessage("Play current track successful");
                                }
                            })
                            .setErrorCallback(mErrorCallback);
                } else {
                    mSpotifyAppRemote.getPlayerApi()
                            .pause()
                            .setResultCallback(new CallResult.ResultCallback<Empty>() {
                                @Override
                                public void onResult(Empty empty) {
//                                    RemotePlayerActivity.this.logMessage("Pause successful");
                                }
                            })
                            .setErrorCallback(mErrorCallback);
                }
            }
        });
    }

    public void onSkipNextButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .skipNext()
                .setResultCallback(new CallResult.ResultCallback<Empty>() {
                    @Override
                    public void onResult(Empty data) {
//                        RemotePlayerActivity.this.logMessage("Skip next successful");
                    }
                })
                .setErrorCallback(mErrorCallback);
    }

    public void onSeekBack(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .seekToRelativePosition(-15000)
                .setResultCallback(new CallResult.ResultCallback<Empty>() {
                    @Override
                    public void onResult(Empty data) {
//                        RemotePlayerActivity.this.logMessage("Seek back 15 sec successful");
                    }
                })
                .setErrorCallback(mErrorCallback);
    }

    public void onSeekForward(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .seekToRelativePosition(15000)
                .setResultCallback(new CallResult.ResultCallback<Empty>() {
                    @Override
                    public void onResult(Empty data) {
//                        RemotePlayerActivity.this.logMessage("Seek forward 15 sec successful");
                    }
                })
                .setErrorCallback(mErrorCallback);
    }

    public void onGetFitnessRecommendedContentItems(View view) {
        mSpotifyAppRemote.getContentApi()
                .getRecommendedContentItems(ContentApi.ContentType.FITNESS)
                .setResultCallback(listItems -> mSpotifyAppRemote.getContentApi()
                        .getChildrenOfItem(listItems.items[0], 3, 0)
                        .setResultCallback(childListItems -> {
                            showDialog("RecommendedContentItems", gson.toJson(childListItems));
                            ListItem item = null;
                            for (int i = 0; i < childListItems.items.length; ++i) {
                                item = childListItems.items[i];
                                if (item.playable) {
//                                    logMessage(String.format("Trying to play %s", item.title));
                                    break;
                                } else {
                                    item = null;
                                }
                            }
                        })
                        .setErrorCallback(mErrorCallback)).setErrorCallback(mErrorCallback);
    }

    private void logError(Throwable throwable, String msg) {
        // Toast.makeText(this, "Error: " + msg, Toast.LENGTH_SHORT).show();
        Log.e(TAG, msg, throwable);
    }

    private void logMessage(String msg) {
        logMessage(msg, Toast.LENGTH_SHORT);
    }

    private void logMessage(String msg, int duration) {
        Toast.makeText(this, msg, duration).show();
        Log.d(TAG, msg);
    }

    private void showDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .create()
                .show();
    }

    public void onPlaybackSpeedButtonClicked(View view) {
        PopupMenu menu = new PopupMenu(this, view);

        menu.getMenu().add(50, 50, 0, "0.5x");
        menu.getMenu().add(80, 80, 1, "0.8x");
        menu.getMenu().add(100, 100, 2, "1x");
        menu.getMenu().add(120, 120, 3, "1.2x");
        menu.getMenu().add(150, 150, 4, "1.5x");
        menu.getMenu().add(200, 200, 5, "2x");
        menu.getMenu().add(300, 300, 6, "3x");

        menu.show();

        menu.setOnMenuItemClickListener(item -> {
            mSpotifyAppRemote.getPlayerApi()
                    .setPodcastPlaybackSpeed(PlaybackSpeed.PodcastPlaybackSpeed.values()[item.getOrder()])
                    .setResultCallback(new CallResult.ResultCallback<Empty>() {
                        @Override
                        public void onResult(Empty empty) {
//                            RemotePlayerActivity.this.logMessage("Play podcast successful");
                        }
                    })
                    .setErrorCallback(mErrorCallback);
            return false;
        });
    }

    private class TrackProgressBar {

        private static final int LOOP_DURATION = 500;
        private final SeekBar mSeekBar;
        private final Handler mHandler;


        private final SeekBar.OnSeekBarChangeListener mSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mSpotifyAppRemote.getPlayerApi().seekTo(seekBar.getProgress())
                        .setErrorCallback(mErrorCallback);
            }
        };

        private final Runnable mSeekRunnable = new Runnable() {
            @Override
            public void run() {
                int progress = mSeekBar.getProgress();
                mSeekBar.setProgress(progress + LOOP_DURATION);
                mHandler.postDelayed(mSeekRunnable, LOOP_DURATION);
            }
        };

        private TrackProgressBar(SeekBar seekBar) {
            mSeekBar = seekBar;
            mSeekBar.setOnSeekBarChangeListener(mSeekBarChangeListener);
            mHandler = new Handler();
        }

        private void setDuration(long duration) {
            mSeekBar.setMax((int) duration);
        }

        private void update(long progress) {
            mSeekBar.setProgress((int) progress);
        }

        private void pause() {
            mHandler.removeCallbacks(mSeekRunnable);
        }

        private void unpause() {
            mHandler.removeCallbacks(mSeekRunnable);
            mHandler.postDelayed(mSeekRunnable, LOOP_DURATION);
        }
    }

    public static final int AUTH_TOKEN_REQUEST_CODE = 0x10;
    public static final int AUTH_CODE_REQUEST_CODE = 0x11;

    private final OkHttpClient mOkHttpClient = new OkHttpClient();
    private String mAccessToken;
    private String mAccessCode;
    private Call mCall;

    @Override
    protected void onDestroy() {
//        cancelCall();
        super.onDestroy();
    }

    public void showSnackBar(View view, String msg) {
            if(view == null) {
               view = findViewById(R.id.app_remote_layout);
            }
            final Snackbar snackbar = Snackbar.make(view, msg, 6000);
            snackbar.getView().setBackgroundColor(ContextCompat.getColor(this, R.color.snackBarBackground));
            TextView tv = (TextView) snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
            tv.setTextColor(Color.DKGRAY);
            if(snackbar.isShown()) {
                snackbar.dismiss();
                snackbar.show();
            } else {
                snackbar.show();
            }
    }

    private void getUserProfileAndStartSearch() {
        if (mAccessToken == null) {
            showSnackBar(null, "Access token is required");
        }
        final Request request = new Request.Builder()
                .url("https://api.spotify.com/v1/me")
                .addHeader("Authorization","Bearer " + mAccessToken)
                .build();

        mCall = mOkHttpClient.newCall(request);

        mCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                showSnackBar(null, "Failed to fetch data");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    final JSONObject jsonObject = new JSONObject(response.body().string());
                    Log.println(Log.ERROR, "Response",jsonObject.toString());
//                    RemotePlayerActivity.this.logMessage("Welcome "+jsonObject.getString("display_name"));
                    if(mMood!=null) {
                        List<String> li = new ArrayList<>();
                        li.add(mMood);
                        getSpotifyUris(li);
                    } else {
                        getSpotifyUris(mLabels);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.println(Log.DEBUG, "Content", response.body().string());
                    showSnackBar(null, "Failed to process data");
                }
            }
        });
    }

    private void getSpotifyUris(List<String> labels) {
        mUris.clear();
        mNames.clear();
        if (mAccessToken == null) {
            showSnackBar(null, "Access token is required");
        }
        String encLabel = "";
        String q = "";
        for(int i=0;i<labels.size();i++) {
            String label = labels.get(i);
            if(i==0) {
                encLabel = label.replace(" ", "+");
                // Searching for only one improves accuracy
                break;
            } else if(i==1) {
                encLabel += " OR "+label;
                break;
            }else{
                //This is not used since Spotify doesn't support more than 2 words in search
                encLabel += " OR "+label;
            }
        }
        q = "?q=" + encLabel + "&type=playlist";
            final Request request = new Request.Builder()
                    .url("https://api.spotify.com/v1/search"+q)
                    .addHeader("Authorization", "Bearer " + mAccessToken)
                    .build();

            mCall = mOkHttpClient.newCall(request);

            mCall.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                    showSnackBar(null, "Failed to fetch data");
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        final JSONObject jsonObject = new JSONObject(response.body().string());
                        Log.println(Log.ERROR, "Search Response", jsonObject.toString());
                        if(jsonObject.length()>0) {
                            Iterator<String> jsonObjKeys = jsonObject.keys();
                            while (jsonObjKeys.hasNext()) {
                                String key = jsonObjKeys.next();
                                if ("playlists".equals(key)) {
                                    JSONObject tracks = jsonObject.getJSONObject(key);
                                    JSONArray items = tracks.getJSONArray("items");
                                    for (int i = 0; i < items.length(); i++) {
                                        String uri = ((JSONObject) items.get(0)).getString("uri");
                                        String name = ((JSONObject) items.get(0)).getString("name");
                                        mSharePlaylistURL = ((JSONObject) items.get(0)).getJSONObject("external_urls").getString("spotify");
                                        mUris.add(uri);
                                        mNames.add(name);
                                        //play only most popular playlist for now
                                        break;
                                    }
                                }
                            }
                        }
                        playUris(mUris,mNames);
                        finishActivity(RESULT_OK);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.println(Log.DEBUG, "Content", response.body().string());
                        showSnackBar(null, "Failed to process data");
                    }
                }
            });
    }

    public void onRequestCodeClicked() {
        final AuthenticationRequest request = getAuthenticationRequest(AuthenticationResponse.Type.CODE);
        AuthenticationClient.openLoginActivity(this, AUTH_CODE_REQUEST_CODE, request);
    }

    public void onRequestTokenClicked() {
        final AuthenticationRequest request = getAuthenticationRequest(AuthenticationResponse.Type.TOKEN);
        AuthenticationClient.openLoginActivity(this, AUTH_TOKEN_REQUEST_CODE, request);
    }

    private AuthenticationRequest getAuthenticationRequest(AuthenticationResponse.Type type) {
        return new AuthenticationRequest.Builder(CLIENT_ID, type, getRedirectUri().toString())
                .setShowDialog(false)
                .setScopes(new String[]{"user-read-email"})
                .setCampaign("your-campaign-token")
                .build();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, data);

        if (AUTH_TOKEN_REQUEST_CODE == requestCode) {
            mAccessToken = response.getAccessToken();
            if(mAccessToken!=null) {
                getUserProfileAndStartSearch();
            }
        } else if (AUTH_CODE_REQUEST_CODE == requestCode) {
            mAccessCode = response.getCode();
        }
    }

    private void cancelCall() {
        if (mCall != null) {
            mCall.cancel();
        }
    }

    private Uri getRedirectUri() {
        return new Uri.Builder()
                .scheme("rekamwebspotify")
                .authority("callback")
                .build();
    }
}
