package my.mmu.Kaixuanrssnewsreader.ui.main;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import my.mmu.Kaixuanrssnewsreader.R;
import my.mmu.Kaixuanrssnewsreader.data.feed.Feed;
import com.google.android.material.button.MaterialButton;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomViewTarget;
import com.bumptech.glide.request.transition.Transition;

public class NavigationFeedItemAdapter extends ListAdapter<Feed, NavigationFeedItemAdapter.FeedItemHolder> {

    private FeedItemClickInterface feedItemClickInterface;

    protected NavigationFeedItemAdapter(FeedItemClickInterface feedItemClickInterface) {
        super(DIFF_CALLBACK);
        this.feedItemClickInterface = feedItemClickInterface;
    }

    private static final DiffUtil.ItemCallback<Feed> DIFF_CALLBACK = new DiffUtil.ItemCallback<Feed>() {
        @Override
        public boolean areItemsTheSame(@NonNull Feed oldItem, @NonNull Feed newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Feed oldItem, @NonNull Feed newItem) {
            return oldItem.equals(newItem);
        }
    };

    @NonNull
    @Override
    public FeedItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.navigation_feed_item, parent, false);
        return new FeedItemHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull FeedItemHolder holder, int position) {
        Feed currentFeed = getItem(position);
        holder.bind(currentFeed);
    }

    class FeedItemHolder extends RecyclerView.ViewHolder {

        private MaterialButton navigationButton;

        public FeedItemHolder(@NonNull View itemView) {
            super(itemView);
            navigationButton = itemView.findViewById(R.id.navigationFeedButton);
        }

        public void bind(Feed feed) {
            navigationButton.setText(feed.getTitle());
            if (!TextUtils.isEmpty(feed.getImageUrl())) {
                Glide.with(navigationButton.getContext())
                        .asBitmap()
                        .load(feed.getImageUrl())
                        .disallowHardwareConfig()
                        .into(new CustomViewTarget<MaterialButton, Bitmap>(navigationButton) {
                            @Override
                            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                getView().setIcon(errorDrawable);
                            }

                            @Override
                            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                getView().setIcon(new BitmapDrawable(getView().getContext().getResources(), resource));
                            }

                            @Override
                            protected void onResourceCleared(@Nullable Drawable placeholder) {
                                getView().setIcon(placeholder);
                            }
                        });
            }
            navigationButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    feedItemClickInterface.onClick(feed.getId(), feed.getTitle());
                }
            });
        }
    }

    interface FeedItemClickInterface {
        void onClick(long id, String feedTitle);
    }
}
