package com.jasonsoft.mediacodecvideoplayer.adapter;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.provider.MediaStore.Video.VideoColumns;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.jasonsoft.mediacodecvideoplayer.LoadThumbnailTask;
import com.jasonsoft.mediacodecvideoplayer.R;
import com.jasonsoft.mediacodecvideoplayer.RoundedCornerImageView;
import com.jasonsoft.mediacodecvideoplayer.Utils;
import com.jasonsoft.mediacodecvideoplayer.data.MenuDrawerItem;
import com.jasonsoft.mediacodecvideoplayer.data.MenuDrawerCategory;

import java.util.List;

public class MenuAdapter extends CursorAdapter {

    public interface MenuListener {

        void onActiveViewChanged(View v);
    }

    class ViewHolder {
        RoundedCornerImageView thumbnailView;
        TextView title;
        TextView details;
    }

    private Context mContext;
    private final LayoutInflater mInflater;
    private Bitmap mDefaultPhotoBitmap;
    private LruCache<String, Bitmap> mMemoryCache;

    private List<Object> mItems;

    private MenuListener mListener;

    private int mActivePosition = -1;

    public MenuAdapter(Context context, Cursor c) {
        super(context, c);
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mDefaultPhotoBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.placeholder_empty);
    }

    public void setListener(MenuListener listener) {
        mListener = listener;
    }

    public void setActivePosition(int activePosition) {
        mActivePosition = activePosition;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        final View view = mInflater.inflate(R.layout.video_list_item, null);
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewHolder holder = (ViewHolder)view.getTag();
        if (null == holder) {
            holder =  populateViewHolder(view);
        }

        long duration = cursor.getLong(cursor.getColumnIndex(VideoColumns.DURATION));
        String details = Utils.msToDisplayDuration(duration) + " - "
                + cursor.getString(cursor.getColumnIndex(VideoColumns.RESOLUTION));
        holder.title.setText(cursor.getString(cursor.getColumnIndex(VideoColumns.TITLE)));
        holder.details.setText(details);

        long origId = cursor.getInt(cursor.getColumnIndex(VideoColumns._ID));
        Utils.loadThumbnail(mContext, origId, holder.thumbnailView, mDefaultPhotoBitmap);
    }

    ViewHolder populateViewHolder(View view) {
        ViewHolder holder = new ViewHolder();
        holder.thumbnailView = (RoundedCornerImageView) view.findViewById(R.id.video_thumbnail);
        holder.title = (TextView) view.findViewById(R.id.video_title);
        holder.details = (TextView) view.findViewById(R.id.video_details);
        view.setTag(holder);
        return holder;
    }
}
