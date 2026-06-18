package my.mmu.Kaixuanrssnewsreader.service.rss;

import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import my.mmu.Kaixuanrssnewsreader.model.FeedSourceType;

public class FeedSourceResolver {

    private static final String TAG = "FeedSourceResolver";
    private static final int TIMEOUT_MS = 8000;
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36";

    private static final List<String> RSS_URL_PATTERNS = Arrays.asList(
            "/feed/", "/feed.xml", "/rss/", "/atom.xml", "/rss.xml", "/feeds/posts/default"
    );

    public static class Result {
        private final RssFeed feed;
        private final String feedType;

        public Result(RssFeed feed, String feedType) {
            this.feed = feed;
            this.feedType = feedType;
        }

        public RssFeed getFeed() {
            return feed;
        }

        public String getFeedType() {
            return feedType;
        }
    }

    private static class FeedCandidate {
        final String url;
        final int score;

        FeedCandidate(String url, int score) {
            this.url = url;
            this.score = score;
        }
    }

    public Result resolve(String url) throws Exception {
        String normalizedUrl = normalizeUrl(url);
        Log.d(TAG, "Resolving feed source for: " + normalizedUrl);

        try {
            RssFeed rssFeed = new RssReader(normalizedUrl).getFeed();
            rssFeed.setLink(normalizedUrl);
            rssFeed.setFeedType(FeedSourceType.RSS);
            Log.d(TAG, "Direct RSS parse succeeded for: " + normalizedUrl);
            return new Result(rssFeed, FeedSourceType.RSS);
        } catch (Exception e) {
            Log.d(TAG, "Direct RSS parse failed, trying autodiscovery: " + e.getMessage());
        }

        List<FeedCandidate> candidates = discoverRssCandidates(normalizedUrl);
        List<String> inputSegments = getPathSegments(normalizedUrl);
        boolean hasInputSegments = !inputSegments.isEmpty();

        for (FeedCandidate candidate : candidates) {
            if (hasInputSegments && candidate.score == 0) {
                Log.d(TAG, "Skipping low-score candidate: " + candidate.url);
                continue;
            }
            try {
                RssFeed rssFeed = new RssReader(candidate.url).getFeed();
                rssFeed.setLink(candidate.url);
                rssFeed.setFeedType(FeedSourceType.RSS);
                Log.d(TAG, "RSS autodiscovery succeeded: " + candidate.url + " (score=" + candidate.score + ")");
                return new Result(rssFeed, FeedSourceType.RSS);
            } catch (Exception e) {
                Log.d(TAG, "Autodiscovered RSS parse failed for " + candidate.url + ": " + e.getMessage());
            }
        }

        if (hasInputSegments && !candidates.isEmpty()) {
            for (FeedCandidate candidate : candidates) {
                if (candidate.score > 0) continue;
                try {
                    RssFeed rssFeed = new RssReader(candidate.url).getFeed();
                    rssFeed.setLink(candidate.url);
                    rssFeed.setFeedType(FeedSourceType.RSS);
                    Log.d(TAG, "RSS autodiscovery (fallback low-score) succeeded: " + candidate.url);
                    return new Result(rssFeed, FeedSourceType.RSS);
                } catch (Exception e) {
                    Log.d(TAG, "Fallback RSS parse failed for " + candidate.url + ": " + e.getMessage());
                }
            }
        }

        Log.d(TAG, "Trying common RSS URL patterns for: " + normalizedUrl);
        for (String pattern : RSS_URL_PATTERNS) {
            String candidateUrl = normalizedUrl + pattern;
            try {
                RssFeed rssFeed = new RssReader(candidateUrl).getFeed();
                rssFeed.setLink(candidateUrl);
                rssFeed.setFeedType(FeedSourceType.RSS);
                Log.d(TAG, "RSS pattern guess succeeded: " + candidateUrl);
                return new Result(rssFeed, FeedSourceType.RSS);
            } catch (Exception e) {
                Log.d(TAG, "RSS pattern guess failed for " + candidateUrl);
            }
        }

        Log.d(TAG, "Falling back to web page scraping for: " + normalizedUrl);
        WebPageScraper scraper = new WebPageScraper();
        RssFeed scrapedFeed = scraper.scrape(normalizedUrl);

        if (scrapedFeed.getRssItems().isEmpty()) {
            throw new Exception("No articles found at: " + normalizedUrl);
        }

        Log.d(TAG, "Web scraping succeeded, found " + scrapedFeed.getRssItems().size() + " articles");
        return new Result(scrapedFeed, FeedSourceType.WEB);
    }

