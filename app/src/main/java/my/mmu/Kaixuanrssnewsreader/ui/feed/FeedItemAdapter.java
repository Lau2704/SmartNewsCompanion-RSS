package my.mmu.Kaixuanrssnewsreader.ui.feed;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import my.mmu.Kaixuanrssnewsreader.R;
import my.mmu.Kaixuanrssnewsreader.data.feed.Feed;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.target.Target;
import android.graphics.Bitmap;
import androidx.annotation.Nullable;

public class FeedItemAdapter extends ListAdapter<Feed, FeedItemAdapter.FeedItemHolder> {

    FeedItemClickInterface feedItemClickInterface;

    protected FeedItemAdapter(FeedItemClickInterface feedItemClickInterface) {
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
                .inflate(R.layout.feed_item, parent, false);
        return new FeedItemHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull FeedItemHolder holder, int position) {
        Feed currentFeed = getItem(position);
        holder.bind(currentFeed);
    }

    class FeedItemHolder extends RecyclerView.ViewHolder {

        private TextView textViewFeedTitle;
        private ImageView imageViewFeedImage;
        private Button reExtractButton;
        private Button updateButton;
        private Button deleteButton;

        public FeedItemHolder(@NonNull View itemView) {
            super(itemView);
            textViewFeedTitle = itemView.findViewById(R.id.feedTitle);
            imageViewFeedImage = itemView.findViewById(R.id.feedImage);
            reExtractButton = itemView.findViewById(R.id.reExtractFeed);
            updateButton = itemView.findViewById(R.id.updateFeed);
            deleteButton = itemView.findViewById(R.id.deleteFeed);
        }

        public void bind(Feed feed) {
            textViewFeedTitle.setText(feed.getTitle());
            if (TextUtils.isEmpty(feed.getImageUrl())) {
                imageViewFeedImage.setVisibility(View.GONE);
            } else {
                imageViewFeedImage.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .asBitmap()
                        .load(feed.getImageUrl())
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
            reExtractButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    feedItemClickInterface.onReExtract(feed.getId());
                }
            });
            updateButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    feedItemClickInterface.onUpdate(feed);
                }
            });
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    feedItemClickInterface.onDelete(feed);
                }
            });
        }
    }

    interface FeedItemClickInterface {
        void onReExtract(long feedId);
        void onUpdate(Feed feed);
        void onDelete(Feed feed);
    }
}
