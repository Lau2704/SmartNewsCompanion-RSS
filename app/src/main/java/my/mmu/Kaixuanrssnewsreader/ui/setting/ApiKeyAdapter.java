package my.mmu.Kaixuanrssnewsreader.ui.setting;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import my.mmu.Kaixuanrssnewsreader.R;
import my.mmu.Kaixuanrssnewsreader.model.ApiKeyEntry;

import java.util.ArrayList;
import java.util.List;

public class ApiKeyAdapter extends RecyclerView.Adapter<ApiKeyAdapter.KeyViewHolder> {

    private final List<ApiKeyEntry> keys = new ArrayList<>();
    private String activeKeyId;
    private final OnKeyActionListener listener;

    public interface OnKeyActionListener {
        void onKeySelected(ApiKeyEntry entry);
        void onKeyDeleted(ApiKeyEntry entry);
    }

    public ApiKeyAdapter(@NonNull OnKeyActionListener listener) {
        this.listener = listener;
    }

    public void setKeys(@NonNull List<ApiKeyEntry> keys, @NonNull String activeKeyId) {
        this.keys.clear();
        this.keys.addAll(keys);
        this.activeKeyId = activeKeyId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public KeyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_api_key, parent, false);
        return new KeyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull KeyViewHolder holder, int position) {
        ApiKeyEntry entry = keys.get(position);
        boolean isActive = entry.getId().equals(activeKeyId);

        holder.keyLabel.setText(entry.getLabel());
        holder.keyValue.setText(entry.getMaskedKey());
        holder.activeIndicator.setVisibility(isActive ? View.VISIBLE : View.INVISIBLE);

        holder.contentArea.setOnClickListener(v -> {
            if (!entry.getId().equals(activeKeyId)) {
                listener.onKeySelected(entry);
            }
        });

        holder.deleteButton.setOnClickListener(v -> listener.onKeyDeleted(entry));
    }

    @Override
    public int getItemCount() {
        return keys.size();
    }

    static class KeyViewHolder extends RecyclerView.ViewHolder {
        final ImageView activeIndicator;
        final View contentArea;
        final TextView keyLabel;
        final TextView keyValue;
        final ImageView deleteButton;

        KeyViewHolder(@NonNull View itemView) {
            super(itemView);
            activeIndicator = itemView.findViewById(R.id.keyActiveIndicator);
            contentArea = itemView.findViewById(R.id.keyContentArea);
            keyLabel = itemView.findViewById(R.id.keyLabel);
            keyValue = itemView.findViewById(R.id.keyValue);
            deleteButton = itemView.findViewById(R.id.keyDeleteButton);
        }
    }
}
