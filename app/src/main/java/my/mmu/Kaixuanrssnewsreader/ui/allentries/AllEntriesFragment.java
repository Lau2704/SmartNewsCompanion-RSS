package my.mmu.Kaixuanrssnewsreader.ui.allentries;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import my.mmu.Kaixuanrssnewsreader.R;
import my.mmu.Kaixuanrssnewsreader.data.entry.Entry;
import my.mmu.Kaixuanrssnewsreader.data.entry.EntryRepository;
import my.mmu.Kaixuanrssnewsreader.data.sharedpreferences.SharedPreferencesRepository;
import my.mmu.Kaixuanrssnewsreader.service.tts.TtsPlayer;
import my.mmu.Kaixuanrssnewsreader.service.tts.TtsPlaylist;
import my.mmu.Kaixuanrssnewsreader.service.tts.TtsService;
import my.mmu.Kaixuanrssnewsreader.databinding.FragmentAllEntriesBinding;

import my.mmu.Kaixuanrssnewsreader.model.EntryInfo;
import my.mmu.Kaixuanrssnewsreader.service.util.AutoTranslator;
import my.mmu.Kaixuanrssnewsreader.service.util.TextUtil;
import my.mmu.Kaixuanrssnewsreader.ui.webview.MediaBrowserHelper;
import my.mmu.Kaixuanrssnewsreader.ui.webview.WebViewActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import my.mmu.Kaixuanrssnewsreader.ui.webview.WebViewViewModel;

@AndroidEntryPoint
public class AllEntriesFragment extends Fragment implements EntryItemAdapter.EntryItemClickInterface, FilterBottomSheet.FilterClickInterface, EntryItemDialog.EntryItemDialogClickInterface {

    private static final String TAG = AllEntriesFragment.class.getSimpleName();
    private FragmentAllEntriesBinding binding;
    private SwipeRefreshLayout swipeRefreshLayout;
    private AllEntriesViewModel allEntriesViewModel;
    private TextView unreadTextView;
    private EntryItemAdapter adapter;
    private List<EntryInfo> entries = new ArrayList<>();
    private String sortBy;
    private String filterBy = "all";
    private String title;
    private long feedId;
    private List<EntryInfo> selectedEntries = new ArrayList<>();
    private TextView selectedCountTextView;
    private ActionBar actionBar;
    private AutoTranslator autoTranslator;

    @Inject
    TtsPlaylist ttsPlaylist;
    @Inject
    TtsPlayer ttsPlayer;
    @Inject
    SharedPreferencesRepository sharedPreferencesRepository;
    @Inject
    EntryRepository entryRepository;
    @Inject
    TextUtil textUtil;

    private boolean isSelectionMode = false;
    private WebViewViewModel webViewViewModel;
    private CompositeDisposable compositeDisposable;

    private MediaBrowserHelper mediaBrowserHelper;
    private MaterialCardView playlistControlBar;
    private ImageButton controlBarPlayPause;
    private ImageButton controlBarSkipNext;
    private ImageButton controlBarSkipPrevious;
    private ImageButton controlBarPlaylistBtn;
    private TextView controlBarTitle;
    private TextView controlBarSubtitle;
    private CompositeDisposable controlBarDisposables = new CompositeDisposable();
    private int currentPlaybackState = PlaybackStateCompat.STATE_NONE;


    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentAllEntriesBinding.inflate(inflater, container, false);

        allEntriesViewModel = new ViewModelProvider(this).get(AllEntriesViewModel.class);
        unreadTextView = binding.unread;

