package my.mmu.Kaixuanrssnewsreader.ui.allentries;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import my.mmu.Kaixuanrssnewsreader.R;
import my.mmu.Kaixuanrssnewsreader.data.entry.EntryRepository;
import my.mmu.Kaixuanrssnewsreader.data.playlist.PlaylistRepository;
import my.mmu.Kaixuanrssnewsreader.model.EntryInfo;
import my.mmu.Kaixuanrssnewsreader.service.tts.TtsPlaylist;

@AndroidEntryPoint
public class PlaylistBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = PlaylistBottomSheet.class.getSimpleName();
    private static final String ARG_PLAYING_ID = "playing_id";

    @Inject
    PlaylistRepository playlistRepository;
    @Inject
    EntryRepository entryRepository;
    @Inject
    TtsPlaylist ttsPlaylist;

    private PlaylistItemAdapter adapter;
    private CompositeDisposable disposables = new CompositeDisposable();

    public static PlaylistBottomSheet newInstance(long playingId) {
        PlaylistBottomSheet sheet = new PlaylistBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_PLAYING_ID, playingId);
        sheet.setArguments(args);
        return sheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_bottom_playlist, null);

        long playingId = 0;
        if (getArguments() != null) {
            playingId = getArguments().getLong(ARG_PLAYING_ID, 0);
        }

        RecyclerView recyclerView = view.findViewById(R.id.playlistRecyclerView);
        TextView emptyState = view.findViewById(R.id.playlistEmptyState);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new PlaylistItemAdapter(playingId, entryId -> {
            ttsPlaylist.updatePlayingId(entryId);
            dismiss();
        });
        recyclerView.setAdapter(adapter);

        disposables.add(Single.fromCallable(() -> {
                    String playlistStr = playlistRepository.getLatestPlaylist();
                    if (playlistStr == null || playlistStr.isEmpty()) {
                        return new ArrayList<EntryInfo>();
                    }
                    List<Long> ids = playlistRepository.stringToLongList(playlistStr);
                    List<EntryInfo> items = new ArrayList<>();
                    for (Long id : ids) {
                        EntryInfo info = entryRepository.getEntryInfoById(id);
                        if (info != null) {
                            items.add(info);
                        }
                    }
                    return items;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    if (items.isEmpty()) {
                        emptyState.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        emptyState.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        adapter.submitList(items);
                    }
                }, throwable -> {
                    emptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                }));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setView(view);
        return builder.create();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.dispose();
    }
}
