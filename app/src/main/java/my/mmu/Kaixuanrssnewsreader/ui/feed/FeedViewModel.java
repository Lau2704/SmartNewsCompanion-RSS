package my.mmu.Kaixuanrssnewsreader.ui.feed;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import my.mmu.Kaixuanrssnewsreader.R;
import my.mmu.Kaixuanrssnewsreader.data.entry.EntryRepository;
import my.mmu.Kaixuanrssnewsreader.data.feed.Feed;
import my.mmu.Kaixuanrssnewsreader.data.feed.FeedRepository;
import my.mmu.Kaixuanrssnewsreader.data.history.HistoryRepository;
import my.mmu.Kaixuanrssnewsreader.service.rss.FeedSourceResolver;
import my.mmu.Kaixuanrssnewsreader.service.rss.RssFeed;
import my.mmu.Kaixuanrssnewsreader.service.rss.RssReader;
import my.mmu.Kaixuanrssnewsreader.service.tts.TtsExtractor;
import my.mmu.Kaixuanrssnewsreader.service.tts.TtsPlayer;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableObserver;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

@HiltViewModel
public class FeedViewModel extends ViewModel {

    private static final String TAG = "FeedViewModel";

    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    private FeedRepository feedRepository;
    private EntryRepository entryRepository;
    private HistoryRepository historyRepository;
    private TtsPlayer ttsPlayer;
    private final TtsExtractor ttsExtractor;
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private MutableLiveData<Integer> toastMessage = new MutableLiveData<>();
    private MutableLiveData<List<Feed>> allFeeds = new MutableLiveData<>();
    private RssFeed rssFeed;

    @Inject
    public FeedViewModel(FeedRepository feedRepository, EntryRepository entryRepository, HistoryRepository historyRepository, TtsPlayer ttsPlayer, TtsExtractor ttsExtractor) {
        this.feedRepository = feedRepository;
        this.entryRepository = entryRepository;
        this.historyRepository = historyRepository;
        this.ttsPlayer = ttsPlayer;
        this.ttsExtractor = ttsExtractor;

        Disposable disposable = feedRepository.getAllFeeds()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<Feed>>() {
                    @Override
                    public void accept(List<Feed> feeds) throws Throwable {
                        allFeeds.postValue(feeds);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Throwable {
                        Log.e(TAG, "Error loading feeds", throwable);
                    }
                });

        compositeDisposable.add(disposable);
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<Integer> getToastMessage() {
        return toastMessage;
    }

    public void resetToastMessage() {
        toastMessage.postValue(null);
    }

    public LiveData<List<Feed>> getAllFeeds() {
        return allFeeds;
    }

    public int getDelayTimeById(long id) {
        return feedRepository.getDelayTimeById(id);
    }

    public void updateDelayTimeById(long id, int delayTime) {
        feedRepository.updateDelayTimeById(id, delayTime);
    }

    public void checkNewFeed(String link, AddFeedCallback addFeedCallback) {
        Completable.fromAction(new Action() {
            @Override
            public void run() throws Throwable {
                if (!feedRepository.checkFeedExist(link)) {
                    FeedSourceResolver resolver = new FeedSourceResolver();
                    FeedSourceResolver.Result result = resolver.resolve(link);
                    rssFeed = result.getFeed();

                    String resolvedLink = rssFeed.getLink();
                    if (!resolvedLink.equals(link) && feedRepository.checkFeedExist(resolvedLink)) {
                        toastMessage.postValue(R.string.feed_already_added);
                        rssFeed = null;
                        return;
                    }

                    if (rssFeed.getFeedType() == null) {
                        rssFeed.setFeedType("RSS");
                    }
                    Log.d(TAG, "Resolved feed: type=" + rssFeed.getFeedType() + " url=" + resolvedLink);
                } else {
                    toastMessage.postValue(R.string.feed_already_added);
                }
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CompletableObserver() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        isLoading.postValue(true);
                    }

                    @Override
                    public void onComplete() {
                        if (rssFeed != null) {
                            addFeedCallback.openLoginDialog(rssFeed);
                            rssFeed = null;
                        }
                        isLoading.postValue(false);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        Log.e(TAG, "Feed resolution failed: " + e.getMessage());
                        toastMessage.postValue(R.string.feed_broken_inaccessible);
                        isLoading.postValue(false);
                    }
                });
    }

    public void addNewFeed(RssFeed feed) {
        Completable.fromAction(new Action() {
            @Override
            public void run() throws Throwable {
                feedRepository.addNewFeed(feed);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CompletableObserver() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        isLoading.postValue(true);
                    }

                    @Override
                    public void onComplete() {
                        toastMessage.postValue(R.string.feed_added_success);
                        isLoading.postValue(false);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        toastMessage.postValue(R.string.feed_broken);
                        isLoading.postValue(false);
                    }
                });
    }

    public void reExtractFeed(long feedId) {
        Completable.fromAction(new Action() {
            @Override
            public void run() throws Throwable {
                ttsPlayer.stop();
                entryRepository.reExtractContent(feedId);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CompletableObserver() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        isLoading.postValue(true);
                    }

                    @Override
                    public void onComplete() {
                        toastMessage.postValue(R.string.feed_content_removed);
                        ttsExtractor.extractAllEntries();
                        Log.d("FYP", "reExtractFeed completed. Now calling extractAllEntries()");
                        isLoading.postValue(false);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        toastMessage.postValue(R.string.feed_re_extract_failed);
                        isLoading.postValue(false);
                    }
                });
    }

    public void deleteFeed(Feed feed) {
        Completable.fromAction(new Action() {
            @Override
            public void run() throws Throwable {
                long currentId = ttsPlayer.getCurrentId();
                if (currentId != 0) {
                    List<Long> ids = entryRepository.getIdsByFeedId(feed.getId());
                    if (ids.contains(currentId)) {
                        ttsPlayer.stop();
                    }
                }
                feedRepository.delete(feed);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CompletableObserver() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        isLoading.postValue(true);
                    }

                    @Override
                    public void onComplete() {
                        toastMessage.postValue(R.string.feed_deleted_success);
                        isLoading.postValue(false);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        toastMessage.postValue(R.string.feed_delete_failed);
                        isLoading.postValue(false);
                    }
                });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        compositeDisposable.dispose();
    }

    public interface AddFeedCallback {
        void openLoginDialog(RssFeed feed);
    }
}
