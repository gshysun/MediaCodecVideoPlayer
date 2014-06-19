/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jasonsoft.mediacodecvideoplayer;


import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;

import net.simonvt.menudrawer.MenuDrawer;
import net.simonvt.menudrawer.Position;

import com.jasonsoft.mediacodecvideoplayer.data.MenuDrawerItem;
import com.jasonsoft.mediacodecvideoplayer.VideoSurfaceView;

/**
 * Displays an Android spinner widget backed by data in an array. The
 * array is loaded from the strings.xml resources file.
 */
public class MediaCodecVideoPlayerActivity extends MenuDrawerBaseActivity {

    private VideoSurfaceView mVideoSurfaceView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        /**
         * derived classes that use onCreate() overrides must always call the super constructor
         */
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);
        updateHomeIndicator(R.drawable.ic_drawer);
        mMenuDrawer.setContentView(R.layout.main);
        mMenuDrawer.setOnDrawerStateChangeListener(this);
        mVideoSurfaceView = (VideoSurfaceView) findViewById(R.id.surface_view);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case android.R.id.home:
            mMenuDrawer.toggleMenu();
            break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateHomeIndicator(int resId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            getActionBar().setHomeAsUpIndicator(resId);
        }
    }

    @Override
    protected void onMenuItemClicked(int position, MenuDrawerItem item) {
    }

    @Override
    protected void onMenuItemClicked(int position) {
        mMenuDrawer.closeMenu();

        Cursor cursor = mAdapter.getCursor();
        cursor.moveToPosition(position);
        String resolution = cursor.getString(cursor.getColumnIndex(MediaStore.Video.VideoColumns.RESOLUTION));
        String data = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA));
        if (data != null) {
            stopVideo();
            playVideo(data);
        }
    }

    @Override
    protected int getDragMode() {
        return MenuDrawer.MENU_DRAG_CONTENT;
    }

    @Override
    protected Position getDrawerPosition() {
        // START = left, END = right
        return Position.START;
    }

    @Override
    public void onDrawerStateChange(int oldState, int newState) {
        boolean isOpen = (newState == MenuDrawer.STATE_OPEN || newState == MenuDrawer.STATE_OPENING);
        updateHomeIndicator(isOpen ? R.drawable.ic_drawer_open : R.drawable.ic_drawer);
    }

    @Override
    public void onDrawerSlide(float openRatio, int offsetPixels) {
    }

    private void stopVideo() {
        mVideoSurfaceView.stopPlayback();
    }

    private void playVideo(String path) {
        mVideoSurfaceView.setVideoPath(path);
        mVideoSurfaceView.start();
    }

}
