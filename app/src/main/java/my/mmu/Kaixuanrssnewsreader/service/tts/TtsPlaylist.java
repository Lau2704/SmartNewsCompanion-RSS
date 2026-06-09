package my.mmu.Kaixuanrssnewsreader.service.tts;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;

import com.bumptech.glide.Glide;

import my.mmu.Kaixuanrssnewsreader.data.entry.EntryRepository;
import my.mmu.Kaixuanrssnewsreader.data.playlist.PlaylistRepository;
import my.mmu.Kaixuanrssnewsreader.model.EntryInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class TtsPlaylist {

    private final Context context;
    private final EntryRepository entryRepository;
    private final PlaylistRepository playlistRepository;
    private MediaMetadataCompat metadata;
    private EntryInfo entryInfo;
    private String content;
    private String html;
    private Bitmap feedImage;
    private long playingId;
    private String translated;

    @Inject
    public TtsPlaylist(@ApplicationContext Context context, EntryRepository entryRepository, PlaylistRepository playlistRepository) {
        this.context = context;
        this.entryRepository = entryRepository;
        this.playlistRepository = playlistRepository;
    }

    public List<MediaBrowserCompat.MediaItem> getMediaItems() {
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        if (metadata != null) {
            result.add(
                    new MediaBrowserCompat.MediaItem(
                            metadata.getDescription(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
        }
        return result;
    }

    public MediaMetadataCompat getCurrentMetadata() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (playingId != 0) {
                    entryInfo = entryRepository.getEntryInfoById(playingId);
                } else {
                    entryInfo = entryRepository.getLastVisitedEntry();
                }

                if (entryInfo != null) {
                    content = entryRepository.getContentById(entryInfo.getEntryId());
                    html = entryRepository.getHtmlById(entryInfo.getEntryId());
                    translated = entryRepository.getTranslatedTextById(entryInfo.getEntryId());
                    try {
                        feedImage = Glide.with(context)
                                .asBitmap()
                                .load(entryInfo.getFeedImageUrl())
                                .disallowHardwareConfig()
                                .submit()
                                .get(2, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (ExecutionException | InterruptedException | java.util.concurrent.TimeoutException e) {
                        e.printStackTrace();
                        feedImage = null;
                    }
                }
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (entryInfo == null) {
            metadata = new MediaMetadataCompat.Builder().build();
            return metadata;
        }

        metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, Long.toString(entryInfo.getEntryId()))
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, entryInfo.getFeedTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, entryInfo.getEntryTitle())
                .putString("link", entryInfo.getEntryLink())
                .putString("content", content)
                .putString("translated", translated)
                .putString("html", html)
                .putString("language", entryInfo.getFeedLanguage())
                .putLong("date", entryInfo.getEntryPublishedDate().getTime())
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, feedImage)
                .putString("feedImageUrl", entryInfo.getFeedImageUrl())
                .putString("entryImageUrl", entryInfo.getEntryImageUrl())
                .putString("bookmark", entryInfo.getBookmark())
                .putLong("feedId", entryInfo.getFeedId())
                .putString("ttsSpeechRate", Float.toString(entryInfo.getTtsSpeechRate()))
                .build();
        return metadata;
    }

    public boolean skipPrevious() {
        boolean success = playlistRepository.updatePlaylistToPrevious();
        if (success) {
            updatePlayingIdToLatest();
        }
        return success;
    }

    public boolean skipNext() {
        boolean success = playlistRepository.updatePlayListToNext();
        if (success) {
            updatePlayingIdToLatest();
        }
        return success;
    }

    public void updatePlayingIdToLatest() {
        this.playingId = entryRepository.getLastVisitedEntryId();
    }

    public void updatePlayingId(long id) {
        this.playingId = id;
    }

    public long getPlayingId() {
        return playingId;
    }
}