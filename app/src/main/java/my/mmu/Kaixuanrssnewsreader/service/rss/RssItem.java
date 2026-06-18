package my.mmu.Kaixuanrssnewsreader.service.rss;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RssItem {

    private String title;
    private String description;
    private String link;
    private String imageUrl;
    private Date pubDate;
    private String category;
    private int priority;

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Date getPubDate() {
        if (pubDate == null) {
            pubDate = new Date();
        }
        return pubDate;
    }

    public boolean hasPubDate() {
        return pubDate != null;
    }

    public void setPubDate(String pubDate) {
        if (pubDate == null || pubDate.isEmpty()) {
            return;
        }

        String[] dateFormats = {
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm:ss",
            "dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm:ss Z",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd",
            "MMMM d, yyyy",
            "MMM d, yyyy",
            "EEEE, MMMM d, yyyy",
            "dd MMM yyyy HH:mm:ss",
            "dd MMM yyyy",
            "dd MMMM yyyy HH:mm:ss",
            "dd MMMM yyyy",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy",
            "MM/dd/yyyy HH:mm:ss",
            "MM/dd/yyyy"
        };

        for (String format : dateFormats) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.ENGLISH);
                dateFormat.setLenient(false);
                Date parsedDate = dateFormat.parse(pubDate);
                if (parsedDate != null) {
                    this.pubDate = parsedDate;
                    return;
                }
            } catch (ParseException e) {
                continue;
            }
        }
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isValid() {
        return title != null && !title.isEmpty() && link != null && !link.isEmpty();
    }
}