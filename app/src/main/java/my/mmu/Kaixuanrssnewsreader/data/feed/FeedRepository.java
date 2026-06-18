package my.mmu.Kaixuanrssnewsreader.data.feed;

import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import my.mmu.Kaixuanrssnewsreader.data.entry.Entry;
import my.mmu.Kaixuanrssnewsreader.data.entry.EntryRepository;
import my.mmu.Kaixuanrssnewsreader.data.history.History;
import my.mmu.Kaixuanrssnewsreader.data.history.HistoryRepository;
import my.mmu.Kaixuanrssnewsreader.model.FeedSourceType;
import my.mmu.Kaixuanrssnewsreader.service.rss.FeedSourceResolver;
import my.mmu.Kaixuanrssnewsreader.service.rss.RssFeed;
import my.mmu.Kaixuanrssnewsreader.service.rss.RssItem;
import my.mmu.Kaixuanrssnewsreader.service.rss.RssReader;
import my.mmu.Kaixuanrssnewsreader.service.rss.RssWorkManager;
import my.mmu.Kaixuanrssnewsreader.service.rss.WebPageScraper;
import my.mmu.Kaixuanrssnewsreader.data.sharedpreferences.SharedPreferencesRepository;
import my.mmu.Kaixuanrssnewsreader.service.tts.TtsExtractor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.inject.Provider;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.CompletableObserver;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class FeedRepository {

    private static final String TAG = "FeedRepository";
    private FeedDao feedDao;
    private EntryRepository entryRepository;
    private HistoryRepository historyRepository;
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private RssWorkManager rssWorkManager;
    private SharedPreferencesRepository preferencesRepository;
    private final Provider<TtsExtractor> ttsExtractorProvider;

    @Inject
    public FeedRepository(FeedDao feedDao, EntryRepository entryRepository, HistoryRepository historyRepository, RssWorkManager rssWorkManager, SharedPreferencesRepository sharedPreferencesRepository,  Provider<TtsExtractor> ttsExtractorProvider) {
        this.feedDao = feedDao;
        this.entryRepository = entryRepository;
        this.historyRepository = historyRepository;
        this.rssWorkManager = rssWorkManager;
        this.preferencesRepository = sharedPreferencesRepository;
        this.ttsExtractorProvider = ttsExtractorProvider;
    }

    public List<Feed> getAllStaticFeeds() {
        return feedDao.getAllStaticFeeds();
    }

    public Flowable<List<Feed>> getAllFeeds() {
        return feedDao.getAllFeeds();
    }

    public MutableLiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public void insert(Feed feed) {
        feedDao.insert(feed);
    }

    public long getFeedIdByLink(String link) {
        return feedDao.getIdByLink(link);
    }

    public void update(Feed feed) {
        feedDao.update(feed)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CompletableObserver() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        Log.d(TAG, "update onSubscribe: called");
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "update onComplete: called");
                        isLoading.postValue(false);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        Log.d(TAG, "update onError: " + e.getMessage());
                    }
                });
    }

    public void delete(Feed feed) {
        entryRepository.deleteByFeedId(feed.getId());
        feedDao.delete(feed)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CompletableObserver() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        Log.d(TAG, "delete onSubscribe: called");
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "delete onComplete: called");
                        if (getFeedCount() == 0 && rssWorkManager.isWorkScheduled()) {
                            rssWorkManager.dequeueRssWorker();
                        }
                        isLoading.postValue(false);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        Log.d(TAG, "delete onError: " + e.getMessage());
                    }
                });
        historyRepository.deleteByFeedId(feed.getId());
    }

    public int getFeedCount() {
        return feedDao.getFeedCount();
    }

    public void deleteAllFeeds() {
        feedDao.deleteAllFeeds()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CompletableObserver() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        Log.d(TAG, "deleteAllFeeds onSubscribe: called");
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "deleteAllFeeds onComplete: called");
                        isLoading.postValue(false);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        Log.d(TAG, "deleteAllFeeds onError: " + e.getMessage());
                    }
                });
    }

    public void addNewFeed(RssFeed feed) {
        String imageUrl;
        if (feed.getImageUrl() != null && !feed.getImageUrl().isEmpty()) {
            imageUrl = feed.getImageUrl();
        } else {
            imageUrl = "https://www.google.com/s2/favicons?sz=64&domain_url=" + feed.getLink();
        }
        Feed newFeed = new Feed(feed.getTitle(), feed.getLink(), feed.getDescription(), imageUrl, feed.getLanguage());
        if (feed.getFeedType() != null) {
            newFeed.setFeedType(feed.getFeedType());
        }

        feedDao.insert(newFeed);
        long feedId = feedDao.getIdByLink(feed.getLink());

        List<Entry> entriesToPreload = new ArrayList<>();
        for (RssItem rssItem : feed.getRssItems()) {
            if (!rssItem.isValid()) continue;
            Entry entry = new Entry(feedId, rssItem.getTitle(), rssItem.getLink(), rssItem.getDescription(), rssItem.getImageUrl(), rssItem.getCategory(), rssItem.getPubDate());

            long insertedId = entryRepository.insert(feedId, entry);
            if (insertedId > 0 && rssItem.getPriority() > 0) {
                entry.setPriority(rssItem.getPriority());
                entriesToPreload.add(entry);
            }
        }

        if (!entriesToPreload.isEmpty()) {
            entryRepository.preloadEntries(entriesToPreload);
        }
        markFeedAsPreloaded(feedId);

        entryRepository.requeueMissingEntries();
        if (entryRepository.hasEmptyContentEntries()) {
            ttsExtractorProvider.get().extractAllEntries();
        } else {
            Log.d(TAG, "No entries to extract.");
        }

        if (!rssWorkManager.isWorkScheduled()) {
            rssWorkManager.enqueueRssWorker();
        }
    }

    public EntryRepository getEntryRepository() {
        return entryRepository;
    }

    public SharedPreferencesRepository getSharedPreferencesRepository() {
        return preferencesRepository;
    }

    public void markFeedAsPreloaded(long feedId) {
        Feed feed = feedDao.getFeedById(feedId);
        if (feed != null && !feed.isPreloaded()) {
            feed.setPreloaded(true);
            feedDao.update(feed)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new CompletableObserver() {
                        @Override
                        public void onSubscribe(@NonNull Disposable d) {
                            Log.d(TAG, "markFeedAsPreloaded: Feed marked as preloaded" + feed.getTitle());
                        }

                        @Override
                        public void onComplete() {
                            Log.d(TAG, "markFeedAsPreloaded: Complete");
                        }

                        @Override
                        public void onError(@NonNull Throwable e) {
                            Log.e(TAG, "markFeedAsPreloaded: Error " + e.getMessage());
                        }
                    });
        } else {
            Log.w(TAG, "markFeedAsPreloaded: Feed not found for ID " + feedId);
        }
    }

    public String refreshEntries() {
        List<Feed> feeds = getAllStaticFeeds();
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        AtomicInteger counter = new AtomicInteger(0);

        for (Feed feed : feeds) {
            executorService.submit(() -> {
                try {
                    Log.d(TAG, "Refreshing feed: " + feed.getLink() + " type=" + feed.getFeedType());
                    RssFeed rssFeed;

                    if (FeedSourceType.WEB.equals(feed.getFeedType())) {
                        WebPageScraper scraper = new WebPageScraper();
                        rssFeed = scraper.scrape(feed.getLink());
                    } else {
                        RssReader rssReader = new RssReader(feed.getLink());
                        rssFeed = rssReader.getFeed();
                    }

                    insertEntriesForFeed(feed, rssFeed, counter);
                    Log.d(TAG, "Successfully refreshed feed: " + feed.getTitle());
                } catch (Exception e) {
                    Log.e(TAG, "Error refreshing feed: " + feed.getTitle(), e);
                }
            });
        }

        executorService.shutdown();
        try {
            executorService.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Log.e(TAG, "Error awaiting termination of executor service.", e);
        }

        entryRepository.requeueMissingEntries();
        return "New entries: " + counter.get();
    }

    private void insertEntriesForFeed(Feed feed, RssFeed rssFeed, AtomicInteger counter) {
        List<History> histories = new ArrayList<>();
        for (RssItem rssItem : rssFeed.getRssItems()) {
            if (!rssItem.isValid()) continue;
            Entry entry = new Entry(feed.getId(), rssItem.getTitle(), rssItem.getLink(), rssItem.getDescription(),
                    rssItem.getImageUrl(), rssItem.getCategory(), rssItem.getPubDate());
            long insertedId = entryRepository.insert(feed.getId(), entry);
            if (insertedId > 0) {
                counter.incrementAndGet();
                entryRepository.updatePriority(1, insertedId);
            } else {
                histories.add(new History(entry.getFeedId(), new Date(), entry.getTitle(), entry.getLink()));
            }
        }

        entryRepository.limitEntriesByFeedId(feed.getId());
        if (!histories.isEmpty()) {
            historyRepository.updateHistoriesByFeedId(feed.getId(), histories);
        }
    }


    public int getDelayTimeById(long id) {
        return feedDao.getDelayTimeById(id);
    }

    public void updateDelayTimeById(long id, int delayTime) {
        feedDao.updateDelayTimeById(id, delayTime);
    }

    public void updateTitleDescLanguage(String title, String desc, String language, String link) {
        feedDao.updateTitleDescLanguage(title, desc, language, link);
    }

    public float getTtsSpeechRateById(long id) {
        return feedDao.getTtsSpeechRateById(id);
    }

    public void updateTtsSpeechRateById(long id, float ttsSpeechRate) {
        feedDao.updateTtsSpeechRateById(id, ttsSpeechRate);
    }

    public boolean checkFeedExist(String link) {
        return feedDao.getIdByLink(link) != 0;
    }
}