    public Result resolveForRefresh(String url, String existingFeedType) throws Exception {
        if (FeedSourceType.WEB.equals(existingFeedType)) {
            WebPageScraper scraper = new WebPageScraper();
            RssFeed scrapedFeed = scraper.scrape(url);
            if (scrapedFeed.getRssItems().isEmpty()) {
                throw new Exception("No articles found at: " + url);
            }
            return new Result(scrapedFeed, FeedSourceType.WEB);
        }

        return resolve(url);
    }

    static Document fetchDocument(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .referrer("https://www.google.com/")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Connection", "keep-alive")
                    .get();
        } catch (Exception e) {
            Log.d(TAG, "JSoup HTTP fetch failed for " + url + ", trying HttpURLConnection: " + e.getMessage());
        }

        return fetchWithHttpUrlConnection(url);
    }

    private static Document fetchWithHttpUrlConnection(String url) {
        HttpURLConnection connection = null;
        try {
            URL urlObj = new URL(url);
            connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            connection.setRequestProperty("Referer", "https://www.google.com/");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "HttpURLConnection also failed: HTTP " + responseCode + " for " + url);
                return null;
            }

            InputStream inputStream = connection.getInputStream();
            Document doc = Jsoup.parse(inputStream, null, url);
            Log.d(TAG, "HttpURLConnection fetch succeeded for: " + url);
            return doc;
        } catch (Exception e) {
            Log.d(TAG, "HttpURLConnection fetch failed for " + url + ": " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<FeedCandidate> discoverRssCandidates(String url) {
        List<FeedCandidate> candidates = new ArrayList<>();
        Document doc = fetchDocument(url);

        if (doc == null) {
            Log.d(TAG, "Could not fetch document for autodiscovery");
            return candidates;
        }

        try {
            Elements rssLinks = doc.select("link[rel=alternate][type=\"application/rss+xml\"]");
            if (rssLinks.isEmpty()) {
                rssLinks = doc.select("link[rel=alternate][type=\"application/atom+xml\"]");
            }

            List<String> inputSegments = getPathSegments(url);

            for (Element linkEl : rssLinks) {
                String href = linkEl.absUrl("href");
                if (href == null || href.isEmpty()) continue;

                int score = scorePathMatch(inputSegments, href);
                candidates.add(new FeedCandidate(href, score));
                Log.d(TAG, "Discovered RSS candidate: " + href + " (score=" + score + ")");
            }

            Collections.sort(candidates, new Comparator<FeedCandidate>() {
                @Override
                public int compare(FeedCandidate a, FeedCandidate b) {
                    return Integer.compare(b.score, a.score);
                }
            });

        } catch (Exception e) {
            Log.d(TAG, "RSS autodiscovery parsing failed: " + e.getMessage());
        }
        return candidates;
    }

    private int scorePathMatch(List<String> inputSegments, String candidateUrl) {
        if (inputSegments.isEmpty()) return 0;

        List<String> candidateSegments = getPathSegments(candidateUrl);
        if (candidateSegments.isEmpty()) return 0;

        int score = 0;
        int minLen = Math.min(inputSegments.size(), candidateSegments.size());
        for (int i = 0; i < minLen; i++) {
            if (inputSegments.get(i).equalsIgnoreCase(candidateSegments.get(i))) {
                score++;
            } else {
                break;
            }
        }

        if (candidateUrl.toLowerCase().contains("comment")) {
            score = Math.max(0, score - 5);
        }

        return score;
    }

    private List<String> getPathSegments(String url) {
        List<String> segments = new ArrayList<>();
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path != null) {
                for (String seg : path.split("/")) {
                    String trimmed = seg.trim();
                    if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("feed") && !trimmed.equalsIgnoreCase("rss")) {
                        segments.add(trimmed.toLowerCase());
                    }
                }
            }
        } catch (URISyntaxException e) {
            Log.w(TAG, "Failed to parse path segments from: " + url, e);
        }
        return segments;
    }

    private String normalizeUrl(String url) {
        if (url == null || url.trim().isEmpty()) return url;
        url = url.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
