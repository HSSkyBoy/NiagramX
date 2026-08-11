package tw.nekomimi.nekogram.shamsicalendar;

/**
 * Created by Saman on 3/31/2017 AD.
 */

public final class PersianDateFormat {

    private PersianDateFormat() {
    }

    public static String format(PersianDate date, String pattern) {
        if (pattern == null) pattern = "l j F Y H:i:s";
        String[] key = {"a", "l", "j", "F", "Y", "H", "i", "s", "d", "g", "n", "m", "t", "w", "y", "z",
                "A", "L", "X", "C", "E", "T", "b", "D", "e", "B", "S"};
        String year2;
        if (("" + date.getShYear()).length() == 2) {
            year2 = "" + date.getShYear();
        } else if (("" + date.getShYear()).length() == 3) {
            year2 = ("" + date.getShYear()).substring(2, 3);
        } else {
            year2 = ("" + date.getShYear()).substring(2, 4);
        }
        String[] values = {date.getShortTimeOfTheDay(), date.dayName(), "" + date.getShDay(),
                date.monthName(),
                "" + date.getShYear(),
                textNumberFilterStatic("" + date.getHour()), textNumberFilterStatic("" + date.getMinute()),
                textNumberFilterStatic("" + date.getSecond()),
                textNumberFilterStatic("" + date.getShDay()), "" + date.getHour(), "" + date.getShMonth(),
                textNumberFilterStatic("" + date.getShMonth()),
                "" + date.getMonthDays(), "" + date.dayOfWeek(), year2, "" + date.getDayInYear(),
                date.getTimeOfTheDay(),
                (date.isLeap() ? "1" : "0"),
                date.AfghanMonthName(),
                date.KurdishMonthName(),
                date.PashtoMonthName(),
                date.monthNamesLatin(),
                LanguageUtils.getPersianNumbers(String.valueOf(date.getShDay())),
                LanguageUtils.getPersianNumbers(String.valueOf(date.getShYear())),
                LanguageUtils.getPersianNumbers(String.valueOf(date.getShMonth())),
                LanguageUtils.getPersianNumbers(textNumberFilterStatic("" + date.getHour())),
                LanguageUtils.getPersianNumbers(textNumberFilterStatic("" + date.getMinute()))
        };
        for (int i = 0; i < key.length; i++) {
            pattern = pattern.replace(key[i], values[i]);
        }
        return pattern;
    }

    public static String format(PersianDate date) {
        return format(date, null);
    }

    public static String textNumberFilterStatic(String date) {
        if (date.length() < 2) {
            return "0" + date;
        }
        return date;
    }
}
