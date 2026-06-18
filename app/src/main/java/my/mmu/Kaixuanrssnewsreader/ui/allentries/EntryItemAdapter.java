package my.mmu.Kaixuanrssnewsreader.ui.allentries;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import my.mmu.Kaixuanrssnewsreader.R;
import my.mmu.Kaixuanrssnewsreader.model.EntryInfo;

import com.google.android.material.button.MaterialButton;
import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.target.Target;
import android.graphics.drawable.Drawable;

public class EntryItemAdapter extends ListAdapter<EntryInfo, EntryItemAdapter.EntryItemHolder> {

    EntryItemClickInterface entryItemClickInterface;
    private Context context;
    private boolean isSelectionMode = false;
    private static final String TAG = "EntryItemAdapter";
    private final boolean autoTranslateEnabled;

    public EntryItemAdapter(EntryItemClickInterface entryItemClickInterface, boolean autoTranslateEnabled) {
        super(DIFF_CALLBACK);
        this.entryItemClickInterface = entryItemClickInterface;
        this.autoTranslateEnabled = autoTranslateEnabled;
    }

    private static final DiffUtil.ItemCallback<EntryInfo> DIFF_CALLBACK = new DiffUtil.ItemCallback<EntryInfo>() {
        @Override
        public boolean areItemsTheSame(@NonNull EntryInfo oldItem, @NonNull EntryInfo newItem) {
            return oldItem.getEntryId() == newItem.getEntryId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull EntryInfo oldE, @NonNull EntryInfo newE) {
            boolean oldTranslated = hasTranslation(oldE);
            boolean newTranslated = hasTranslation(newE);

            boolean oldExtracted = oldE.isHasContent();
            boolean newExtracted = newE.isHasContent();

            boolean sameBookmark = Objects.equals(oldE.getBookmark(), newE.getBookmark());
            boolean sameVisited  = Objects.equals(oldE.getVisitedDate(), newE.getVisitedDate());

            return sameBookmark && sameVisited &&
                    (oldTranslated == newTranslated) &&
                    (oldExtracted  == newExtracted);
        }

        private boolean hasTranslation(EntryInfo e) {
            return e.isHasHtml() && e.isHasOriginalHtml();
        }
    };

