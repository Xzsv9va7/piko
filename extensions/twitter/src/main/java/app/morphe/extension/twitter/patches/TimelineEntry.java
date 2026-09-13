/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches;

import static app.morphe.extension.shared.StringRef.str;

import android.text.TextUtils;
import android.view.View;

import com.x.models.interstitial.BlurImageInterstitial;
import com.twitter.model.json.mediavisibility.JsonBlurredImageInterstitial;
import com.twitter.model.json.timeline.urt.JsonTimelineEntry;
import com.twitter.model.json.core.JsonSensitiveMediaWarning;
import com.twitter.model.json.timeline.urt.JsonTimelineModuleItem;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.twitter.Pref;
import app.morphe.extension.twitter.settings.SettingsStatus;
import app.morphe.extension.twitter.entity.Video;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import app.morphe.extension.crimera.PikoUtils;

public class TimelineEntry {
    public static final boolean hideAds;
    private static final boolean hideWTF,hideCTS,hideCTJ,hideDetailedPosts,hideRBMK,hidePinnedPosts,hidePremiumPrompt,showSensitiveMedia,hideTopPeopleSearch,hideTodaysNews;
    static {
        hideAds = (Pref.hideAds() && SettingsStatus.hideAds);
        hideWTF = (Pref.hideWTF() && SettingsStatus.hideWTF);
        hideCTS = (Pref.hideCTS() && SettingsStatus.hideCTS);
        hideCTJ = (Pref.hideCTJ() && SettingsStatus.hideCTJ);
        hideDetailedPosts = (Pref.hideDetailedPosts() && SettingsStatus.hideDetailedPosts);
        hideRBMK = (Pref.hideRBMK() && SettingsStatus.hideRBMK);
        hidePinnedPosts = (Pref.hideRPinnedPosts() && SettingsStatus.hideRPinnedPosts);
        hidePremiumPrompt = (Pref.hidePremiumPrompt() && SettingsStatus.hidePremiumPrompt);
        showSensitiveMedia = Pref.showSensitiveMedia();
        hideTopPeopleSearch = (Pref.hideTopPeopleSearch() && SettingsStatus.hideTopPeopleSearch);
        hideTodaysNews = (Pref.hideTodaysNews() && SettingsStatus.hideTodaysNews);
    }

    private static boolean isAdEntryId(String entryId) {
        String id = entryId.toLowerCase(Locale.ROOT);
        return id.contains("promoted")
            || id.contains("rtb")
            || id.contains("advertiser")
            || id.contains("search-ad")
            || id.contains("searchad")
            || id.contains("brand-takeover")
            || id.contains("timeline-spotlight")
            || id.startsWith("superhero")
            || id.startsWith("eventsummary")
            || id.startsWith("main-event-")
            || id.equals("pivot")
            || id.startsWith("pivot-");
    }

    private static boolean isEntryIdRemove(String entryId) {
        if (entryId == null || entryId.isEmpty()) {
            return false;
        }
        String[] split = entryId.split("-");
        String entryId2 = split[0];
        if (entryId2.equals("cursor")) {
            return false;
        }
        // Explore/search items use Guide- and semantic_core- prefixes; still drop ads.
        if (hideAds && isAdEntryId(entryId)) {
            return true;
        }
        if (entryId2.equals("Guide") || entryId2.startsWith("semantic_core")) {
            return false;
        }
        if (entryId2.equals("conversationthread") && split.length == 3 && hideAds) {
            return true;
        }
        if (entryId2.startsWith("tweetdetail") && hideDetailedPosts) {
            return true;
        }
        if (entryId2.equals("bookmarked") && hideRBMK) {
            return true;
        }
        if (entryId.startsWith("community-to-join") && hideCTJ) {
            return true;
        }
        if (entryId.startsWith("who-to-follow") && hideWTF) {
            return true;
        }
        if (entryId.startsWith("who-to-subscribe") && hideCTS) {
            return true;
        }
        if (entryId.startsWith("pinned-tweets") && hidePinnedPosts) {
            return true;
        }
        if (entryId.startsWith("messageprompt-") && hidePremiumPrompt) {
            return true;
        }
        if (entryId2.equals("toptabsrpusermodule") && hideTopPeopleSearch) {
            return true;
        }
        if (entryId.startsWith("stories") && hideTodaysNews) {
            return true;
        }
        return false;
    }

