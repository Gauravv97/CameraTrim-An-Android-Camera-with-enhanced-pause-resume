package com.csaproject.gehu.myapplication;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.request.RequestOptions;
import com.woxthebox.draglistview.DragItemAdapter;
import com.woxthebox.draglistview.DragListView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    FloatingActionButton fabAdd;
    private ArrayList<entry> mItemArray;
    private DragListView mDragListView;
    private boolean mIsSwiped = false, mIsChangeRequired = false;
    protected Context _context;
    private ItemAdapter listAdapter;
    private Toolbar toolbar;
    public ArrayList<Integer> multiSelectList;

    private DBHandler dbHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDragListView = findViewById(R.id.drag_list_view);
        mDragListView.getRecyclerView().setVerticalScrollBarEnabled(true);
        fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                check_permissions();
            }
        });
        mItemArray = new ArrayList<>();
        dbHandler = new DBHandler(this, null, null, 1);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        init_ListView();
        setupListRecyclerView();
    }

    private void init_ListView() {
        mDragListView = (DragListView) findViewById(R.id.drag_list_view);
        mDragListView.getRecyclerView().setVerticalScrollBarEnabled(true);
        mDragListView.setDragListListener(new DragListView.DragListListenerAdapter() {
            @Override
            public void onItemDragStarted(int position) {
                mIsSwiped = true;
            }

            @Override
            public void onItemDragEnded(int fromPosition, int toPosition) {
                if (fromPosition != toPosition) {
                    dbHandler.move_entry(fromPosition, toPosition);

                }
                mIsSwiped = false;
                mIsChangeRequired = false;
                listAdapter.refreshData();
                listAdapter.notifyDataSetChanged();
            }
        });
    }

    private void CheckIfListisEmpty() {
        if (mDragListView.getAdapter().getItemCount() > 0) {
            mDragListView.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.empty_view)).setVisibility(View.GONE);
        } else {
            mDragListView.setVisibility(View.GONE);
            ((TextView) findViewById(R.id.empty_view)).setVisibility(View.VISIBLE);
        }
    }

    public void multi_select(int position) {
        if (multiSelectList.contains(position)) {
            multiSelectList.remove(Integer.valueOf(position));
            getSupportActionBar().setTitle(multiSelectList.size() + "/" + dbHandler.rCount());
        } else {
            multiSelectList.add(position);
            getSupportActionBar().setTitle(multiSelectList.size() + "/" + dbHandler.rCount());
        }
        listAdapter.notifyDataSetChanged();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.

        if (listAdapter.mIsMultiSelect) {
            getMenuInflater().inflate(R.menu.multi_select, menu);
        }

        return true;
    }

    public void SetMultiToolbar() {
        multiSelectList = new ArrayList<>();
        mItemArray = new ArrayList<entry>();
        mDragListView.setDragEnabled(false);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetToolbar();
            }
        });
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(0 + "/" + dbHandler.rCount());
        invalidateOptionsMenu();
        findViewById(R.id.fabAdd).setVisibility(View.GONE);
    }

    public void resetToolbar() {
        mDragListView.setDragEnabled(true);
        multiSelectList.clear();
        listAdapter.mIsMultiSelect = false;
        getSupportActionBar().setTitle(R.string.app_name);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        toolbar.setNavigationOnClickListener(null);
        invalidateOptionsMenu();
        findViewById(R.id.fabAdd).setVisibility(View.VISIBLE);
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        if (listAdapter.mIsMultiSelect) {
            resetToolbar();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupListRecyclerView();
    }

    private void setupListRecyclerView() {
        mDragListView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
        mItemArray = new ArrayList<>();
        for (int i = 0; i < dbHandler.rCount(); i++)
            mItemArray.add(dbHandler.getresult(i));
        listAdapter = new ItemAdapter(mItemArray, R.layout.list_item, R.id.dragHandle_listView, false);
        mDragListView.setAdapter(listAdapter, false);
        mDragListView.setCanDragHorizontally(false);
        CheckIfListisEmpty();
    }

    class ItemAdapter extends DragItemAdapter<entry, ItemAdapter.ViewHolder> {

        private int mLayoutId;
        private int mGrabHandleId;
        private boolean mDragOnLongPress;
        public boolean mIsMultiSelect = false;
        private Context mContext;
        private entry data[];
        private MediaMetadataRetriever retriever = new MediaMetadataRetriever();


        ItemAdapter(ArrayList<entry> list, int layoutId, int grabHandleId, boolean dragOnLongPress) {
            mLayoutId = layoutId;
            mGrabHandleId = grabHandleId;
            mDragOnLongPress = dragOnLongPress;
            mContext = MainActivity.this;
            refreshData();
            setItemList(list);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
            return new ViewHolder(view);
        }

        public void refreshData() {
            data = FetchImages();
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);

            File file = new File(data[holder.getAdapterPosition()].getPath());
            holder.mTextView.setText(file.getName().replaceFirst("[.][^.]+$", ""));
            holder.mDateTime.setText((new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((new Date(file.lastModified()))).substring(0, 10)));
            retriever.setDataSource(mContext, Uri.fromFile(file));
            String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long timeInMillisec = Long.parseLong(time);
            holder.mPages.setText(getTimeStringFromMs(timeInMillisec));
            Glide.with(mContext).load(Uri.fromFile(file)).apply(RequestOptions.bitmapTransform(new CenterCrop())).into(holder.mImageView);
            holder.mPopUp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final PopupMenu popup = new PopupMenu(mContext, holder.mPopUp);
                    popup.getMenuInflater().inflate(R.menu.popup, popup.getMenu());
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            int i = item.getItemId();
                            if (i == R.id.trim) {
                                //do something
                                String trimfilepath = data[holder.getAdapterPosition()].getPath();
                                Intent intent = new Intent(MainActivity.this, trimActivity.class);
                                intent.putExtra("filepath",trimfilepath);
                                startActivity(intent);

                                return true;
                            }
                            if (i == R.id.resume) {
                                //do something
                                String trimfilepath = data[holder.getAdapterPosition()].getPath();
                                Intent intent = new Intent(MainActivity.this, RecordVideo.class);
                                intent.putExtra("filepath",trimfilepath);
                                startActivity(intent);

                                return true;
                            }
                            return onMenuItemClick(item);
                        }
                    });
                    popup.show();
                }
                });


            if (mIsMultiSelect) {
                holder.mPopUp.setVisibility(View.GONE);
                holder.mGrabHandle.setVisibility(View.GONE);
                holder.checkBox.setVisibility(View.VISIBLE);
                if (((MainActivity) mContext).multiSelectList.contains(holder.getAdapterPosition()))
                    holder.checkBox.setChecked(true);
                else holder.checkBox.setChecked(false);
            } else {
                holder.mPopUp.setVisibility(View.VISIBLE);
                holder.mGrabHandle.setVisibility(View.VISIBLE);
                holder.checkBox.setVisibility(View.GONE);
            }
        }

        @Override
        public long getUniqueItemId(int position) {
            return mItemList.get(position).getID();
        }

        class ViewHolder extends DragItemAdapter.ViewHolder {
            ImageView mImageView, mGrabHandle,mPopUp;
            TextView mTextView, mDateTime, mPages;
            CheckBox checkBox;

            ViewHolder(final View itemView) {
                super(itemView, mGrabHandleId, mDragOnLongPress);
            /*if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                itemView.findViewById(R.id.item_layout).setForeground(mContext.getDrawable(R.drawable.shadow_item));
            else itemView.findViewById(R.id.dragView_listView).setVisibility(View.VISIBLE);*/


                checkBox = itemView.findViewById(R.id.multiSelectcheckBox);
                mTextView = (TextView) itemView.findViewById(R.id.text_listItem);
                mDateTime = itemView.findViewById(R.id.dateTime_listItem);
                mPages = itemView.findViewById(R.id.pages_listItem);
                mGrabHandle = itemView.findViewById(mGrabHandleId);
                mImageView = (ImageView) itemView.findViewById(R.id.imageView_listItem);
                mPopUp=itemView.findViewById(R.id.popup_listView);
                if (!mIsMultiSelect)
                    checkBox.setVisibility(View.GONE);
                int width = Resources.getSystem().getDisplayMetrics().widthPixels;
                int height = Resources.getSystem().getDisplayMetrics().heightPixels;
                if (width > height)
                    width = height;
                mImageView.setLayoutParams(new LinearLayout.LayoutParams(width / 3, width / 3));
                mImageView.setBackground(ContextCompat.getDrawable(mContext, R.drawable.border));
                mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);


            }

            @Override
            public void onItemClicked(View view) {
                // Intent intent = new Intent(mContext, View_Entry.class);

                if (mIsMultiSelect) {
                    view.findViewById(R.id.multiSelectcheckBox).setVisibility(View.VISIBLE);
                    ((MainActivity) mContext).multi_select(getAdapterPosition());
                } else {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(data[getAdapterPosition()].getPath()));
                    intent.setDataAndType(Uri.parse(data[getAdapterPosition()].getPath()), "video/mp4");
                    startActivity(intent);

                    //((MainActivity) mContext).onChangeLayout();
                    //intent.putExtra("ID", getAdapterPosition());
                    //mContext.startActivity(intent);
                }

            }

            @Override
            public boolean onItemLongClicked(View view) {
                if (mContext.getClass() == MainActivity.class) {
                    if (!mIsMultiSelect) {
                        mIsMultiSelect = true;
                        ((MainActivity) mContext).SetMultiToolbar();
                    }
                    ((MainActivity) mContext).multi_select(getAdapterPosition());

                }
                return true;
            }

        }
    }

    private entry[] FetchImages() {

        entry[] file = new entry[dbHandler.rCount()];
        for (int i = 0; i < file.length; i++)
            file[i] = dbHandler.getresult(i);

        return file;
    }

    public String getNumberWithLeadingZero(int _number) {
        if (_number < 10) {
            return "0" + String.valueOf(_number);
        } else {
            return String.valueOf(_number);
        }
    }

    public String getTimeStringFromMs(long _ms) {
        int totalSeconds = (int) _ms / 1000;
        int seconds = totalSeconds % 60;
        int minutes = totalSeconds / 60;

        return getNumberWithLeadingZero(minutes) + ":" + getNumberWithLeadingZero(seconds);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_delete) {
            if (multiSelectList.toArray().length > 0) {
                AlertDialog.Builder statsOptInDialog = new AlertDialog.Builder(this);
                statsOptInDialog.setCancelable(false);
                statsOptInDialog.setTitle("Confirm");
                if (multiSelectList.toArray().length == 1)
                    statsOptInDialog.setMessage("Are you sure you want to delete " + multiSelectList.toArray().length + " item?");
                else
                    statsOptInDialog.setMessage("Are you sure you want to delete " + multiSelectList.toArray().length + " items?");
                statsOptInDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Integer pos[] = new Integer[multiSelectList.toArray().length];
                        multiSelectList.toArray(pos);
                        Arrays.sort(pos, new Comparator<Integer>() {
                            @Override
                            public int compare(Integer t1, Integer t2) {
                                return t2.compareTo(t1);
                            }
                        });
                        for (int i = 0; i < pos.length; i++) {
                            dbHandler.DeleteEntry(pos[i]);
                        }
                        Glide.get(MainActivity.this).clearMemory();
                        setupListRecyclerView();
                        resetToolbar();
                        dialog.dismiss();
                    }
                });
                statsOptInDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                statsOptInDialog.create().show();
            }

            return true;
        }
        if (id == R.id.action_share) {
            Integer pos[] = new Integer[multiSelectList.toArray().length];
            multiSelectList.toArray(pos);
            entry en[]=new entry[pos.length];
            for (int i = 0; i < pos.length; i++) {
                en[i]=dbHandler.getresult(pos[i]);
            }
            ShareVideos(MainActivity.this,en);

        }
        return super.onOptionsItemSelected(item);
    }
    private static void ShareVideos(Context context,entry [] e){
        ArrayList<Uri> uris=new ArrayList<>(e.length);
        for (entry en:e){
                File file = new File(en.getPath());
                Uri uri = FileProvider.getUriForFile(context, "com.csaproject.gehu.myapplication.fileprovider", file);
                uris.add(uri);
        }
        if(!uris.isEmpty()){
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND_MULTIPLE);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setType("video/mp4");
            context.startActivity(Intent.createChooser(intent, "Share Your Photo"));
        }

    }
    private void check_permissions(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            askPermission();

        } else {

                Intent intent = new Intent(MainActivity.this, RecordVideo.class);
                startActivity(intent);


            // write your logic here
        }
    }
    private   static final int PERMISSIONS_MULTIPLE_REQUEST = 123;
    private void askPermission(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED|| ContextCompat
                .checkSelfPermission(this,
                        Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED||ContextCompat
                .checkSelfPermission(this,
                        Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale
                    (this, Manifest.permission.READ_EXTERNAL_STORAGE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale
                            (this, Manifest.permission.CAMERA) ||
                    ActivityCompat.shouldShowRequestPermissionRationale
                            (this, Manifest.permission.RECORD_AUDIO)) {

                Snackbar.make(this.findViewById(android.R.id.content),
                        "Please Grant Permissions to store images",
                        Snackbar.LENGTH_INDEFINITE).setAction("ENABLE",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                    requestPermissions(
                                            new String[]{Manifest.permission
                                                    .READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},
                                            PERMISSIONS_MULTIPLE_REQUEST);
                            }
                        }).show();
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    requestPermissions(
                            new String[]{Manifest.permission
                                    .READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},
                            PERMISSIONS_MULTIPLE_REQUEST);
            }
        } else {

                Intent intent = new Intent(MainActivity.this, RecordVideo.class);
                startActivity(intent);

            // write your logic code if permission already granted
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode) {
            case PERMISSIONS_MULTIPLE_REQUEST:
                if (grantResults.length > 0) {
                    boolean cameraPermission = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                    boolean readExternalFile = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean audio=grantResults[2] == PackageManager.PERMISSION_GRANTED;
                    if(cameraPermission && readExternalFile&&audio)
                    {

                            Intent intent = new Intent(MainActivity.this, RecordVideo.class);
                            startActivity(intent);

                        // write your logic here
                    } else {
                        Snackbar.make(this.findViewById(android.R.id.content),
                                "Please Grant Permissions to enable Camera",
                                Snackbar.LENGTH_INDEFINITE).setAction("ENABLE",
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                            requestPermissions(
                                                    new String[]{Manifest.permission
                                                            .READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},
                                                    PERMISSIONS_MULTIPLE_REQUEST);
                                    }
                                }).show();
                    }
                }
                break;
        }
    }

}