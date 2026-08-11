package tw.nekomimi.nekogram.shamsicalendar;

public class LanguageUtils {
    public static String getPersianNumbers(String string) {
        return string.replace("0", "۰").replace("1", "١").replace("2", "۲").replace("3", "۳").replace("4", "۴").replace("5", "۵").replace("6", "۶").replace("7", "۷").replace("8", "۸").replace("9", "۹");
    }
}
