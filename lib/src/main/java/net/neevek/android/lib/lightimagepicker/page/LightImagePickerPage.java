package net.neevek.android.lib.lightimagepicker.page;

import android.Manifest;
import android.animation.Animator;
import android.content.res.Configuration;
import android.os.Build;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import net.neevek.android.lib.lightimagepicker.LightImagePickerActivity;
import net.neevek.android.lib.lightimagepicker.P;
import net.neevek.android.lib.lightimagepicker.R;
import net.neevek.android.lib.lightimagepicker.model.LocalMediaResourceLoader;
import net.neevek.android.lib.lightimagepicker.model.OnImagesSelectedListener;
import net.neevek.android.lib.lightimagepicker.pojo.Bucket;
import net.neevek.android.lib.lightimagepicker.pojo.LocalMediaResource;
import net.neevek.android.lib.lightimagepicker.util.Async;
import net.neevek.android.lib.lightimagepicker.util.ToolbarHelper;
import net.neevek.android.lib.paginize.Page;
import net.neevek.android.lib.paginize.PageActivity;
import net.neevek.android.lib.paginize.annotation.InjectViewByName;
import net.neevek.android.lib.paginize.annotation.PageLayoutName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.neevek.android.lib.lightimagepicker.LightImagePickerActivity.PARAM_SELECTED_IMAGES;
import static net.neevek.android.lib.lightimagepicker.LightImagePickerActivity.PARAM_TITLE;

/**
 * Lilith Games
 * Created by JiaminXie on 12/01/2017.
 */

@PageLayoutName(P.layout.light_image_picker_page_album)
public class LightImagePickerPage extends Page implements ResourceBucketManager.OnBucketSelectedListener, View.OnClickListener {
    private final static int SHOW_BUCKET_LIST_ANIMATION_DURATION = 150;

    @InjectViewByName(P.id.light_image_picker_toolbar)
    private Toolbar mToolbar;
    @InjectViewByName(P.id.light_image_picker_rv_photo_list)
    private RecyclerView mRvPhotoList;
    @InjectViewByName(P.id.light_image_picker_rv_bucket_list)
    private RecyclerView mRvBucketList;
    @InjectViewByName(value = P.id.light_image_picker_view_bucket_list_bg, listenerTypes = View.OnClickListener.class)
    private View mViewBucketListBg;
    @InjectViewByName(value = P.id.light_image_picker_tv_select_bucket, listenerTypes = View.OnClickListener.class)
    private TextView mTvSelectBucket;
    @InjectViewByName(value = P.id.light_image_picker_tv_preview, listenerTypes = View.OnClickListener.class)
    private TextView mTvPreview;
    @InjectViewByName(value = P.id.light_image_picker_btn_send, listenerTypes = View.OnClickListener.class)
    private Button mBtnSend;

    private AlbumListAdapter mAdapter;
    private List<LocalMediaResource> mResourceList;
    private Set<LocalMediaResource> mSelectedItemSet = new LinkedHashSet<LocalMediaResource>();

    private Map<String, List<LocalMediaResource>> mCachedResourceList = new HashMap<String, List<LocalMediaResource>>();
    private ResourceBucketManager mResourceBucketManager;

    private OnImagesSelectedListener mOnImagesSelectedListener;
    private int mMaxAllowedSelection = 9;

    public static LightImagePickerPage create(PageActivity pageActivity, String title, ArrayList<String> selectedImages) {
        LightImagePickerPage lightImagePickerPage = new LightImagePickerPage(pageActivity);
        lightImagePickerPage.getBundle().putString(LightImagePickerActivity.PARAM_TITLE, TextUtils.isEmpty(title) ? lightImagePickerPage.getString(R.string.light_image_picker_album) : title);
        lightImagePickerPage.getBundle().putStringArrayList(LightImagePickerActivity.PARAM_SELECTED_IMAGES, selectedImages);
        return lightImagePickerPage;
    }

    public LightImagePickerPage(PageActivity pageActivity) {
        super(pageActivity);

        ToolbarHelper.setNavigationIconEnabled(mToolbar, true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            mRvPhotoList.setLayoutManager(new GridLayoutManager(getContext(), 3, LinearLayoutManager.VERTICAL, false));
        } else {
            mRvPhotoList.setLayoutManager(new GridLayoutManager(getContext(), 6, LinearLayoutManager.VERTICAL, false));
        }
        mAdapter = new AlbumListAdapter();
        mRvPhotoList.setAdapter(mAdapter);

        requestPermission();
        loadBuckets();
    }

    @Override
    public void onShow() {
        super.onShow();

//        if (mOnImagesSelectedListener == null) {
//            throw new IllegalStateException("OnImagesSelectedListener is required," +
//                    " call setOnImagesSelectedListener to set one before showing the page");
//        }

        mToolbar.setTitle(getBundle().getString(PARAM_TITLE));
        ArrayList<String> images = getBundle().getStringArrayList(PARAM_SELECTED_IMAGES);
        if (images != null) {
            for (int i = 0; i < images.size(); ++i) {
                mSelectedItemSet.add(new LocalMediaResource(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE, 0, images.get(i), 0, 0));
            }
        }
    }