        ConstraintLayout emptyContainer = binding.emptyContainer;
        RecyclerView entriesRecycler = binding.entriesRecycler;
        entriesRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        entriesRecycler.setHasFixedSize(true);
        boolean autoTranslate = sharedPreferencesRepository.getAutoTranslate();
        adapter = new EntryItemAdapter(this, autoTranslate);
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                entriesRecycler.scrollToPosition(0);

            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                entriesRecycler.scrollToPosition(0);

            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                entriesRecycler.scrollToPosition(0);

            }
        });
        entriesRecycler.setAdapter(adapter);

        sortBy = allEntriesViewModel.getSortBy();

        swipeRefreshLayout = binding.swipeRefreshLayout;

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                allEntriesViewModel.refreshEntries(swipeRefreshLayout);
            }
        });

        binding.deleteAllVisitedEntriesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                allEntriesViewModel.deleteAllVisitedEntries();
            }
        });

        allEntriesViewModel.getToastMessage().observe(getViewLifecycleOwner(), new Observer<Integer>() {
            @Override
            public void onChanged(Integer resId) {
                if (resId != null) {
                    Snackbar.make(requireView(), getString(resId), Snackbar.LENGTH_SHORT).show();
                    allEntriesViewModel.resetToastMessage();
                }
            }
        });

        allEntriesViewModel.getToastMessageString().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
                if (s != null && !s.isEmpty()) {
                    Snackbar.make(requireView(), s, Snackbar.LENGTH_SHORT).show();
                    allEntriesViewModel.resetToastMessageString();
                }
            }
        });

        allEntriesViewModel.getUnreadCount().observe(getViewLifecycleOwner(), new Observer<Integer>() {
            @Override
            public void onChanged(Integer integer) {
                String unread;
                if (integer != null) unread = getString(R.string.unread_count, integer);
                else unread = getString(R.string.no_unread);
                unreadTextView.setText(unread);
            }
        });

        allEntriesViewModel.getAllEntries().observe(getViewLifecycleOwner(), new Observer<List<EntryInfo>>() {
            @Override
            public void onChanged(List<EntryInfo> entryInfos) {
                entries = entryInfos;

                for (EntryInfo entry : entries) {
                    Log.d("ENTRY_CHECK", "Entry: " + entry.getEntryTitle() + ", FeedTitle: " + entry.getFeedTitle() + ", FeedID: " + entry.getFeedId());
                }

                if (entries.size() == 0) {
                    entriesRecycler.setVisibility(View.GONE);
                    emptyContainer.setVisibility(View.VISIBLE);
                } else {
                    emptyContainer.setVisibility(View.GONE);
                    entriesRecycler.setVisibility(View.VISIBLE);
                    if (sortBy.equals("oldest")) {
                        Collections.sort(entries, new EntryInfo.OldestComparator());
                    } else {
                        Collections.sort(entries, new EntryInfo.LatestComparator());
                    }
                }
                adapter.submitList(new ArrayList<>(entries));
            }
        });

        webViewViewModel = new ViewModelProvider(requireActivity()).get(WebViewViewModel.class);
        compositeDisposable = new CompositeDisposable();
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            String newTitle = getArguments().getString("title");
            feedId = getArguments().getLong("id");

            if (newTitle != null) {
                title = newTitle;
                binding.filterTitle.setText(title);
                allEntriesViewModel.getEntriesByFeed(feedId, filterBy);

                allEntriesViewModel.getAllEntries().observe(getViewLifecycleOwner(), entries -> {
                    this.entries = entries;
                    adapter.submitList(entries);

                    if (autoTranslator != null) {
                        autoTranslator.runAutoTranslation(() -> {
                            adapter.submitList(new ArrayList<>(entries));
                        });
                    } else {
                        Log.e("AutoTranslator", "autoTranslator is null when attempting to translate");
                    }
                });
            }
        } else {
            title = getString(R.string.all_feeds_title);
        }

        NavController navController = Navigation.findNavController(view);

        binding.goToAddFeedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavOptions navOptions = new NavOptions.Builder()
                        .setPopUpTo(R.id.feedFragment, false)
                        .build();
                navController.navigate(R.id.feedFragment, null, navOptions);
            }
        });

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                if (isSelectionMode) {
                    menu.clear();
                    enterSelectionMode();
                } else {
                    menuInflater.inflate(R.menu.top_app_bar_main, menu);

                    MenuItem menuItem = menu.findItem(R.id.search);
                    SearchView searchView = (SearchView) menuItem.getActionView();
                    searchView.setQueryHint(getString(R.string.search_hint));

                    searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                        @Override
                        public boolean onQueryTextSubmit(String query) {
                            return false;
                        }

                        @Override
                        public boolean onQueryTextChange(String newText) {
                            final String query = newText.toLowerCase(Locale.ROOT);
                            final List<EntryInfo> filteredEntries = new ArrayList<>();
                            for (EntryInfo entryInfo : entries) {
                                final String entryTitle = entryInfo.getEntryTitle().toLowerCase(Locale.ROOT);
                                if (entryTitle.contains(query)) filteredEntries.add(entryInfo);
                            }
                            adapter.submitList(filteredEntries);
                            return true;
                        }
                    });
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.filter) {
                    FilterBottomSheet filterBottomSheet = new FilterBottomSheet(AllEntriesFragment.this, sortBy, filterBy);
                    filterBottomSheet.show(requireActivity().getSupportFragmentManager(), "FilterBottomSheet");
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        webViewViewModel.getLoadingState().observe(getViewLifecycleOwner(), isLoading -> {
            Log.d(TAG, "Loading state observed in AllEntriesFragment: " + isLoading);
            if (entries != null) {
                for (EntryInfo entry : entries) {
                    entry.setLoading(isLoading);
                    Log.d(TAG, "Entry ID: " + entry.getEntryId() + " - isLoading set to: " + entry.isLoading());
                }
                adapter.notifyDataSetChanged();
            } else {
                Log.d(TAG, "Entries list is null in AllEntriesFragment.");
            }
        });

        setupPlaylistControlBar(view);
    }

    private void doWhenTranslationFinish(EntryInfo entryInfo, String translatedHtml, String targetLanguage) {
        webViewViewModel.resetEntry(entryInfo.getEntryId());

        // Handle html
        Document doc = Jsoup.parse(translatedHtml);
        doc.head().append(webViewViewModel.getStyle());
        Objects.requireNonNull(doc.selectFirst("body"))
                .prepend(webViewViewModel.getHtml(
                        entryInfo.getEntryTitle(),
                        entryInfo.getFeedTitle(),
                        entryInfo.getEntryPublishedDate(),
                        entryInfo.getFeedImageUrl()
                ));
        String finalHtml = doc.html();

        webViewViewModel.updateHtml(finalHtml, entryInfo.getEntryId());
        entryRepository.updateHtml(finalHtml, entryInfo.getEntryId());

        final String translatedContent = textUtil.extractHtmlContent(finalHtml, "--####--");

        webViewViewModel.updateTranslated(translatedContent, entryInfo.getEntryId());
        webViewViewModel.updateEntryTranslatedField(entryInfo.getEntryId(), translatedContent);
        entryRepository.updateTranslatedText(translatedContent, entryInfo.getEntryId());

        sharedPreferencesRepository.setIsTranslatedView(entryInfo.getEntryId(), true);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Entry updatedEntry = entryRepository.getEntryById(entryInfo.getEntryId());
            String contentToSpeak = (updatedEntry != null) ? updatedEntry.getTranslated() : null;

            if (contentToSpeak != null && !contentToSpeak.trim().isEmpty()) {
                Log.d("AllEntriesFragment", "Triggering TTS with translated content");

                boolean isInWebView = sharedPreferencesRepository.getCurrentReadingEntryId() == entryInfo.getEntryId();
                boolean isTranslatedView = sharedPreferencesRepository.getIsTranslatedView(entryInfo.getEntryId());

                if (isInWebView && isTranslatedView) {
                    ttsPlayer.extract(entryInfo.getEntryId(), entryInfo.getFeedId(), contentToSpeak, targetLanguage);
                } else {
                    Log.d("AllEntriesFragment", "TTS extract skipped (not current or not translated view)");
                }
            } else {
                Log.w("AllEntriesFragment", "Translated content is empty or missing");
            }
        }, 500);
    }

    private void translate(EntryInfo entryInfo) {
        String html = webViewViewModel.getHtmlById(entryInfo.getEntryId());
        if (html == null) return;
        Log.d(TAG, "translating title: " + entryInfo.getEntryTitle());
        // Identify source language
        String content = textUtil.extractHtmlContent(html, "--####--");
        String translationMethod = sharedPreferencesRepository.getTranslationMethod();
        String targetLanguage = sharedPreferencesRepository.getDefaultTranslationLanguage();

        Disposable disposable = textUtil.identifyLanguageRx(content).subscribe(languageCode -> {
            Disposable translateDisposable;
            Log.d(TAG, "translate: translation method: " + translationMethod);
            if (translationMethod.equals("lineByLine")) {
                translateDisposable = textUtil.translateHtmlLineByLine(languageCode, targetLanguage, html, entryInfo.getEntryTitle(), entryInfo.getEntryId(), progress -> {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), getString(R.string.translated_for, progress, entryInfo.getEntryTitle()), Toast.LENGTH_SHORT).show()
                    );
                }).subscribe(translatedHtml -> {
                    doWhenTranslationFinish(entryInfo, translatedHtml, targetLanguage);
                }, error -> {
                    Log.e(TAG, "Translation failed for lineByLine mode", error);
                });
            } else if (translationMethod.equals("paragraphByParagraph")) {
                translateDisposable = textUtil.translateHtmlByParagraph(languageCode, targetLanguage, html, entryInfo.getEntryTitle(), entryInfo.getEntryId(), progress -> {
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), getString(R.string.translated_for, progress, entryInfo.getEntryTitle()), Toast.LENGTH_SHORT).show()
                        );
            }).subscribe(translatedHtml -> {
                    doWhenTranslationFinish(entryInfo, translatedHtml, targetLanguage);
                }, error -> {
                    Log.e("AllEntriesFragment", "Translation failed for paragraph mode", error);
                });

            } else {
                translateDisposable = textUtil.translateHtmlAllAtOnce(languageCode, targetLanguage, html, entryInfo.getEntryTitle(), entryInfo.getEntryId(), progress -> {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), getString(R.string.translated_for, progress, entryInfo.getEntryTitle()), Toast.LENGTH_SHORT).show()
                    );
                }).subscribe(translatedHtml -> {
                    doWhenTranslationFinish(entryInfo, translatedHtml, targetLanguage);
                }, error -> {
                    Log.e(TAG, "Translation failed for allAtOnce mode", error);
                });
            }
            compositeDisposable.add(translateDisposable);
        }, throwable -> {
            System.err.println("Error identifying language: " + throwable.getMessage());
        });
        compositeDisposable.add(disposable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (controlBarDisposables != null) {
            controlBarDisposables.dispose();
        }
        playlistControlBar = null;
        controlBarPlayPause = null;
        controlBarSkipNext = null;
        controlBarSkipPrevious = null;
        controlBarPlaylistBtn = null;
        controlBarTitle = null;
        controlBarSubtitle = null;
        binding = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mediaBrowserHelper != null) {
            mediaBrowserHelper.onStart();
        }
        refreshControlBarState();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mediaBrowserHelper != null) {
            mediaBrowserHelper.onStop();
        }
    }

    private void setupPlaylistControlBar(@NonNull View view) {
        View controlBarInclude = view.findViewById(R.id.playlistControlBarInclude);
        if (controlBarInclude == null) return;

        playlistControlBar = (MaterialCardView) controlBarInclude;
        controlBarPlayPause = controlBarInclude.findViewById(R.id.controlBarPlayPause);
        controlBarPlayPause.setImageTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.white)));
        controlBarSkipNext = controlBarInclude.findViewById(R.id.controlBarSkipNext);
        controlBarSkipPrevious = controlBarInclude.findViewById(R.id.controlBarSkipPrevious);
        controlBarPlaylistBtn = controlBarInclude.findViewById(R.id.controlBarPlaylistBtn);
        controlBarTitle = controlBarInclude.findViewById(R.id.controlBarTitle);
        controlBarSubtitle = controlBarInclude.findViewById(R.id.controlBarSubtitle);

        mediaBrowserHelper = new MediaBrowserHelper(requireContext(), TtsService.class) {
            @Override
            protected void onChildrenLoaded(@NonNull String parentId, @NonNull List<android.support.v4.media.MediaBrowserCompat.MediaItem> children) {
                refreshControlBarState();
            }
        };

        mediaBrowserHelper.registerCallback(new android.support.v4.media.session.MediaControllerCompat.Callback() {
            @Override
            public void onPlaybackStateChanged(@Nullable android.support.v4.media.session.PlaybackStateCompat state) {
                if (state != null) {
                    currentPlaybackState = state.getState();
                    updatePlayPauseIcon(currentPlaybackState);
                }
            }

            @Override
            public void onMetadataChanged(@NonNull android.support.v4.media.MediaMetadataCompat metadata) {
                refreshControlBarState();
            }
        });

        controlBarPlayPause.setOnClickListener(v -> {
            if (mediaBrowserHelper == null) return;
            android.support.v4.media.session.MediaControllerCompat.TransportControls controls =
                    mediaBrowserHelper.getTransportControls();
            if (controls == null) return;

            if (currentPlaybackState == PlaybackStateCompat.STATE_PLAYING) {
                controls.pause();
            } else {
                controls.play();
            }
        });

        controlBarSkipNext.setOnClickListener(v -> {
            if (mediaBrowserHelper != null) {
                android.support.v4.media.session.MediaControllerCompat.TransportControls controls =
                        mediaBrowserHelper.getTransportControls();
                if (controls != null) controls.skipToNext();
            }
        });

        controlBarSkipPrevious.setOnClickListener(v -> {
            if (mediaBrowserHelper != null) {
                android.support.v4.media.session.MediaControllerCompat.TransportControls controls =
                        mediaBrowserHelper.getTransportControls();
                if (controls != null) controls.skipToPrevious();
            }
        });

        controlBarPlaylistBtn.setOnClickListener(v -> {
            long playingId = ttsPlayer.getCurrentId();
            PlaylistBottomSheet bottomSheet = PlaylistBottomSheet.newInstance(playingId);
            bottomSheet.show(getChildFragmentManager(), PlaylistBottomSheet.TAG);
        });

        if (ttsPlayer.getCurrentId() > 0) {
            refreshControlBarState();
        }
    }

    private void refreshControlBarState() {
        if (playlistControlBar == null) return;

        long currentId = ttsPlayer.getCurrentId();
        if (currentId <= 0 && currentPlaybackState == PlaybackStateCompat.STATE_NONE) {
            playlistControlBar.setVisibility(View.GONE);
            return;
        }

        playlistControlBar.setVisibility(View.VISIBLE);

        if (currentId > 0) {
            controlBarDisposables.add(
                    Single.fromCallable(() -> entryRepository.getEntryInfoById(currentId))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(entryInfo -> {
                                if (controlBarTitle != null && entryInfo != null) {
                                    controlBarTitle.setText(entryInfo.getEntryTitle());
                                    controlBarSubtitle.setText(entryInfo.getFeedTitle());
                                }
                            }, throwable -> Log.e(TAG, "Failed to load control bar info", throwable))
            );
        }

        updatePlayPauseIcon(ttsPlayer.isSpeaking()
                ? PlaybackStateCompat.STATE_PLAYING
                : PlaybackStateCompat.STATE_PAUSED);
    }

    private void updatePlayPauseIcon(int state) {
        if (controlBarPlayPause == null) return;
        if (state == PlaybackStateCompat.STATE_PLAYING) {
            controlBarPlayPause.setImageResource(R.drawable.ic_baseline_pause_24);
        } else {
            controlBarPlayPause.setImageResource(R.drawable.ic_baseline_play_arrow_24);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (swipeRefreshLayout.isRefreshing()) swipeRefreshLayout.setRefreshing(false);
        if (isSelectionMode) {
            exitSelectionMode();
        }
    }

    @Override
    public void onEntryClick(EntryInfo entryInfo) {
        onPlayingButtonClick(entryInfo.getEntryId());
    }

    @Override
    public void onMoreButtonClick(long entryId, String link, boolean unread) {
        long[] allLinks = new long[entries.size()];
        int index = 0;
        for (EntryInfo entryInfo : entries) {
            allLinks[index] = entryInfo.getEntryId();
            index++;
        }

        Bundle args = new Bundle();
        args.putLongArray("ids", allLinks);
        args.putLong("id", entryId);
        args.putString("link", link);
        args.putBoolean("unread", unread);
        EntryItemBottomSheet bottomSheet = new EntryItemBottomSheet();
        bottomSheet.setArguments(args);
        bottomSheet.show(getChildFragmentManager(), EntryItemBottomSheet.TAG);
    }

    @Override
    public void onBookmarkButtonClick(String bool, long id) {
        allEntriesViewModel.updateBookmark(bool, id);
    }

    @Override
    public void onFilterChange(String filter) {
        this.filterBy = filter;
        allEntriesViewModel.getEntriesByFeed(feedId, filter);
        String text = " (" + title + ")";

        switch (filter) {
            case "read":
                text = getString(R.string.filter_read_only) + text;
                binding.filterTitle.setText(text);
                break;
            case "unread":
                text = getString(R.string.filter_unread_only) + text;
                binding.filterTitle.setText(text);
                break;
            case "bookmark":
                text = getString(R.string.filter_bookmarks) + text;
                binding.filterTitle.setText(text);
                break;
            default:
                binding.filterTitle.setText(title);
        }
    }

    @Override
    public void onSortChange(String sort) {
        allEntriesViewModel.setSortBy(sort);
        sortBy = sort;
        List<EntryInfo> sortedEntries = new ArrayList<>(entries);
        if (sortBy.equals("oldest")) {
            Collections.sort(sortedEntries, new EntryInfo.OldestComparator());
        } else {
            Collections.sort(sortedEntries, new EntryInfo.LatestComparator());
        }
        adapter.submitList(sortedEntries);
        entries = sortedEntries;
    }

    @Override
    public void onPlayingButtonClick(long entryId) {
        Context context = getContext();
        Intent intent = new Intent(context, WebViewActivity.class);
        intent.putExtra("read", false);
        intent.putExtra("entry_id", entryId);

        List<Long> allLinks = new ArrayList<>();
        for (EntryInfo entryInfo : entries) {
            allLinks.add(entryInfo.getEntryId());
        }
        allEntriesViewModel.insertPlaylist(allLinks, entryId);
        allEntriesViewModel.updateVisitedDate(entryId);

//        Bundle b = ActivityOptions.makeSceneTransitionAnimation(getActivity()).toBundle();
        if (context != null) {
            context.startActivity(intent);
        }
    }

    @Override
    public void onReadingButtonClick(long entryId) {
        Context context = getContext();
        Intent intent = new Intent(context, WebViewActivity.class);
        intent.putExtra("read", true);
        intent.putExtra("entry_id", entryId);

        List<Long> allLinks = new ArrayList<>();
        for (EntryInfo entryInfo : entries) {
            allLinks.add(entryInfo.getEntryId());
        }
        allEntriesViewModel.insertPlaylist(allLinks, entryId);
        allEntriesViewModel.updateVisitedDate(entryId);

//        Bundle b = ActivityOptions.makeSceneTransitionAnimation(getActivity()).toBundle();
        if (context != null) {
            context.startActivity(intent);
        }
    }

    @Override
    public void onSelectionModeChanged(boolean isSelectionMode) {
        this.isSelectionMode = isSelectionMode;
        requireActivity().invalidateOptionsMenu();
    }

    @Override
    public void onItemSelected(EntryInfo entryInfo) {
        if (entryInfo.isSelected()) {
            selectedEntries.add(entryInfo);
        } else {
            selectedEntries.remove(entryInfo);
        }
        selectedCountTextView.setText(getString(R.string.selected_count, selectedEntries.size()));
    }

    public void enterSelectionMode() {
        isSelectionMode = true;

        // Inflate custom view for action bar
        actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setCustomView(R.layout.actionbar_multipleselection);

            // Find the TextView in the custom view and update it
            selectedCountTextView = actionBar.getCustomView().findViewById(R.id.selected_count);
            selectedCountTextView.setText(getString(R.string.selected_count, selectedEntries.size()));
            actionBar.getCustomView().findViewById(R.id.menu_delete).setOnClickListener(v -> {
                for (EntryInfo item : selectedEntries) {
                    allEntriesViewModel.deleteEntry(item.getEntryId());
                }
                exitSelectionMode();
            });
            actionBar.getCustomView().findViewById(R.id.menu_mark_as_read).setOnClickListener(v -> {
                for (EntryInfo item : selectedEntries) {
                    allEntriesViewModel.updateVisitedDate(item.getEntryId());
                }
                exitSelectionMode();
            });
            actionBar.getCustomView().findViewById(R.id.menu_translate).setOnClickListener(v -> {
                for (EntryInfo item : selectedEntries) {
                    translate(item);
                }
                Toast.makeText(requireContext(), getString(R.string.translating_entries, selectedEntries.size()), Toast.LENGTH_SHORT).show();
                exitSelectionMode();
            });
            actionBar.getCustomView().findViewById(R.id.menu_cancel).setOnClickListener(v -> {
                exitSelectionMode();
            });
        }
    }

    public void exitSelectionMode() {
        isSelectionMode = false;
        for (EntryInfo entryInfo : selectedEntries) {
            entryInfo.setSelected(false);
        }
        selectedEntries.clear();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setCustomView(null);
        requireActivity().invalidateOptionsMenu();
        adapter.exitSelectionMode();
    }
}