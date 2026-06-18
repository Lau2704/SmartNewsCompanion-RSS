package my.mmu.Kaixuanrssnewsreader.service.rss;

import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import my.mmu.Kaixuanrssnewsreader.model.FeedSourceType;

public class WebPageScraper {

    private static final String TAG = "WebPageScraper";
    private static final int MAX_ARTICLES = 30;
    private static final int TIMEOUT_MS = 8000;
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36";

    private static final Set<String> REJECTED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".svg", ".webp",
            ".mp3", ".mp4", ".avi", ".mov", ".wmv", ".flv",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".zip", ".rar", ".7z", ".tar", ".gz",
            ".css", ".js", ".json", ".xml", ".rss"
    ));

    private static final Set<String> REJECTED_PATH_SEGMENTS = new HashSet<>(Arrays.asList(
            "about", "contact", "privacy", "terms", "legal", "login", "signin",
            "signup", "register", "search", "tag", "tags", "author",
            "comment", "comments", "reply", "print", "share", "bookmark",
            "feed", "feeds", "rss", "sitemap", "robots", "favicon",
            "wp-admin", "wp-login", "admin", "dashboard",
            "category", "categories", "archive", "archives",
            "cart", "checkout", "account", "profile", "settings",
            "faq", "help", "support", "advertise", "subscribe",
            "newsletter", "notification", "notifications"
    ));

    private static final Pattern TRACKING_PARAMS = Pattern.compile(
            "utm_[a-z_]+|fbclid|gclid|mc_cid|mc_eid|ref|source|rssid|fb|spref"
    );

    public RssFeed scrape(String url) throws Exception {
        Document doc = FeedSourceResolver.fetchDocument(url);
        if (doc == null) {
            throw new Exception("Failed to fetch page: HTTP error for " + url);
        }
        return parse(doc, url);
    }

    RssFeed parse(Document doc, String baseUrl) {
        RssFeed feed = new RssFeed();
        feed.setLink(baseUrl);
        feed.setFeedType(FeedSourceType.WEB);

        extractFeedMetadata(doc, feed, baseUrl);

        String domain = extractDomain(baseUrl);
        Map<String, RssItem> itemsByUrl = new LinkedHashMap<>();

        scrapeArticleElements(doc, domain, itemsByUrl);
        if (itemsByUrl.size() < MAX_ARTICLES) {
            scrapeHeadingLinks(doc, domain, itemsByUrl);
        }
        if (itemsByUrl.size() < MAX_ARTICLES) {
            scrapeContainerLinks(doc, domain, itemsByUrl);
        }

        for (RssItem item : itemsByUrl.values()) {
            feed.addRssItem(item);
        }

        Log.d(TAG, "Scraped " + feed.getRssItems().size() + " articles from " + baseUrl);
        return feed;
    }

    private void extractFeedMetadata(Document doc, RssFeed feed, String baseUrl) {
        String title = null;
        Element ogSiteName = doc.selectFirst("meta[property=og:site_name]");
        if (ogSiteName != null) {
            title = ogSiteName.attr("content");
        }
        if (title == null || title.trim().isEmpty()) {
            title = doc.title();
        }
        if (title != null && !title.trim().isEmpty()) {
            feed.setTitle(title.trim());
        } else {
            feed.setTitle(extractDomain(baseUrl));
        }

        Element metaDesc = doc.selectFirst("meta[name=description]");
        if (metaDesc == null) {
            metaDesc = doc.selectFirst("meta[property=og:description]");
        }
        if (metaDesc != null) {
            String desc = metaDesc.attr("content");
            if (desc != null && !desc.trim().isEmpty()) {
                feed.setDescription(desc.trim());
            }
        }

        Element htmlTag = doc.selectFirst("html");
        if (htmlTag != null) {
            String lang = htmlTag.attr("lang");
            if (lang != null && !lang.trim().isEmpty()) {
                feed.setLanguage(lang.trim());
            }
        }

        String favicon = null;
        Element faviconLink = doc.selectFirst("link[rel~=(?i)icon], link[rel~=(?i)shortcut icon]");
        if (faviconLink != null) {
            favicon = faviconLink.absUrl("href");
        }
        if (favicon == null || favicon.isEmpty()) {
            favicon = "https://www.google.com/s2/favicons?sz=64&domain_url=" + baseUrl;
        }
        feed.setImageUrl(favicon);
    }

    private void scrapeArticleElements(Document doc, String domain, Map<String, RssItem> itemsByUrl) {
        Elements articles = doc.select("article");
        for (Element article : articles) {
            if (itemsByUrl.size() >= MAX_ARTICLES) break;

            Element linkEl = article.selectFirst("a[href]");
            if (linkEl == null) continue;

            String href = normalizeUrl(linkEl.absUrl("href"), domain);
            if (!isValidArticleUrl(href, domain)) continue;

            String title = extractTitle(linkEl, article);
            if (title == null || title.trim().isEmpty()) continue;

            RssItem item = buildItem(title, href, article, domain);
            itemsByUrl.put(href, item);
        }
    }

    private void scrapeHeadingLinks(Document doc, String domain, Map<String, RssItem> itemsByUrl) {
        Elements headingLinks = doc.select("h2 a[href], h3 a[href], h4 a[href]");
        for (Element linkEl : headingLinks) {
            if (itemsByUrl.size() >= MAX_ARTICLES) break;

            String href = normalizeUrl(linkEl.absUrl("href"), domain);
            if (!isValidArticleUrl(href, domain)) continue;
            if (itemsByUrl.containsKey(href)) continue;

            String title = linkEl.text().trim();
            if (title.isEmpty()) continue;

            Element parent = linkEl.parent();
            if (parent != null) parent = parent.parent();

            RssItem item = buildItem(title, href, parent != null ? parent : linkEl, domain);
            itemsByUrl.put(href, item);
        }
    }

    private void scrapeContainerLinks(Document doc, String domain, Map<String, RssItem> itemsByUrl) {
        String[] containerPatterns = {
                "[class*=article]", "[class*=post]", "[class*=story]",
                "[class*=news]", "[class*=content]", "[class*=entry]",
                "[id*=article]", "[id*=post]", "[id*=story]",
                "[id*=news]", "[id*=content]"
        };

        for (String pattern : containerPatterns) {
            if (itemsByUrl.size() >= MAX_ARTICLES) break;

            Elements containers = doc.select(pattern);
            for (Element container : containers) {
                if (itemsByUrl.size() >= MAX_ARTICLES) break;

                Elements links = container.select("a[href]");
                for (Element linkEl : links) {
                    if (itemsByUrl.size() >= MAX_ARTICLES) break;

                    String href = normalizeUrl(linkEl.absUrl("href"), domain);
                    if (!isValidArticleUrl(href, domain)) continue;
                    if (itemsByUrl.containsKey(href)) continue;

                    String title = extractTitle(linkEl, container);
                    if (title == null || title.trim().isEmpty()) continue;

                    RssItem item = buildItem(title, href, container, domain);
                    itemsByUrl.put(href, item);
                }
            }
        }
    }

    private String extractTitle(Element linkEl, Element context) {
        Element heading = context.selectFirst("h1, h2, h3, h4, h5, h6");
        if (heading != null) {
            String headingText = heading.text().trim();
            if (!headingText.isEmpty() && headingText.length() > 5) {
                return headingText;
            }
        }

        String linkText = linkEl.text().trim();
        if (!linkText.isEmpty() && linkText.length() > 5) {
            return linkText;
        }

        String titleAttr = linkEl.attr("title");
        if (titleAttr != null && !titleAttr.trim().isEmpty()) {
            return titleAttr.trim();
        }

        return null;
    }

    private RssItem buildItem(String title, String href, Element context, String domain) {
        RssItem item = new RssItem();
        item.setTitle(title.trim());
        item.setLink(href);

        if (context != null) {
            Element img = context.selectFirst("img[src]");
            if (img != null) {
                String src = img.absUrl("src");
                if (src != null && !src.isEmpty()) {
                    item.setImageUrl(src);
                }
            }

            List<String> descParts = new ArrayList<>();
            Elements paragraphs = context.select("p");
            for (Element p : paragraphs) {
                String pText = p.text().trim();
                if (!pText.isEmpty() && pText.length() > 15) {
                    descParts.add(pText);
                    if (descParts.size() >= 2) break;
                }
            }
            if (!descParts.isEmpty()) {
                item.setDescription(join(descParts, " "));
            }

            Element time = context.selectFirst("time[datetime]");
            if (time != null) {
                String datetime = time.attr("datetime");
                if (datetime != null && !datetime.trim().isEmpty()) {
                    item.setPubDate(datetime.trim());
                }
            }

            if (!item.hasPubDate()) {
                Element dateEl = context.selectFirst("[class*=date], [class*=time], [class*=published], [class*=meta-date], [class*=post-date], [class*=entry-date]");
                if (dateEl != null) {
                    String dateText = dateEl.text().trim();
                    if (dateText != null && !dateText.isEmpty() && dateText.length() < 50) {
                        item.setPubDate(dateText);
                    }
                }
            }

            Element categoryEl = context.selectFirst("[class*=category], [class*=tag], a[rel=tag]");
            if (categoryEl != null) {
                String cat = categoryEl.text().trim();
                if (!cat.isEmpty() && cat.length() < 50) {
                    item.setCategory(cat);
                }
            }
        }

        return item;
    }

    private boolean isValidArticleUrl(String url, String domain) {
        if (url == null || url.isEmpty()) return false;
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false;

        String lowerUrl = url.toLowerCase();
        if (lowerUrl.startsWith("mailto:") || lowerUrl.startsWith("javascript:") || lowerUrl.startsWith("tel:")) {
            return false;
        }

        String urlNoFragment = url.split("#")[0];
        if (urlNoFragment.isEmpty()) return false;

        lowerUrl = urlNoFragment.toLowerCase();
        for (String ext : REJECTED_EXTENSIONS) {
            if (lowerUrl.endsWith(ext)) return false;
        }

        try {
            URI uri = new URI(urlNoFragment);
            String host = uri.getHost();
            if (host == null) return false;

            String baseDomain = extractBaseDomain(domain);
            String urlBaseDomain = extractBaseDomain(host);

            if (!baseDomain.equalsIgnoreCase(urlBaseDomain)) {
                String hostNoWww = host.startsWith("www.") ? host.substring(4) : host;
                String domainNoWww = domain.startsWith("www.") ? domain.substring(4) : domain;
                if (!hostNoWww.equalsIgnoreCase(domainNoWww) &&
                    !hostNoWww.endsWith("." + domainNoWww) &&
                    !domainNoWww.endsWith("." + hostNoWww)) {
                    return false;
                }
            }

            String path = uri.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) return false;

            String[] segments = path.split("/");
            int realSegments = 0;
            for (String seg : segments) {
                if (!seg.isEmpty()) {
                    realSegments++;
                    String lowerSeg = seg.toLowerCase();
                    if (REJECTED_PATH_SEGMENTS.contains(lowerSeg)) {
                        return false;
                    }
                    if (lowerSeg.equals("page") && realSegments == segments.length - 1) {
                        try {
                            String nextSeg = segments[segments.length - 1];
                            if (!nextSeg.isEmpty()) Integer.parseInt(nextSeg);
                            return false;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            if (realSegments < 1) return false;

        } catch (URISyntaxException e) {
            return false;
        }

        return true;
    }

    private String normalizeUrl(String url, String domain) {
        if (url == null || url.isEmpty()) return "";

        int fragmentIndex = url.indexOf('#');
        if (fragmentIndex > 0) {
            url = url.substring(0, fragmentIndex);
        }

        try {
            URI uri = new URI(url);
            String query = uri.getQuery();
            if (query != null) {
                String[] params = query.split("&");
                StringBuilder cleanQuery = new StringBuilder();
                for (String param : params) {
                    String[] kv = param.split("=", 2);
                    String key = kv[0];
                    Matcher m = TRACKING_PARAMS.matcher(key);
                    if (!m.matches()) {
                        if (cleanQuery.length() > 0) cleanQuery.append("&");
                        cleanQuery.append(param);
                    }
                }
                String newQuery = cleanQuery.length() > 0 ? cleanQuery.toString() : null;
                uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
                        uri.getPath(), newQuery, null);
                url = uri.toString();
            } else {
                uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
                        uri.getPath(), null, null);
                url = uri.toString();
            }
        } catch (URISyntaxException e) {
            Log.w(TAG, "Failed to normalize URL: " + url, e);
        }

        return url;
    }

    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost();
        } catch (URISyntaxException e) {
            return "";
        }
    }

    private String extractBaseDomain(String host) {
        if (host == null) return "";
        if (host.startsWith("www.")) host = host.substring(4);
        String[] parts = host.split("\\.");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        return host;
    }

    private String join(List<String> parts, String delimiter) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
