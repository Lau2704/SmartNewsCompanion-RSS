package my.mmu.Kaixuanrssnewsreader.ui.allentries;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import my.mmu.Kaixuanrssnewsreader.R;
import my.mmu.Kaixuanrssnewsreader.model.EntryInfo;

public class PlaylistItemAdapter extends ListAdapter<EntryInfo, PlaylistItemAdapter.PlaylistItemViewHolder> {

    private long playingId;
    private final OnPlaylistItemClickListener clickListener;

    public interface OnPlaylistItemClickListener {
        void onPlaylistItemClicked(long entryId);
    }

    public PlaylistItemAdapter(long playingId, OnPlaylistItemClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.playingId = playingId;
        this.clickListener = clickListener;
    }

    public void setPlayingId(long playingId) {
        this.playingId = playingId;
    }

    private static final DiffUtil.ItemCallback<EntryInfo> DIFF_CALLBACK = new DiffUtil.ItemCallback<EntryInfo>() {
        @Override
        public boolean areItemsTheSame(@NonNull EntryInfo oldItem, @NonNull EntryInfo newItem) {
            return oldItem.getEntryId() == newItem.getEntryId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull EntryInfo oldItem, @NonNull EntryInfo newItem) {
            return oldItem.equals(newItem);
        }
    };

    @NonNull
    @Override
    public PlaylistItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.playlist_queue_item, parent, false);
        return new PlaylistItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistItemViewHolder holder, int position) {
        EntryInfo entry = getItem(position);
        holder.entryTitle.setText(entry.getEntryTitle());
        holder.feedTitle.setText(entry.getFeedTitle());

        Glide.with(holder.itemView.getContext())
                .load(entry.getFeedImageUrl())
                .disallowHardwareConfig()
                .into(holder.feedIcon);

        boolean isPlaying = entry.getEntryId() == playingId;
        if (isPlaying) {
            holder.itemView.setBackgroundTintList(
                    ContextCompat.getColorStateList(holder.itemView.getContext(), R.color.primary_opacity16));
        } else {
            holder.itemView.setBackgroundTintList(null);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onPlaylistItemClicked(entry.getEntryId());
            }
        });
    }

    static class PlaylistItemViewHolder extends RecyclerView.ViewHolder {
        ImageView feedIcon;
        TextView entryTitle;
        TextView feedTitle;

        PlaylistItemViewHolder(@NonNull View itemView) {
            super(itemView);
            feedIcon = itemView.findViewById(R.id.feedIcon);
            entryTitle = itemView.findViewById(R.id.entryTitle);
            feedTitle = itemView.findViewById(R.id.feedTitle);
        }
    }
}