    @NonNull
    @Override
    public EntryItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.entry_item, parent, false);
        context = parent.getContext();
        return new EntryItemHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull EntryItemAdapter.EntryItemHolder holder, int position) {
        EntryInfo currentEntry = getItem(position);

        boolean isLoading = currentEntry.isLoading();
        Log.d(TAG, "onBindViewHolder: Position " + position + " - isLoading: " + isLoading);

        holder.bind(currentEntry);
    }

    public void exitSelectionMode() {
        isSelectionMode = false;
        entryItemClickInterface.onSelectionModeChanged(isSelectionMode);
        notifyDataSetChanged();
    }

    class EntryItemHolder extends RecyclerView.ViewHolder {

        private TextView textViewEntryTitle, textViewFeedTitle, textViewEntryPubDate;
        private ImageView imageViewEntryImage, imageViewFeedImage;
        private MaterialButton bookmarkButton;
        private MaterialButton moreButton;
        private CheckBox selectedCheckbox;
        private View view;

        public EntryItemHolder(@NonNull View itemView) {
            super(itemView);
            textViewEntryTitle = itemView.findViewById(R.id.entryTitle);
            textViewFeedTitle = itemView.findViewById(R.id.feedTitle);
            textViewEntryPubDate = itemView.findViewById(R.id.entryPubDate);
            imageViewFeedImage = itemView.findViewById(R.id.feedImage);
            imageViewEntryImage = itemView.findViewById(R.id.entryImage);
            bookmarkButton = itemView.findViewById(R.id.bookmark_button);
            moreButton = itemView.findViewById(R.id.more_button);
            selectedCheckbox = itemView.findViewById(R.id.selectedCheckbox);
            view = itemView;
        }

        public void bind(EntryInfo entryInfo) {
            TextView statusView = view.findViewById(R.id.extractionStatus);

            textViewEntryTitle.setTextColor(textViewEntryPubDate.getTextColors());
            textViewEntryTitle.setText(entryInfo.getEntryTitle());
            textViewFeedTitle.setText(entryInfo.getFeedTitle());

            String pubDate = covertTimeToText(entryInfo.getEntryPublishedDate());
            textViewEntryPubDate.setText(pubDate);

            if (entryInfo.getVisitedDate() != null) {
                textViewEntryTitle.setTextColor(Color.parseColor("#CD5C5C"));
            }

            if (entryInfo.getBookmark() == null || entryInfo.getBookmark().equals("N")) {
                bookmarkButton.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_bookmark_outline));
                bookmarkButton.setIconTint(ContextCompat.getColorStateList(context, R.color.text));
            } else {
                bookmarkButton.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_bookmark_filled));
                bookmarkButton.setIconTint(ContextCompat.getColorStateList(context, R.color.primary));
            }
            bookmarkButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (entryInfo.getBookmark() == null || entryInfo.getBookmark().equals("N")) {
                        entryItemClickInterface.onBookmarkButtonClick("Y", entryInfo.getEntryId());
                    } else {
                        entryItemClickInterface.onBookmarkButtonClick("N", entryInfo.getEntryId());
                    }
                }
            });

            moreButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    entryItemClickInterface.onMoreButtonClick(entryInfo.getEntryId(), entryInfo.getEntryLink(), entryInfo.getVisitedDate() == null);
                }
            });

            if (TextUtils.isEmpty(entryInfo.getEntryImageUrl())) {
                imageViewEntryImage.setVisibility(View.GONE);
            } else {
                imageViewEntryImage.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .asBitmap()
                        .load(entryInfo.getEntryImageUrl())
                        .disallowHardwareConfig()
                        .listener(new RequestListener<Bitmap>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                                imageViewEntryImage.setVisibility(View.GONE);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                                return false;
                            }
                        })
                        .into(imageViewEntryImage);
            }
            if (TextUtils.isEmpty(entryInfo.getFeedImageUrl())) {
                imageViewFeedImage.setVisibility(View.GONE);
            } else {
                imageViewFeedImage.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .asBitmap()
                        .load(entryInfo.getFeedImageUrl())
                        .disallowHardwareConfig()
                        .listener(new RequestListener<Bitmap>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                                imageViewFeedImage.setVisibility(View.GONE);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                                return false;
                            }
                        })
                        .into(imageViewFeedImage);
            }

            selectedCheckbox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            selectedCheckbox.setChecked(entryInfo.isSelected());
            selectedCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isSelectionMode) {
                        entryInfo.setSelected(isChecked);
                        entryItemClickInterface.onItemSelected(entryInfo);
                    }
                }
            });

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    entryItemClickInterface.onEntryClick(entryInfo);
                }
            });

            view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    isSelectionMode = true;
                    entryItemClickInterface.onSelectionModeChanged(true);
                    notifyDataSetChanged();
                    return true;
                }
            });

            boolean hasContent = entryInfo.isHasContent();
            int priority = entryInfo.getPriority();
            boolean hasOriginalHtml   = entryInfo.isHasOriginalHtml();
            boolean hasTranslatedHtml = entryInfo.isHasHtml()
                    && (!entryInfo.isHasOriginalHtml() ||
                    entryInfo.isHasTranslated());

            statusView.setText("");

            if (autoTranslateEnabled) {
                if (hasOriginalHtml && hasTranslatedHtml) {
                    statusView.setBackgroundResource(R.drawable.status_dot_green);
                    statusView.setVisibility(View.VISIBLE);
                } else if (hasOriginalHtml || hasContent || priority > 0) {
                    statusView.setBackgroundResource(R.drawable.status_dot_yellow);
                    statusView.setVisibility(View.VISIBLE);
                } else {
                    statusView.setBackgroundResource(R.drawable.status_dot_red);
                    statusView.setVisibility(View.VISIBLE);
                }
            } else {
                if (hasOriginalHtml) {
                    statusView.setBackgroundResource(R.drawable.status_dot_green);
                    statusView.setVisibility(View.VISIBLE);
                } else if (hasContent || priority > 0) {
                    statusView.setBackgroundResource(R.drawable.status_dot_yellow);
                    statusView.setVisibility(View.VISIBLE);
                } else {
                    statusView.setBackgroundResource(R.drawable.status_dot_red);
                    statusView.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    public interface EntryItemClickInterface {
        void onEntryClick(EntryInfo entryInfo);

        void onMoreButtonClick(long entryId, String link, boolean unread);

        void onBookmarkButtonClick(String bool, long id);

        void onSelectionModeChanged(boolean isSelectionMode);

        void onItemSelected(EntryInfo entryInfo);
    }

    public String covertTimeToText(Date date) {
        if (date == null) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
        return sdf.format(date);
    }
}
