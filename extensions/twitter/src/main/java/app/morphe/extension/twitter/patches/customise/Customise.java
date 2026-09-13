/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.customise;

import java.util.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.view.View;
import android.view.ViewGroup;

import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.twitter.Pref;
import app.morphe.extension.crimera.PikoUtils;
import com.twitter.model.json.search.JsonTypeaheadResponse;
import app.morphe.extension.twitter.entity.Debug;
import app.morphe.extension.twitter.patches.TimelineEntry;

import static app.morphe.extension.shared.StringRef.str;

@SuppressWarnings("unused")
public class Customise {
    private static void logger(Object j){
        PikoUtils.logger(j);
    }

    /**
     * Injection point.
     */
    public static List<?> navBar(List<?> inp){
        try {
            ArrayList<?> choices = Pref.customNavbar();
            if (!choices.isEmpty()) {
                for (Object obj : inp) {
                    if (obj == null) continue;
                    String itemStr = obj.toString();
                    if (choices.contains(itemStr)) {
                        inp.remove(obj);
                    }
                }
            }
        } catch (Exception e){
            logger(e);
        }
        return inp;
    }

    /**
     * Injection point.
     * @param inp           ArrayList composed of TabCustomizationItems.
     * @param fieldName1    Field name of TabCustomizationItem.
     * @param fieldName2    Field name of Enum.
     */
    public static ArrayList<?> navBar(ArrayList<?> inp, String fieldName1, String fieldName2){
        try {
            ArrayList<?> choices = Pref.customNavbar();
            if (!choices.isEmpty()) {
                for (Object obj : inp) {
                    var cls = new Debug(obj);
                    Object tabCustomizationItem = cls.getField(fieldName1);
                    if (tabCustomizationItem != null) {
                        var cls2 = new Debug(tabCustomizationItem);
                        if (cls2.getField(fieldName2) instanceof String itemStr) {
                            // Since the name of the new Enum is all uppercase, convert all values in the string array to uppercase for comparison.
                            if (choices.contains(itemStr.toUpperCase())) {
                                inp.remove(obj);
                            }
                        }
                    }
                }
            }
        } catch (Exception e){
            logger(e);
        }
        return inp;
    }

    /**
     * Injection point.
     * @param map A map with Enum and TabCustomizationItem as key-value pairs.
     *            This map is used as a cache.
     */
    public static Map<Object, Object> navBar(Map<Object, Object> map) {
        try {
            ArrayList<?> choices = Pref.customNavbar();
            if (!choices.isEmpty()) {
                if (!map.isEmpty()) {
                    // Starting with 11.90.1-release.0, this Map can be immutable.
                    // To avoid exceptions, create a new Map.
                    Map<Object, Object> newMap = Collections.synchronizedMap(new LinkedHashMap<>());
                    for (Map.Entry<Object, Object> entry : map.entrySet()) {
                        Object key = entry.getKey();
                        if (key == null) continue;
                        // Since the name of the new Enum is all uppercase, convert all values in the string array to uppercase for comparison.
                        String itemStr = key.toString().toUpperCase();
                        if (!choices.contains(itemStr)) {
                            newMap.put(key, map.get(key));
                        }
                    }
                    return newMap;
                }
            }
        } catch (Exception e){
            logger(e);
        }
        return map;
    }

    public static ArrayList profiletabs(ArrayList inp){
        try{
            ArrayList choices = Pref.customProfileTabs();

            if(choices.isEmpty()) return inp;

            Object inpObj = inp.clone();
            ArrayList<?> arr = (ArrayList<?>) inpObj;
            Iterator itr = inp.iterator();

            while (itr.hasNext()) {
                Object obj = itr.next();
                Class<?> clazz = obj.getClass();
                Field field = clazz.getDeclaredField("g");
                String g = (String) field.get(obj);

                if ((g!=null && choices.contains(g)) || (g==null && choices.contains("subs"))){
                    arr.remove(obj);
                }
            }
            return arr;


        }catch (Exception e){
            logger(e);
        }
        return inp;
    }