    private static final Map<Class<?>, Field[]> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final int PROMOTED_SCAN_DEPTH = 4;

    private static boolean isPromotedTypeName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("quickpromote") || n.contains("promotebutton") || n.contains("eligibility")) {
            return false;
        }
        return n.contains("promotedmetadata")
            || n.contains("promotedtrend")
            || n.contains("promotedcontent")
            || n.contains("advertisermetadata")
            || n.contains("searchad")
            || (n.contains("promoted") && (n.contains("json") || n.contains("metadata") || n.contains("ad")));
    }

    private static boolean shouldScanType(Class<?> cls) {
        if (cls == null || cls.isPrimitive() || cls.isEnum()) {
            return false;
        }
        String name = cls.getName();
        return name.startsWith("com.twitter.model.json")
            || name.startsWith("com.twitter.api.model.json")
            || name.startsWith("com.x.models");
    }

    private static boolean hasPromotedMetadata(Object obj, int depth) {
        if (obj == null || depth > PROMOTED_SCAN_DEPTH) {
            return false;
        }
        if (obj instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (hasPromotedMetadata(item, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        if (obj instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                if (hasPromotedMetadata(item, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        if (obj.getClass().isArray()) {
            return false;
        }
        Class<?> cls = obj.getClass();
        if (isPromotedTypeName(cls.getName())) {
            return true;
        }
        if (!shouldScanType(cls)) {
            return false;
        }
        Field[] fields = FIELD_CACHE.computeIfAbsent(cls, Class::getDeclaredFields);
        for (Field field : fields) {
            Class<?> type = field.getType();
            boolean typeLooksPromoted = isPromotedTypeName(type.getName())
                || isPromotedTypeName(field.getName());
            if (!typeLooksPromoted && !shouldScanType(type)
                && !Collection.class.isAssignableFrom(type)
                && !Map.class.isAssignableFrom(type)) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value == null) {
                    continue;
                }
                if (typeLooksPromoted || isPromotedTypeName(value.getClass().getName())) {
                    return true;
                }
                if (hasPromotedMetadata(value, depth + 1)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static Object hideIfPromoted(Object data) {
        if (!hideAds || data == null) {
            return data;
        }
        return hasPromotedMetadata(data, 0) ? null : data;
    }

    public static Object filterPromotedFromTypeahead(Object response) {
        if (!hideAds || response == null) {
            return response;
        }
        try {
            Field[] fields = FIELD_CACHE.computeIfAbsent(response.getClass(), Class::getDeclaredFields);
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(response);
                if (value == null) {
                    continue;
                }
                if (isPromotedTypeName(field.getType().getName()) || isPromotedTypeName(value.getClass().getName())) {
                    field.set(response, null);
                    continue;
                }
                if (value instanceof List<?> list) {
                    try {
                        list.removeIf(item -> item != null && hasPromotedMetadata(item, 0));
                    } catch (UnsupportedOperationException ignored) {
                        List<Object> filtered = new ArrayList<>(list.size());
                        for (Object item : list) {
                            if (item == null || !hasPromotedMetadata(item, 0)) {
                                filtered.add(item);
                            }
                        }
                        field.set(response, filtered);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return response;
    }

    public static JsonTimelineEntry checkEntry(JsonTimelineEntry jsonTimelineEntry) {
        try {
            if (jsonTimelineEntry == null) {
                return null;
            }
            if (isEntryIdRemove(jsonTimelineEntry.a)) {
                return null;
            }
            if (hideAds && hasPromotedMetadata(jsonTimelineEntry, 0)) {
                return null;
            }
        } catch (Exception ignored) {
        }
        return jsonTimelineEntry;
    }
    public static JsonTimelineModuleItem checkEntry(JsonTimelineModuleItem jsonTimelineModuleItem) {
        try {
            if (jsonTimelineModuleItem == null) {
                return null;
            }
            if (isEntryIdRemove(jsonTimelineModuleItem.a)) {
                return null;
            }
            if (hideAds && hasPromotedMetadata(jsonTimelineModuleItem, 0)) {
                return null;
            }
        } catch (Exception ignored) {
        }
        return jsonTimelineModuleItem;
    }
    // Interface to reset obfuscated fields
    // This is one of the methods to avoid using Java Reflection, which has high overhead
    public interface JsonBlurredImageInterstitialPatchInterface {
        // Method is added during patching
        void patch_showSensitiveMedia();
    }
    public interface JsonSensitiveMediaWarningPatchInterface {
        // Method is added during patching
        void patch_showSensitiveMedia();
    }
    public static JsonBlurredImageInterstitial showSensitiveMedia(JsonBlurredImageInterstitialPatchInterface patchInterface) {
        if (showSensitiveMedia && patchInterface != null) {
            patchInterface.patch_showSensitiveMedia();
        }
        return (JsonBlurredImageInterstitial) patchInterface;
    }
    public static JsonSensitiveMediaWarning showSensitiveMedia(JsonSensitiveMediaWarningPatchInterface patchInterface) {
        if (showSensitiveMedia && patchInterface != null) {
            patchInterface.patch_showSensitiveMedia();
        }
        return (JsonSensitiveMediaWarning) patchInterface;
    }
    public static BlurImageInterstitial showSensitiveMedia(BlurImageInterstitial interstitial) {
        return showSensitiveMedia ? null : interstitial;
    }
    public static void showSensitiveImage(View view) {
        if (showSensitiveMedia && view != null) {
            // Click the 'Show' button on the timeline to make the blurred image visible
            Utils.runOnMainThread(view::callOnClick);
        }
    }
    // Caution: This profile may include potentially sensitive content
    private static final String sensitiveProfileHeader = str("profile_interstitial_sensitive_media_header");
    private static boolean isSensitiveProfile = false;
    public static int setSensitiveProfileWarningDialogTitle(String title, int visibility) {
        if (showSensitiveMedia) {
            // Check the title of the alert dialog to prevent other profile warnings (such as racism or terrorism) from closing
            isSensitiveProfile = TextUtils.equals(sensitiveProfileHeader, title);

            if (isSensitiveProfile) {
                // If it is a general sensitive media warning, hide the alert dialog
                return View.GONE;
            }
        }

        return visibility;
    }
    public static void showSensitiveProfile(View view) {
        if (isSensitiveProfile && view != null) {
            // If it is a general sensitive media warning, also click the dismiss button on the alert dialog
            // This is to prevent the UI from breaking due to incorrect WindowInsets calculations, even though the alert dialog is hidden
            Utils.runOnMainThread(view::callOnClick);
        }
    }
    public static boolean hidePromotedTrend(Object data) {
        return data != null && hideAds;
    }

    public static List<Object> timelineVideos(List<Object> videoEntities){
        int maxBitrate = 0;
        Object maxVideoObject = null;
        try{
            if(Pref.ENABLE_FORCE_HD) {
                for (Object vidObj : videoEntities) {
                    Video vid = new Video(vidObj);
                    String mediaExt = vid.getExtension();
                    if (!(mediaExt.equals("mp4"))) continue;

                    int bitrate = vid.getBitrate();
                    if(bitrate<maxBitrate) continue;
                    maxBitrate = bitrate;
                    maxVideoObject = vidObj;
                }
                if (maxVideoObject != null) {
                    ArrayList<Object> result = new ArrayList<>();
                    result.add(maxVideoObject);
                    return result;
                }
            }

        } catch (Exception ex) {
            PikoUtils.logger(ex);
        }

        return videoEntities;
    }

//end
}
