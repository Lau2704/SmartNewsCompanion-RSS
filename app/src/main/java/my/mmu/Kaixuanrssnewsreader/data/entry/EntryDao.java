package my.mmu.Kaixuanrssnewsreader.data.entry;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import my.mmu.Kaixuanrssnewsreader.model.EntryInfo;

import java.util.Date;
import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;

@Dao
public interface EntryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(Entry entry);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertEntries(List<Entry> entries);

    @Update
    Completable update(Entry entry);

    @Delete
    Completable delete(Entry entry);

    @Query("DELETE FROM entry_table WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM entry_table WHERE id IN (:ids) AND bookmark is not 'Y'")
    void deleteByIds(List<Long> ids);

    @Query("DELETE FROM entry_table WHERE feedId = :feedId")
    Completable deleteByFeedId(long feedId);

    @Query("SELECT e.id as entryId, e.title as entryTitle, e.priority as priority, e.link as entryLink, e.description as entryDescription, e.imageUrl as entryImageUrl, e.publishedDate as entryPublishedDate, e.visitedDate as visitedDate, e.category as entryCategory, e.bookmark as bookmark, CASE WHEN e.content IS NOT NULL AND e.content != '' THEN 1 ELSE 0 END as hasContent, CASE WHEN e.original_html IS NOT NULL AND e.original_html != '' THEN 1 ELSE 0 END as hasOriginalHtml, CASE WHEN e.html IS NOT NULL AND e.html != '' THEN 1 ELSE 0 END as hasHtml, CASE WHEN e.translated IS NOT NULL AND e.translated != '' THEN 1 ELSE 0 END as hasTranslated, f.id as feedId, f.ttsSpeechRate as ttsSpeechRate, f.language as feedLanguage, f.title as feedTitle, f.imageUrl as feedImageUrl " +
            "FROM entry_table e " +
            "LEFT JOIN feed_table f ON e.feedId = f.id")
    Flowable<List<EntryInfo>> getAllEntriesInfo();

    @Query("SELECT e.id as entryId, e.title as entryTitle, e.priority as priority, e.link as entryLink, e.description as entryDescription, e.imageUrl as entryImageUrl, e.publishedDate as entryPublishedDate, e.visitedDate as visitedDate, e.category as entryCategory, e.bookmark as bookmark, CASE WHEN e.content IS NOT NULL AND e.content != '' THEN 1 ELSE 0 END as hasContent, CASE WHEN e.original_html IS NOT NULL AND e.original_html != '' THEN 1 ELSE 0 END as hasOriginalHtml, CASE WHEN e.html IS NOT NULL AND e.html != '' THEN 1 ELSE 0 END as hasHtml, CASE WHEN e.translated IS NOT NULL AND e.translated != '' THEN 1 ELSE 0 END as hasTranslated, f.id as feedId, f.ttsSpeechRate as ttsSpeechRate, f.language as feedLanguage, f.title as feedTitle, f.imageUrl as feedImageUrl " +
            "FROM entry_table e " +
            "LEFT JOIN feed_table f ON e.feedId = f.id " +
            "WHERE bookmark = 'Y'")
    Flowable<List<EntryInfo>> getEntriesByBookmark();

    @Query("SELECT e.id as entryId, e.title as entryTitle, e.priority as priority, e.link as entryLink, e.description as entryDescription, e.imageUrl as entryImageUrl, e.publishedDate as entryPublishedDate, e.visitedDate as visitedDate, e.category as entryCategory, e.bookmark as bookmark, CASE WHEN e.content IS NOT NULL AND e.content != '' THEN 1 ELSE 0 END as hasContent, CASE WHEN e.original_html IS NOT NULL AND e.original_html != '' THEN 1 ELSE 0 END as hasOriginalHtml, CASE WHEN e.html IS NOT NULL AND e.html != '' THEN 1 ELSE 0 END as hasHtml, CASE WHEN e.translated IS NOT NULL AND e.translated != '' THEN 1 ELSE 0 END as hasTranslated, f.id as feedId, f.ttsSpeechRate as ttsSpeechRate, f.language as feedLanguage, f.title as feedTitle, f.imageUrl as feedImageUrl " +
            "FROM entry_table e " +
            "LEFT JOIN feed_table f ON e.feedId = f.id " +
            "WHERE visitedDate is null")
    Flowable<List<EntryInfo>> getEntriesByUnread();

    @Query("SELECT e.id as entryId, e.title as entryTitle, e.priority as priority, e.link as entryLink, e.description as entryDescription, e.imageUrl as entryImageUrl, e.publishedDate as entryPublishedDate, e.visitedDate as visitedDate, e.category as entryCategory, e.bookmark as bookmark, CASE WHEN e.content IS NOT NULL AND e.content != '' THEN 1 ELSE 0 END as hasContent, CASE WHEN e.original_html IS NOT NULL AND e.original_html != '' THEN 1 ELSE 0 END as hasOriginalHtml, CASE WHEN e.html IS NOT NULL AND e.html != '' THEN 1 ELSE 0 END as hasHtml, CASE WHEN e.translated IS NOT NULL AND e.translated != '' THEN 1 ELSE 0 END as hasTranslated, f.id as feedId, f.ttsSpeechRate as ttsSpeechRate, f.language as feedLanguage, f.title as feedTitle, f.imageUrl as feedImageUrl " +
            "FROM entry_table e " +
            "LEFT JOIN feed_table f ON e.feedId = f.id " +
            "WHERE visitedDate is not null")
    Flowable<List<EntryInfo>> getEntriesByRead();

    @Query("SELECT e.id as entryId, e.title as entryTitle, e.priority as priority, e.link as entryLink, e.description as entryDescription, e.imageUrl as entryImageUrl, e.publishedDate as entryPublishedDate, e.visitedDate as visitedDate, e.category as entryCategory, e.bookmark as bookmark, CASE WHEN e.content IS NOT NULL AND e.content != '' THEN 1 ELSE 0 END as hasContent, CASE WHEN e.original_html IS NOT NULL AND e.original_html != '' THEN 1 ELSE 0 END as hasOriginalHtml, CASE WHEN e.html IS NOT NULL AND e.html != '' THEN 1 ELSE 0 END as hasHtml, CASE WHEN e.translated IS NOT NULL AND e.translated != '' THEN 1 ELSE 0 END as hasTranslated, f.id as feedId, f.ttsSpeechRate as ttsSpeechRate, f.language as feedLanguage, f.title as feedTitle, f.imageUrl as feedImageUrl " +
            "FROM entry_table e " +
            "LEFT JOIN feed_table f ON e.feedId = f.id " +
            "WHERE bookmark = 'Y' AND e.feedId = :id")
    Flowable<List<EntryInfo>> getEntriesByBookmark(long id);

    @Query("SELECT e.id as entryId, e.title as entryTitle, e.priority as priority, e.link as entryLink, e.description as entryDescription, e.imageUrl as entryImageUrl, e.publishedDate as entryPublishedDate, e.visitedDate as visitedDate, e.category as entryCategory, e.bookmark as bookmark, CASE WHEN e.content IS NOT NULL AND e.content != '' THEN 1 ELSE 0 END as hasContent, CASE WHEN e.original_html IS NOT NULL AND e.original_html != '' THEN 1 ELSE 0 END as hasOriginalHtml, CASE WHEN e.html IS NOT NULL AND e.html != '' THEN 1 ELSE 0 END as hasHtml, CASE WHEN e.translated IS NOT NULL AND e.translated != '' THEN 1 ELSE 0 END as hasTranslated, f.id as feedId, f.ttsSpeechRate as ttsSpeechRate, f.language as feedLanguage, f.title as feedTitle, f.imageUrl as feedImageUrl " +
            "FROM entry_table e " +
            "LEFT JOIN feed_table f ON e.feedId = f.id " +
            "WHERE visitedDate is null AND e.feedId = :id")
    Flowable<List<EntryInfo>> getEntriesByUnread(long id);

    @Query("SELECT e.id as entryId, e.title as entryTitle, e.priority as priority, e.link as entryLink, e.description as entryDescription, e.imageUrl as entryImageUrl, e.publishedDate as entryPublishedDate, e.visitedDate as visitedDate, e.category as entryCategory, e.bookmark as bookmark, CASE WHEN e.content IS NOT NULL AND e.content != '' THEN 1 ELSE 0 END as hasContent, CASE WHEN e.original_html IS NOT NULL AND e.original_html != '' THEN 1 ELSE 0 END as hasOriginalHtml, CASE WHEN e.html IS NOT NULL AND e.html != '' THEN 1 ELSE 0 END as hasHtml, CASE WHEN e.translated IS NOT NULL AND e.translated != '' THEN 1 ELSE 0 END as hasTranslated, f.id as feedId, f.ttsSpeechRate as ttsSpeechRate, f.language as feedLanguage, f.title as feedTitle, f.imageUrl as feedImageUrl " +
            "FROM entry_table e " +
            "LEFT JOIN feed_table f ON e.feedId = f.id " +
            "WHERE visitedDate is not null AND e.feedId = :id")
    Flowable<List<EntryInfo>> getEntriesByRead(long id);

    @Query("SELECT e.id as entryId, e.title as entryTitle, e.priority as priority, e.link as entryLink, e.description as entryDescription, e.imageUrl as entryImageUrl, e.publishedDate as entryPublishedDate, e.visitedDate as visitedDate, e.category as entryCategory, e.bookmark as bookmark, CASE WHEN e.content IS NOT NULL AND e.content != '' THEN 1 ELSE 0 END as hasContent, CASE WHEN e.original_html IS NOT NULL AND e.original_html != '' THEN 1 ELSE 0 END as hasOriginalHtml, CASE WHEN e.html IS NOT NULL AND e.html != '' THEN 1 ELSE 0 END as hasHtml, CASE WHEN e.translated IS NOT NULL AND e.translated != '' THEN 1 ELSE 0 END as hasTranslated, f.id as feedId, f.ttsSpeechRate as ttsSpeechRate, f.language as feedLanguage, f.title as feedTitle, f.imageUrl as feedImageUrl " +
            "FROM entry_table e " +
            "LEFT JOIN feed_table f ON e.feedId = f.id " +
            "WHERE e.feedId = :id")
    Flowable<List<EntryInfo>> getEntriesByFeed(long id);

    @Query("SELECT e.id as entryId, e.title as entryTitle, e.priority as priority, e.link as entryLink, e.description as entryDescription, e.imageUrl as entryImageUrl, e.publishedDate as entryPublishedDate, e.visitedDate as visitedDate, e.category as entryCategory, e.bookmark as bookmark, CASE WHEN e.content IS NOT NULL AND e.content != '' THEN 1 ELSE 0 END as hasContent, CASE WHEN e.original_html IS NOT NULL AND e.original_html != '' THEN 1 ELSE 0 END as hasOriginalHtml, CASE WHEN e.html IS NOT NULL AND e.html != '' THEN 1 ELSE 0 END as hasHtml, CASE WHEN e.translated IS NOT NULL AND e.translated != '' THEN 1 ELSE 0 END as hasTranslated, f.id as feedId, f.ttsSpeechRate as ttsSpeechRate, f.language as feedLanguage, f.title as feedTitle, f.imageUrl as feedImageUrl " +
            "FROM entry_table e " +
            "LEFT JOIN feed_table f ON e.feedId = f.id " +
            "ORDER BY e.publishedDate DESC")
    LiveData<List<EntryInfo>> getAllEntriesInfoLive();

    @Query("SELECT e.* " +
            "FROM entry_table e " +
            "LEFT JOIN feed_table f ON e.feedId = f.id " +
            "WHERE f.id = :id")
    List<Entry> getStaticEntriesByFeed(long id);

    @Query("SELECT e.id as entryId, e.title as entryTitle, e.priority as priority, e.link as entryLink, e.description as entryDescription, e.imageUrl as entryImageUrl, e.publishedDate as entryPublishedDate, e.visitedDate as visitedDate, e.category as entryCategory, e.bookmark as bookmark, CASE WHEN e.content IS NOT NULL AND e.content != '' THEN 1 ELSE 0 END as hasContent, CASE WHEN e.original_html IS NOT NULL AND e.original_html != '' THEN 1 ELSE 0 END as hasOriginalHtml, CASE WHEN e.html IS NOT NULL AND e.html != '' THEN 1 ELSE 0 END as hasHtml, CASE WHEN e.translated IS NOT NULL AND e.translated != '' THEN 1 ELSE 0 END as hasTranslated, f.id as feedId, f.ttsSpeechRate as ttsSpeechRate, f.language as feedLanguage, f.title as feedTitle, f.imageUrl as feedImageUrl " +
            "FROM entry_table e " +
            "LEFT JOIN feed_table f ON e.feedId = f.id " +
            "WHERE e.id = :id")
    EntryInfo getEntryInfoById(long id);

    @Query("SELECT * FROM entry_table WHERE isCached = 1 AND priority > 0 ORDER BY priority ASC")
    List<Entry> getPreloadedEntries();

    @Query("UPDATE entry_table SET isCached = :isCached WHERE id = :entryId")
    void updatePreloadStatus(long entryId, boolean isCached);

    @Query("SELECT id FROM entry_table WHERE id = :id")
    long checkEntryExist(long id);

    @Query("SELECT content FROM entry_table WHERE id = :id")
    String getContentById(long id);

    @Query("SELECT html FROM entry_table WHERE id = :id")
    String getHtmlById(long id);

    @Query("UPDATE entry_table SET visitedDate = :date WHERE id = :entryId")
    void updateDate(Date date, long entryId);

    @Query("UPDATE entry_table SET content = :content WHERE id = :id")
    void updateContent(String content, long id);

    @Query("UPDATE entry_table SET html = :html WHERE id = :id")
    void updateHtml(String html, long id);

    @Query("SELECT id FROM entry_table ORDER BY visitedDate DESC LIMIT 1")
    long getLastVisitedEntryId();

    @Query("SELECT visitedDate FROM entry_table WHERE id = :id")
    Date checkIsVisited(long id);

    @Query("SELECT * FROM entry_table WHERE content is null AND priority != 0 ORDER BY priority ASC LIMIT 1")
    Entry getEmptyEntryOrderByPrior();

    @Query("SELECT * FROM entry_table WHERE content is null")
    Entry getEmptyEntry();

    @Query("SELECT * FROM entry_table WHERE original_html IS NULL AND priority != 0 ORDER BY priority ASC LIMIT 1")
    Entry getEntryMissingHtmlByPriority();

    @Query("SELECT * FROM entry_table WHERE original_html IS NULL")
    Entry getEntryMissingHtml();

    @Query("SELECT id FROM entry_table WHERE feedId = :id")
    List<Long> getIdsByFeedId(long id);

    @Query("SELECT id FROM entry_table WHERE visitedDate is not null AND bookmark is not 'Y'")
    List<Long> getAllVisitedEntriesId();

    @Query("DELETE FROM entry_table WHERE visitedDate is not null AND bookmark is not 'Y'")
    void deleteAllVisitedEntries();

    @Query("UPDATE entry_table SET priority = 0 WHERE priority != 0 AND content IS NOT NULL AND original_html IS NOT NULL")
    void clearPriority();

    @Query("UPDATE entry_table SET priority = :priority WHERE id = :id AND content is null")
    void updatePriority(int priority, long id);

    @Query("UPDATE entry_table SET priority = :priority WHERE id = :id")
    void updatePriorityUnconditional(int priority, long id);

    @Query("UPDATE entry_table SET sentCountStopAt = :sentCount WHERE id = :id")
    void updateSentCount(int sentCount, long id);

    @Query("UPDATE entry_table SET sentCountStopAt = :sentCount WHERE id = :id")
    void updateSentCountByLink(int sentCount, long id);

    @Query("UPDATE entry_table SET title = :title WHERE feedId = :feedId AND link = :link")
    void updateTitle(long feedId, String title, String link);

    @Query("UPDATE entry_table SET link = :link WHERE feedId = :feedId AND title = :title")
    void updateLink(long feedId, String title, String link);

    @Query("UPDATE entry_table SET bookmark = :bool WHERE id = :id")
    void updateBookmark(String bool, long id);

    @Query("UPDATE entry_table SET content = null, html = null, sentCountStopAt = 0 WHERE feedId = :id")
    void updateContentByFeedId(long id);

    @Query("SELECT * FROM entry_table WHERE feedId = :feedId")
    List<Entry> getEntriesByFeedId(long feedId);

    @Query("SELECT sentCountStopAt FROM entry_table WHERE id = :id")
    int getSentCount(long id);

    @Query("SELECT bookmark FROM entry_table WHERE id = :id")
    String getBookmark(long id);

    @Query("SELECT COUNT(id) FROM entry_table WHERE visitedDate is null AND bookmark = 'Y'")
    Flowable<Integer> getUnreadCountByBookmark();

    @Query("SELECT COUNT(id) FROM entry_table WHERE visitedDate is null AND feedId = :id AND bookmark = 'Y'")
    Flowable<Integer> getUnreadCountByBookmark(long id);

    @Query("SELECT COUNT(id) FROM entry_table WHERE visitedDate is null")
    Flowable<Integer> getUnreadCount();

    @Query("SELECT COUNT(id) FROM entry_table WHERE visitedDate is null AND feedId = :id")
    Flowable<Integer> getUnreadCount(long id);

    @Query("DELETE FROM entry_table WHERE feedId = :feedId AND id NOT IN (SELECT id FROM entry_table WHERE feedId = :feedId ORDER BY publishedDate DESC LIMIT :limit) AND id NOT IN (SELECT id FROM entry_table WHERE bookmark = 'Y' AND feedId = :feedId)")
    void limitEntriesByFeed(long feedId, int limit);

    @Query("UPDATE entry_table SET priority = 1 WHERE content IS NULL AND priority = 0")
    void requeueMissingEntries();

    @Query("SELECT * FROM entry_table WHERE id = :id")
    Entry getEntryById(long id);

    @Query("SELECT * FROM entry_table WHERE id = :id")
    LiveData<Entry> getEntryEntityById(long id);

    @Query("SELECT * FROM entry_table WHERE html IS NOT NULL AND html NOT LIKE '%translated-title%'")
    List<Entry> getUntranslatedEntries();

    @Query("SELECT original_html FROM entry_table WHERE id = :id")
    String getOriginalHtmlById(long id);

    @Query("UPDATE entry_table SET original_html = :originalHtml WHERE id = :id")
    void updateOriginalHtml(String originalHtml, long id);

    @Query("UPDATE entry_table SET translated = :translated WHERE id = :id")
    void updateTranslated(String translated, long id);

    @Query("UPDATE entry_table SET translated = :translated WHERE id = :id")
    void updateTranslatedText(String translated, long id);

    @Query("UPDATE entry_table SET publishedDate = :date WHERE id = :id")
    void updatePublishedDate(Date date, long id);

    @Query("SELECT id FROM entry_table WHERE original_html IS NULL AND priority = 0 ORDER BY publishedDate DESC LIMIT 50")
    List<Long> getFailedEntryIds();
}