    public static boolean hideAllExploreTabs() {
        try {
            ArrayList<?> choices = Pref.customExploreTabs();
            if (choices.isEmpty()) {
                return false;
            }
            String[] all = ResourceUtils.getStringArray("piko_array_exploretabs_val");
            if (all == null || all.length == 0) {
                return false;
            }
            for (String id : all) {
                if (!choices.contains(id)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            logger(e);
            return false;
        }
    }

    public static void onExploreTabLayoutLayout(View tabLayout) {
        if (tabLayout == null || !hideAllExploreTabs()) {
            return;
        }
        tabLayout.post(() -> hideExploreTabsChrome(tabLayout));
    }

    private static boolean isExploreTabLayout(View tabLayout) {
        try {
            Method getTabCount = tabLayout.getClass().getMethod("getTabCount");
            int count = (int) getTabCount.invoke(tabLayout);
            if (count < 2) {
                return false;
            }
            for (int i = 0; i < count; i++) {
                CharSequence title = tabText(tabLayout, i);
                if (title == null) {
                    continue;
                }
                String text = title.toString();
                if (text.equals(str("guide_tab_title_trending"))
                    || text.equals(str("guide_tab_title_fun"))
                    || text.equals(str("guide_tab_title_news"))
                    || text.equals(str("guide_tab_title_sports"))
                    || text.equals(str("guide_tab_title_entertainment"))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static CharSequence tabText(View tabLayout, int index) {
        try {
            Object tab = tabLayout.getClass().getMethod("getTabAt", int.class).invoke(tabLayout, index);
            if (tab == null) {
                return null;
            }
            try {
                return (CharSequence) tab.getClass().getMethod("getText").invoke(tab);
            } catch (Exception ignored) {
                Field field = tab.getClass().getDeclaredField("c");
                field.setAccessible(true);
                return (CharSequence) field.get(tab);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void hideExploreTabsChrome(View tabLayout) {
        try {
            if (!isExploreTabLayout(tabLayout)) {
                return;
            }
            tabLayout.setVisibility(View.GONE);
            if (!(tabLayout.getParent() instanceof ViewGroup parent)) {
                return;
            }
            boolean seenTabs = false;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child == tabLayout) {
                    seenTabs = true;
                    continue;
                }
                if (seenTabs) {
                    child.setVisibility(View.GONE);
                }
            }
        } catch (Exception e) {
            logger(e);
        }
    }

    public static ArrayList exploretabs(ArrayList inp){
        try{
            ArrayList choices = Pref.customExploreTabs();

            if(choices.isEmpty()) return inp;

            Object inpObj = inp.clone();
            ArrayList<?> arr = (ArrayList<?>) inpObj;
            Iterator itr = inp.iterator();

            while (itr.hasNext()) {

                Object obj = itr.next();
                Class<?> clazz = obj.getClass();
                Field field = clazz.getDeclaredField("a");
                String id = (String) field.get(obj);

                if (id!=null && choices.contains(id)){
                    arr.remove(obj);
                }
            }
            if (arr.isEmpty() || hideAllExploreTabs()) {
                return new ArrayList<>();
            }
            return arr;


        }catch (Exception e){
            logger(e);
        }
        return inp;
    }

    public static List inlineBar(List inp){
        try{
            ArrayList choices = Pref.inlineBar();

            if(choices.isEmpty()) return inp;

            List list2 = new ArrayList<>(inp);
            Iterator itr = inp.iterator();

            while (itr.hasNext()) {
                Object obj = itr.next();
                if (obj != null) {
                    String itemStr = obj.toString();
                    if(choices.contains(itemStr)){
                        list2.remove(obj);
                    }
                }
            }
            return list2;
        }catch (Exception e){
            logger(e);
        }
        return inp;
    }

    public static List sideBar(List inp){
        try{
            ArrayList choices = Pref.customSidebar();

            if(choices.isEmpty()) return inp;

            List list2 = new ArrayList<>(inp);
            Iterator itr = list2.iterator();

            while (itr.hasNext()) {
                Object obj = itr.next();
                String itemStr = obj.toString();
                if(choices.contains(itemStr)){
                    inp.remove(obj);
                }
            }

        }catch (Exception e){
            logger(e);
        }
        return inp;
    }

    public static JsonTypeaheadResponse typeAheadResponse(JsonTypeaheadResponse jsonTypeaheadResponse){
        try{
            TimelineEntry.filterPromotedFromTypeahead(jsonTypeaheadResponse);
            ArrayList choices = Pref.customSearchTypeAhead();
            if(!choices.isEmpty())
            {
                if (choices.contains("users")) {
                    jsonTypeaheadResponse.a = new ArrayList<>();
                }
                if (choices.contains("topics")) {
                    jsonTypeaheadResponse.b = new ArrayList<>();
                }
                if (choices.contains("events")) {
                    jsonTypeaheadResponse.c = new ArrayList<>();
                }
                if (choices.contains("lists")) {
                    jsonTypeaheadResponse.d = new ArrayList<>();
                }
                if (choices.contains("ordered_section")) {
                    jsonTypeaheadResponse.e = new ArrayList<>();
                }
            }
        }catch (Exception e){
            logger(e);
        }
        return jsonTypeaheadResponse;
    }

    public static List searchTabs(List inp){
        try{
            ArrayList choices = Pref.searchTabs();

            if(choices.isEmpty()) return inp;

            List list2 = new ArrayList<>(inp);
            Iterator itr = inp.iterator();

            while (itr.hasNext()) {
                Object obj = itr.next();
                Class<?> clazz = obj.getClass();
                Field field = clazz.getDeclaredField("a");
                int itemVal = (int) field.get(obj);
                if(choices.contains(String.valueOf(itemVal))){
                    list2.remove(obj);
                }
            }
            return list2;
        }catch (Exception e){
            logger(e);
        }
        return inp;
    }

    public static List notificationTabs(List inp){
        try{
            String uriKey = "twitter://notifications/";
            ArrayList choices = Pref.notificationTabs();

            if(choices.isEmpty()) return inp;

            List list2 = new ArrayList<>(inp);
            Iterator itr = inp.iterator();

            while (itr.hasNext()) {
                Object obj = itr.next();
                Debug cls = new Debug(obj);

                Object itemStr = cls.getField("a");
                itemStr = String.valueOf(itemStr).replace(uriKey,"");
                if(choices.contains(itemStr)){
                    list2.remove(obj);
                }

            }
            return list2;

        }catch (Exception e){
            logger(e);
        }
        return inp;
    }

//class end
}