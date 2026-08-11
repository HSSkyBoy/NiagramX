package tw.nekomimi.nekogram.shamsicalendar;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import org.telegram.messenger.FileLog;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import tw.nekomimi.nekogram.NekoConfig;

@SuppressLint("SimpleDateFormat")
public class PersianDate {

    /*----- Define Variable ---*/
    private Long timeInMilliSecond;
    public static final int AM = 1;
    public static final int PM = 2;
    public static final String AM_SHORT_NAME = "ق.ظ";
    public static final String PM_SHORT_NAME = "ب.ظ";
    public static final String AM_NAME = "قبل از ظهر";
    public static final String PM_NAME = "بعد از ظهر";
    private int shYear;
    private int shMonth;
    private int shDay;
    private int grgYear;
    private int grgMonth;
    private int grgDay;
    private int hour;
    private int minute;
    private int second;

    public enum Dialect {
        AFGHAN,
        IRANIAN,
        KURDISH,
        PASHTO,
        LATIN
    }

    /**
     * Contractor
     */
    public PersianDate() {
        this.timeInMilliSecond = new Date().getTime();
        this.changeTime();
    }

    /**
     * Contractor
     */
    public PersianDate(Long timeInMilliSecond) {
        this.timeInMilliSecond = timeInMilliSecond;
        this.changeTime();
    }