    @Override
    public void onUncover(Object arg) {
        super.onUncover(arg);
        mAdapter.notifyDataSetChanged();
        updateButtonsState();
    }

    public LightImagePickerPage setMaxAllowedSelection(int maxAllowedSelection) {
        mMaxAllowedSelection = maxAllowedSelection;
        return this;
    }

    public LightImagePickerPage setOnImagesSelectedListener(OnImagesSelectedListener onImagesSelectedListener) {
        mOnImagesSelectedListener = onImagesSelectedListener;
        return this;
    }

    private void loadBuckets() {
        Async.run(new Runnable() {
            @Override
            public void run() {
                final List<Bucket> bucketList = LocalMediaResourceLoader.getImageBuckets(getContext());
                Async.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mResourceBucketManager = new ResourceBucketManager(getContext(), mRvBucketList, mTvSelectBucket, bucketList);
                        mResourceBucketManager.setOnBucketSelectedListener(LightImagePickerPage.this);

                        if (bucketList.size() > 0) {
                            loadPhotoListByBucketId(bucketList.get(0).bucketId);
                        }
                    }
                });
            }
        });
    }

    private void loadPhotoListByBucketId(final String bucketId) {
        Async.run(new Runnable() {
            @Override
            public void run() {
                List<LocalMediaResource> resourceList = mCachedResourceList.get(bucketId);
                if (resourceList == null) {
                    resourceList = LocalMediaResourceLoader.getImagesByBucketId(getContext(), bucketId);
                    if (resourceList != null) {
                        mCachedResourceList.put(bucketId, resourceList);
                    }
                }
                final List<LocalMediaResource> finalResourceList = resourceList;
                Async.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mResourceList = finalResourceList;
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= 16) {
            ActivityCompat.requestPermissions(getContext(),
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.light_image_picker_tv_select_bucket || id == R.id.light_image_picker_view_bucket_list_bg) {
            toggleBucketList();

        } else if (id == R.id.light_image_picker_tv_preview) {
            LightImagePickerPreviewPage.create(getContext(), mMaxAllowedSelection)
                    .setOnImagesSelectedListener(mOnImagesSelectedListener)
                    .setData(null, mSelectedItemSet)
                    .show(true);

        } else if (id == R.id.light_image_picker_btn_send) {
            finishSelectingImages();

        }
    }

    private void finishSelectingImages() {
        if (mOnImagesSelectedListener == null) {
            return;
        }

        ArrayList<String> selectedImages = new ArrayList<String>(mSelectedItemSet.size());
        for (LocalMediaResource res : mSelectedItemSet) {
            selectedImages.add(res.path);
        }
        mOnImagesSelectedListener.onImagesSelected(selectedImages);

        if (getContext() instanceof LightImagePickerActivity) {
            getContext().finish();
        } else {
            hide(true);
        }
    }

    private void toggleBucketList() {
        if (mRvBucketList.getVisibility() == View.GONE) {
            mViewBucketListBg.setVisibility(View.VISIBLE);
            mRvBucketList.setVisibility(View.VISIBLE);
            if (mRvBucketList.getHeight() == 0) {
                mRvBucketList.layout(0, 0, getView().getWidth(), getView().getHeight());
            }

            mViewBucketListBg.setAlpha(0);
            mViewBucketListBg.animate()
                    .alpha(0.6f)
                    .setDuration(SHOW_BUCKET_LIST_ANIMATION_DURATION)
                    .setInterpolator(new DecelerateInterpolator())
                    .setListener(null)  // required to clear the listener set in the 'else' part
                    .start();
            mRvBucketList.setTranslationY(mRvBucketList.getHeight());
            mRvBucketList.animate()
                    .translationY(0)
                    .setInterpolator(new DecelerateInterpolator())
                    .setListener(null)  // required to clear the listener set in the 'else' part
                    .setDuration(SHOW_BUCKET_LIST_ANIMATION_DURATION)
                    .start();
        } else {
            mViewBucketListBg.animate()
                    .alpha(0f)
                    .setDuration(SHOW_BUCKET_LIST_ANIMATION_DURATION)
                    .setInterpolator(new DecelerateInterpolator())
                    .setListener(new Animator.AnimatorListener() {
                        public void onAnimationEnd(Animator animation) {
                            mViewBucketListBg.setVisibility(View.GONE);
                        }
                        public void onAnimationStart(Animator animation) { }
                        public void onAnimationCancel(Animator animation) { }
                        public void onAnimationRepeat(Animator animation) { }
                    })
                    .start();
            mRvBucketList.setTranslationY(0);
            mRvBucketList.animate()
                    .translationY(mRvBucketList.getHeight())
                    .setDuration(SHOW_BUCKET_LIST_ANIMATION_DURATION)
                    .setInterpolator(new DecelerateInterpolator())
                    .setListener(new Animator.AnimatorListener() {
                        public void onAnimationEnd(Animator animation) {
                            mRvBucketList.setVisibility(View.GONE);
                        }
                        public void onAnimationStart(Animator animation) { }
                        public void onAnimationCancel(Animator animation) { }
                        public void onAnimationRepeat(Animator animation) { }
                    })
                    .start();
        }
    }

    private void updateButtonsState() {
        mTvPreview.setEnabled(mSelectedItemSet.size() > 0);
        mBtnSend.setEnabled(mSelectedItemSet.size() > 0);
        if (mSelectedItemSet.size() == 0) {
            mTvPreview.setText(R.string.light_image_picker_preview);
            mBtnSend.setText(R.string.light_image_picker_send);
        } else {
            mTvPreview.setText(getString(R.string.light_image_picker_preview_selected_items, mSelectedItemSet.size()));
            mBtnSend.setText(getString(R.string.light_image_picker_send_selected_items, mSelectedItemSet.size()));
        }
    }

    @Override
    public void onBucketSelected(Bucket selectedBucket) {
        loadPhotoListByBucketId(selectedBucket.bucketId);
        toggleBucketList();
    }

    @Override
    public boolean onBackPressed() {
        if (mRvBucketList.getVisibility() == View.VISIBLE) {
            toggleBucketList();
            return true;
        }

        boolean result = super.onBackPressed();
        if (!result) {
            result = true;
            getContext().finish();
        }
        if (mOnImagesSelectedListener != null) {
            mOnImagesSelectedListener.onCancelled();
        }
        return result;
    }

    private class AlbumListAdapter extends RecyclerView.Adapter<AlbumListAdapter.ViewHolder>
            implements CompoundButton.OnCheckedChangeListener, View.OnClickListener {
        private int sideLength = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100, getResources().getDisplayMetrics());

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ViewHolder holder = new ViewHolder(getContext().getLayoutInflater().inflate(R.layout.light_image_picker_photo_list_item, parent, false));
            holder.cbItemCheckbox.setOnCheckedChangeListener(this);
            holder.ivItemImage.setOnClickListener(this);
            return holder;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            LocalMediaResource resource = mResourceList.get(position);

//            boolean bitmapSet = false;
//            try {
//                BitmapFactory.Options options = new BitmapFactory.Options();
//                Bitmap bitmap = MediaStore.Images.Thumbnails.getThumbnail(
//                        getContext().getContentResolver(), resource.id,
//                        MediaStore.Images.Thumbnails.MINI_KIND,
//                        options);
//                if (bitmap != null) {
//                    holder.ivItemImage.setImageBitmap(bitmap);
//                    L.d("use thumbnail: %dx%d", bitmap.getWidth(), bitmap.getHeight());
//                    bitmapSet = true;
//                }
//            } catch (Exception e) {
//                if (BuildConfig.DEBUG) {
//                    e.printStackTrace();
//                }
//            }
//
//            if (!bitmapSet) {
                Glide.with(getContext())
                        .load(resource.path)
                        .override(sideLength, sideLength)
                        .centerCrop()
                        .crossFade()

                        .into(holder.ivItemImage);
//            }

            boolean selected = mSelectedItemSet.contains(resource);
            holder.viewItemMask.setVisibility(selected ? View.VISIBLE : View.GONE);
            holder.cbItemCheckbox.setChecked(selected);

            holder.cbItemCheckbox.setTag(position);
            holder.ivItemImage.setTag(position);
        }

        @Override
        public int getItemCount() {
            return mResourceList != null ? mResourceList.size() : 0;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (!buttonView.isPressed()) {
                return;
            }

            if (isChecked && mSelectedItemSet.size() >= mMaxAllowedSelection) {
                buttonView.setChecked(false);
                Toast.makeText(getContext(), getString(R.string.light_image_picker_select_item_count_limit, mMaxAllowedSelection), Toast.LENGTH_SHORT).show();
                return;
            }

            Integer position = (Integer) buttonView.getTag();
            if (position == null) {
                return;
            }

            LocalMediaResource resource = mResourceList.get(position);
            if (isChecked) {
                mSelectedItemSet.add(resource);
            } else {
                mSelectedItemSet.remove(resource);
            }
            mAdapter.notifyItemChanged(position);

            updateButtonsState();
        }

        @Override
        public void onClick(View v) {
            Integer position = (Integer) v.getTag();
            if (position == null) {
                return;
            }
            LightImagePickerPreviewPage.create(getContext(), mMaxAllowedSelection)
                    .setOnImagesSelectedListener(mOnImagesSelectedListener)
                    .setData(mResourceList, mSelectedItemSet)
                    .setStartItemIndex((Integer)v.getTag())
                    .show(true);
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private ImageView ivItemImage;
            private View viewItemMask;
            private CheckBox cbItemCheckbox;

            public ViewHolder(View itemView) {
                super(itemView);
                ivItemImage = (ImageView) itemView.findViewById(R.id.light_image_picker_iv_item_image);
                viewItemMask = itemView.findViewById(R.id.light_image_picker_view_photo_list_item_mask);
                cbItemCheckbox = (CheckBox) itemView.findViewById(R.id.light_image_picker_cb_photo_list_item_checkbox);
            }
        }
    }
}