    /**
     * ---- Don not change---
     */
    private final int[][] grgSumOfDays = {
            {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365},
            {0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335, 366}};
    private final int[][] hshSumOfDays = {
            {0, 31, 62, 93, 124, 155, 186, 216, 246, 276, 306, 336, 365},
            {0, 31, 62, 93, 124, 155, 186, 216, 246, 276, 306, 336, 366}};
    private final String[] dayNames = {"شنبه", "یک‌شنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه",
            "جمعه"};
    private final String[] monthNames = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};
    private final String[] monthNamesLatin = {"Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
            "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"};
    private final String[] AfghanMonthNames = {"حمل", "ثور", "جوزا", "سرطان", "اسد", "سنبله", "میزان",
            "عقرب", "قوس", "جدی", "دلو", "حوت"};
    private final String[] KurdishMonthNames = {"جیژنان", "گولان", "زه ردان", "په رپه ر", "گه لاویژ",
            "نوخشان", "به ران", "خه زان", "ساران", "بفران", "به ندان", "رمشان"};
    private final String[] PashtoMonthNames = {"وری", "غويی", "غبرګولی", "چنګاښ", "زمری", "وږی",
            "تله", "لړم", "ليندۍ", "مرغومی", "سلواغه", "كب"};
    private String delimiter = "/";

    /*---- Setter And getter ----*/
    public int getShYear() {
        return shYear;
    }

    public PersianDate setShYear(int shYear) {
        this.shYear = shYear;
        this.prepareDate2(this.getShYear(), this.getShMonth(), this.getShDay());
        return this;
    }

    public int getShMonth() {
        return shMonth;
    }

    public PersianDate setShMonth(int shMonth) {
        this.shMonth = shMonth;
        this.prepareDate2(this.getShYear(), this.getShMonth(), this.getShDay());
        return this;
    }

    public int getShDay() {
        return shDay;
    }

    @SuppressWarnings("UnusedReturnValue")
    public PersianDate setShDay(int shDay) {
        this.shDay = shDay;
        this.prepareDate2(this.getShYear(), this.getShMonth(), this.getShDay());
        return this;
    }

    public int getGrgYear() {
        return grgYear;
    }

    public PersianDate setGrgYear(int grgYear) {
        this.grgYear = grgYear;
        prepareDate();
        return this;
    }

    public int getGrgMonth() {
        return grgMonth;
    }

    public PersianDate setGrgMonth(int grgMonth) {
        this.grgMonth = grgMonth;
        prepareDate();
        return this;
    }

    public int getGrgDay() {
        return grgDay;
    }

    public PersianDate setGrgDay(int grgDay) {
        this.grgDay = grgDay;
        prepareDate();
        return this;
    }

    public int getHour() {
        return hour;
    }

    public PersianDate setHour(int hour) {
        this.hour = hour;
        prepareDate();
        return this;
    }

    public int getMinute() {
        return minute;
    }

    public PersianDate setMinute(int minute) {
        this.minute = minute;
        prepareDate();
        return this;
    }

    public int getSecond() {
        return second;
    }

    public PersianDate setSecond(int second) {
        this.second = second;
        prepareDate();
        return this;
    }

    public String getDelimiter() {
        return this.delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getPersianNormalDate() {
        if (NekoConfig.displayPersianCalendarByLatin.Bool()) {
            return this.getShDay() + " " + this.monthNamesLatin() + " " + this.getShYear();
        } else {
            return LanguageUtils.getPersianNumbers(String.valueOf(this.getShDay())) + " " + this.monthName() + " " + LanguageUtils.getPersianNumbers(String.valueOf(this.getShYear()));
        }
    }

    // like 9 شهریور
    public String getPersianMonthDay() {
        if (NekoConfig.displayPersianCalendarByLatin.Bool()) {
            return this.getShDay() + " " + this.monthNamesLatin();
        } else {
            return LanguageUtils.getPersianNumbers(String.valueOf(this.getShDay())) + " " + this.monthName();
        }
    }

    /**
     * init with Grg data
     *
     * @param year   Year in Grg
     * @param month  Month in Grg
     * @param day    day in Grg
     * @param hour   hour
     * @param minute min
     * @param second second
     */
    public void initGrgDate(int year, int month, int day, int hour, int minute, int second) {
        this.grgYear = year;
        this.grgMonth = month;
        this.grgDay = day;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.setGrgYear(year)
                .setGrgMonth(month)
                .setGrgDay(day)
                .setHour(hour)
                .setMinute(minute)
                .setSecond(second);
        int[] convert = this.toJalali(year, month, day);
        this.shYear = convert[0];
        this.shMonth = convert[1];
        this.shDay = convert[2];
        this.setShYear(convert[0])
                .setShMonth(convert[1])
                .setShDay(convert[2]);
    }

    /**
     * Helper function for initialize jalali date
     *
     * @param year  Year
     * @param month Month
     * @param day   Day
     */
    private void prepareDate2(int year, int month, int day) {
        int[] convert = this.toGregorian(year, month, day);
        this.grgYear = convert[0];
        this.grgMonth = convert[1];
        this.setGrgDay(convert[2]);
    }

    /**
     * Helper function for initialize
     */
    private void prepareDate() {
        String dtStart = this.textNumberFilter("" + this.getGrgYear()) + "-" + this
                .textNumberFilter("" + this.getGrgMonth()) + "-" + this
                .textNumberFilter("" + this.getGrgDay())
                + "T" + this.textNumberFilter("" + this.getHour()) + ":" + this
                .textNumberFilter("" + this.getMinute()) + ":" + this
                .textNumberFilter("" + this.getSecond()) + "Z";
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        int[] convert = this.toJalali(this.getGrgYear(), this.getGrgMonth(), this.getGrgDay());
        this.shYear = convert[0];
        this.shMonth = convert[1];
        this.shDay = convert[2];
        Date date;
        try {
            date = format.parse(dtStart);
            this.timeInMilliSecond = date != null ? date.getTime() : 0;
        } catch (ParseException e) {
            FileLog.e(e);
        }
    }

    /**
     * return time in long value
     *
     * @return Value of time in mile
     */
    public Long getTime() {
        return this.timeInMilliSecond;
    }

    /**
     * Check Grg year is leap
     *
     * @param Year Year
     * @return boolean
     */
    public boolean grgIsLeap(int Year) {
        return ((Year % 4) == 0 && ((Year % 100) != 0 || (Year % 400) == 0));
    }

    /**
     * Check year in Leap
     *
     * @return true or false
     */
    public boolean isLeap() {
        return this.isLeap(this.shYear);
    }

    /**
     * Check custom year is leap
     *
     * @param year int year
     * @return true or false
     */
    public boolean isLeap(int year) {
        double referenceYear = 1375;
        double startYear = 1375;
        double yearRes = year - referenceYear;
        if (yearRes > 0) {
            if (yearRes >= 33) {
                double numb = yearRes / 33;
                startYear = referenceYear + Math.floor(numb) * 33;
            }
        } else {
            if (yearRes >= -33) {
                startYear = referenceYear - 33;
            } else {
                double numb = Math.abs(yearRes / 33);
                startYear = referenceYear - (Math.floor(numb) + 1) * 33;
            }
        }
        double[] leapYears = {startYear, startYear + 4, startYear + 8, startYear + 16, startYear + 20,
                startYear + 24, startYear + 28, startYear + 33};
        return (Arrays.binarySearch(leapYears, year)) >= 0;
    }

    /**
     * Convert Grg date to jalali date
     *
     * @param year year in Grg date
     * @param month month in Grg date
     * @param day day in Grg date
     * @return a int[year][month][day] in jalali date
     */
    public int[] toJalali(int year, int month, int day) {
        int hshDay = 1;
        int hshMonth = 1;
        int hshElapsed;
        int hshYear = year - 621;
        boolean grgLeap = this.grgIsLeap(year);
        boolean hshLeap = this.isLeap(hshYear - 1);
        int grgElapsed = grgSumOfDays[(grgLeap ? 1 : 0)][month - 1] + day;
        int XmasToNorooz = (hshLeap && grgLeap) ? 80 : 79;
        if (grgElapsed <= XmasToNorooz) {
            hshElapsed = grgElapsed + 286;
            hshYear--;
            if (hshLeap && !grgLeap) {
                hshElapsed++;
            }
        } else {
            hshElapsed = grgElapsed - XmasToNorooz;
            hshLeap = this.isLeap(hshYear);
        }
        if (year >= 2029 && (year - 2029) % 4 == 0) {
            hshElapsed++;
        }
        for (int i = 1; i <= 12; i++) {
            if (hshSumOfDays[(hshLeap ? 1 : 0)][i] >= hshElapsed) {
                hshMonth = i;
                hshDay = hshElapsed - hshSumOfDays[(hshLeap ? 1 : 0)][i - 1];
                break;
            }
        }
        return new int[]{hshYear, hshMonth, hshDay};
    }

    /**
     * Convert Jalali date to Grg
     *
     * @param year Year in jalali
     * @param month Month in Jalali
     * @param day Day in Jalali
     * @return int[year][month][day]
     */
    public int[] toGregorian(int year, int month, int day) {
        int grgYear = year + 621;
        int grgDay = 0;
        int grgMonth = 0;
        int grgElapsed;

        boolean hshLeap = this.isLeap(year);
        boolean grgLeap = this.grgIsLeap(grgYear);

        int hshElapsed = hshSumOfDays[hshLeap ? 1 : 0][month - 1] + day;

        if (month > 10 || (month == 10 && hshElapsed > 286 + (grgLeap ? 1 : 0))) {
            grgElapsed = hshElapsed - (286 + (grgLeap ? 1 : 0));
            grgLeap = grgIsLeap(++grgYear);
        } else {
            hshLeap = this.isLeap(year - 1);
            grgElapsed = hshElapsed + 79 + (hshLeap ? 1 : 0) - (grgIsLeap(grgYear - 1) ? 1 : 0);
        }
        if (grgYear >= 2030 && (grgYear - 2030) % 4 == 0) {
            grgElapsed--;
        }
        if (grgYear == 1989) {
            grgElapsed++;
        }
        for (int i = 1; i <= 12; i++) {
            if (grgSumOfDays[grgLeap ? 1 : 0][i] >= grgElapsed) {
                grgMonth = i;
                grgDay = grgElapsed - grgSumOfDays[grgLeap ? 1 : 0][i - 1];
                break;
            }
        }
        return new int[]{grgYear, grgMonth, grgDay};
    }

    /**
     * calc day of week
     *
     * @return int
     */
    public int dayOfWeek() {
        return this.dayOfWeek(this);
    }

    /**
     * Get day of week from PersianDate object
     *
     * @param date persianDate
     * @return int
     */
    public int dayOfWeek(PersianDate date) {
        return this.dayOfWeek(date.toDate());
    }

    /**
     * Get day of week from Date object
     *
     * @param date Date
     * @return int
     */
    public int dayOfWeek(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
            return 0;
        }
        return (cal.get(Calendar.DAY_OF_WEEK));
    }

    /**
     * return month name
     *
     * @return string
     */
    public String monthName(Dialect dialect) {
        return monthName(this.getShMonth(), dialect);
    }

    /**
     * Return month name
     *
     * @param month Month
     */
    public String monthName(int month, Dialect dialect) {
        return switch (dialect) {
            case AFGHAN -> this.AfghanMonthNames[month - 1];
            case KURDISH -> this.KurdishMonthNames[month - 1];
            case PASHTO -> this.PashtoMonthNames[month - 1];
            case LATIN -> this.monthNamesLatin[month - 1];
            default -> this.monthNames[month - 1];
        };
    }

    /**
     * Get current month name in Persian
     */
    public String monthName() {
        return monthName(Dialect.IRANIAN);
    }

    /**
     * Get month name in Afghan
     */
    public String AfghanMonthName(int month) {
        return this.AfghanMonthNames[month - 1];
    }

    /**
     * Get current date Afghan month name
     */
    public String AfghanMonthName() {
        return this.AfghanMonthName(this.getShMonth());
    }

    /**
     * Get month name in Kurdish
     */
    public String KurdishMonthName(int month) {
        return this.KurdishMonthNames[month - 1];
    }

    /**
     * Get current date Kurdish month name
     */
    public String KurdishMonthName() {
        return this.KurdishMonthName(this.getShMonth());
    }

    /**
     * Get month name in Pashto
     */
    public String PashtoMonthName(int month) {
        return this.PashtoMonthNames[month - 1];
    }

    /**
     * Get current date Pashto month name
     */
    public String PashtoMonthName() {
        return this.PashtoMonthName(this.getShMonth());
    }


    /**
     * Get month name in monthNamesLatin
     */
    public String monthNamesLatin(int month) {
        return this.monthNamesLatin[month - 1];
    }

    /**
     * Get current date monthNamesLatin month name
     */
    public String monthNamesLatin() {
        return this.monthNamesLatin(this.getShMonth());
    }


    /**
     * get day name
     */
    public String dayName() {
        return this.dayName(this);
    }

    /**
     * Get Day Name
     */
    public String dayName(PersianDate date) {
        return this.dayNames[this.dayOfWeek(date)];
    }

    /**
     * Number days of month
     *
     * @return return days
     */
    public int getMonthDays() {
        return this.getMonthDays(this.getShYear(), this.getShMonth());
    }

    /**
     * calc count of day in month
     */
    public int getMonthDays(int Year, int month) {
        if (month == 12 && !this.isLeap(Year)) {
            return 29;
        }
        if (month <= 6) {
            return 31;
        } else {
            return 30;
        }
    }

    /**
     * calculate day in year
     */
    public int getDayInYear() {
        return this.getDayInYear(this.getShMonth(), getShDay());
    }

    /**
     * Calc day of the year
     *
     * @param month Month
     * @param day Day
     */
    public int getDayInYear(int month, int day) {
        for (int i = 1; i < month; i++) {
            if (i <= 6) {
                day += 31;
            } else {
                day += 30;
            }
        }
        return day;
    }

    /**
     * add date
     *
     * @param year   Number of Year you want add
     * @param month  Number of month you want add
     * @param day    Number of day you want add
     * @param hour   Number of hour you want add
     * @param minute Number of minute you want add
     * @param second Number of second you want add
     */
    public void addDate(long year, long month, long day, long hour, long minute, long second) {
        if (month >= 12) {
            year += month / 12;
            month = month % 12;
        }
        for (long i = (year - 1); i >= 0; i--) {
            if (this.isLeap(this.getShYear() + (int) i)) {
                day += 366;
            } else {
                day += 365;
            }
        }
        for (long i = (month - 1); i >= 0; i--) {
            int monthTmp = this.getShMonth() + (int) i;
            int yearTmp = this.getShYear();
            if (monthTmp > 12) {
                monthTmp -= 12;
                yearTmp++;
            }
            day += this.getMonthLength(yearTmp, monthTmp);
        }
        this.timeInMilliSecond += (day * 24 * 3_600 * 1_000);
        this.timeInMilliSecond += ((second + (hour * 3600) + (minute * 60)) * 1_000);
        this.changeTime();
    }

    public void addDay(long day) {
        this.addDate(0, 0, day, 0, 0, 0);
    }

    /**
     * Compare 2 date
     *
     * @param dateInput PersianDate type
     */
    public Boolean after(PersianDate dateInput) {
        return (this.timeInMilliSecond < dateInput.getTime());
    }

    /**
     * compare to data
     *
     * @param dateInput Input
     */
    public Boolean before(PersianDate dateInput) {
        return (!this.after(dateInput));
    }

    /**
     * Check date equals
     */
    public Boolean equals(PersianDate dateInput) {
        return (this.timeInMilliSecond.equals(dateInput.getTime()));
    }

    /**
     * compare two data
     *
     * @return 0 = equal,1=data1 > anotherDate,-1=data1 > anotherDate
     */
    public int compareTo(PersianDate anotherDate) {
        return (this.timeInMilliSecond.compareTo(anotherDate.getTime()));
    }

    @NonNull
    @Override
    public String toString() {
        return PersianDateFormat.format(this, null);
    }
    /*----- Helper Function-----*/

    /**
     * convert PersianDate class to date
     */
    public Date toDate() {
        return new Date(this.timeInMilliSecond);
    }

    /**
     * Helper function
     */
    private String textNumberFilter(String date) {
        if (date.length() < 2) {
            return "0" + date;
        }
        return date;
    }

    /**
     * initialize with time in millisecond
     */
    private void changeTime() {
        this.initGrgDate(Integer.parseInt(new SimpleDateFormat("yyyy").format(this.timeInMilliSecond)),
                Integer.parseInt(new SimpleDateFormat("MM").format(this.timeInMilliSecond)),
                Integer.parseInt(new SimpleDateFormat("dd").format(this.timeInMilliSecond)),
                Integer.parseInt(new SimpleDateFormat("HH").format(this.timeInMilliSecond)),
                Integer.parseInt(new SimpleDateFormat("mm").format(this.timeInMilliSecond)),
                Integer.parseInt(new SimpleDateFormat("ss").format(this.timeInMilliSecond)));
    }

    /**
     * Return today
     */
    public static PersianDate today() {
        PersianDate persianDate = new PersianDate();
        persianDate.setHour(0).setMinute(0).setSecond(0);
        return persianDate;
    }

    /**
     * Get tomorrow
     */
    public static PersianDate tomorrow() {
        PersianDate persianDate = new PersianDate();
        persianDate.addDay(1);
        persianDate.setHour(0).setMinute(0).setSecond(0);
        return persianDate;
    }

    /**
     * Check is midNight
     */
    public Boolean isMidNight() {
        return (this.hour < 12);
    }

    /**
     * Get short name time of the day
     */
    public String getShortTimeOfTheDay() {
        return (this.isMidNight()) ? AM_SHORT_NAME : PM_SHORT_NAME;
    }

    /**
     * Get time of the day
     */
    public String getTimeOfTheDay() {
        return (this.isMidNight()) ? AM_NAME : PM_NAME;
    }

    /**
     * Get number of days in month
     *
     * @param year Jalali year
     * @param month Jalali month
     * @return number of days in month
     */
    public Integer getMonthLength(Integer year, Integer month) {
        if (month <= 6) {
            return 31;
        } else if (month <= 11) {
            return 30;
        } else {
            if (this.isLeap(year)) {
                return 30;
            } else {
                return 29;
            }
        }
    }
}
