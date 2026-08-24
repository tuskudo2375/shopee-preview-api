package titus.vietclockwidget;

import java.util.Calendar;

/** Vietnamese lunar date conversion based on the astronomical new-moon method. */
final class LunarCalendar {
    private static final double TIME_ZONE = 7.0;
    private static final double SYNODIC_MONTH = 29.530588853;
    private static final double PI = Math.PI;

    private LunarCalendar() {
    }

    static LunarDate fromSolar(Calendar solar) {
        int day = solar.get(Calendar.DAY_OF_MONTH);
        int month = solar.get(Calendar.MONTH) + 1;
        int year = solar.get(Calendar.YEAR);
        return convertSolarToLunar(day, month, year, TIME_ZONE);
    }

    static String shortLabel(Calendar solar) {
        LunarDate lunar = fromSolar(solar);
        String dayLabel = lunar.day <= 10 ? "Mùng " + lunar.day : String.valueOf(lunar.day);
        String leap = lunar.leap ? " nhuận" : "";
        return "Âm lịch: " + dayLabel + " tháng " + lunar.month + leap;
    }

    private static LunarDate convertSolarToLunar(int day, int month, int year, double timeZone) {
        long dayNumber = jdFromDate(day, month, year);
        int k = (int) Math.floor((dayNumber - 2415021.076998695) / SYNODIC_MONTH);
        int monthStart = getNewMoonDay(k + 1, timeZone);
        if (monthStart > dayNumber) {
            monthStart = getNewMoonDay(k, timeZone);
        }

        int a11 = getLunarMonth11(year, timeZone);
        int b11 = a11;
        int lunarYear;
        if (a11 >= monthStart) {
            lunarYear = year;
            a11 = getLunarMonth11(year - 1, timeZone);
        } else {
            lunarYear = year + 1;
            b11 = getLunarMonth11(year + 1, timeZone);
        }

        int lunarDay = (int) (dayNumber - monthStart + 1);
        int diff = (int) Math.floor((monthStart - a11) / 29.0);
        int lunarMonth = diff + 11;
        boolean lunarLeap = false;

        if (b11 - a11 > 365) {
            int leapMonthDiff = getLeapMonthOffset(a11, timeZone);
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10;
                if (diff == leapMonthDiff) {
                    lunarLeap = true;
                }
            }
        }

        if (lunarMonth > 12) {
            lunarMonth -= 12;
        }
        if (lunarMonth >= 11 && diff < 4) {
            lunarYear--;
        }
        return new LunarDate(lunarDay, lunarMonth, lunarYear, lunarLeap);
    }

    private static long jdFromDate(int day, int month, int year) {
        int a = (14 - month) / 12;
        int y = year + 4800 - a;
        int m = month + 12 * a - 3;
        return day + (153L * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045;
    }

    private static double newMoon(int k) {
        double time = k / 1236.85;
        double time2 = time * time;
        double time3 = time2 * time;
        double dr = PI / 180.0;
        double jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * time2
                - 0.000000155 * time3;
        jd1 += 0.00033 * Math.sin((166.56 + 132.87 * time - 0.009173 * time2) * dr);

        double solarMeanAnomaly = 359.2242 + 29.10535670 * k - 0.0000333 * time2
                - 0.00000347 * time3;
        double moonMeanAnomaly = 306.0253 + 385.81691806 * k + 0.0107306 * time2
                + 0.00001236 * time3;
        double moonArgument = 21.2964 + 390.67050646 * k - 0.0016528 * time2
                - 0.00000239 * time3;

        double correction = (0.1734 - 0.000393 * time) * Math.sin(solarMeanAnomaly * dr)
                + 0.0021 * Math.sin(2 * solarMeanAnomaly * dr)
                - 0.4068 * Math.sin(moonMeanAnomaly * dr)
                + 0.0161 * Math.sin(2 * moonMeanAnomaly * dr)
                - 0.0004 * Math.sin(3 * moonMeanAnomaly * dr)
                + 0.0104 * Math.sin(2 * moonArgument * dr)
                - 0.0051 * Math.sin((solarMeanAnomaly + moonMeanAnomaly) * dr)
                - 0.0074 * Math.sin((solarMeanAnomaly - moonMeanAnomaly) * dr)
                + 0.0004 * Math.sin((2 * moonArgument + solarMeanAnomaly) * dr)
                - 0.0004 * Math.sin((2 * moonArgument - solarMeanAnomaly) * dr)
                - 0.0006 * Math.sin((2 * moonArgument + moonMeanAnomaly) * dr)
                + 0.0010 * Math.sin((2 * moonArgument - moonMeanAnomaly) * dr)
                + 0.0005 * Math.sin((2 * moonMeanAnomaly + solarMeanAnomaly) * dr);

        double deltaT;
        if (time < -11) {
            deltaT = 0.001 + 0.000839 * time + 0.0002261 * time2 - 0.00000845 * time3
                    - 0.000000081 * time * time3;
        } else {
            deltaT = -0.000278 + 0.000265 * time + 0.000262 * time2;
        }
        return jd1 + correction - deltaT;
    }

    private static int getNewMoonDay(int k, double timeZone) {
        return (int) Math.floor(newMoon(k) + 0.5 + timeZone / 24.0);
    }

    private static double sunLongitude(double dayNumber) {
        double time = (dayNumber - 2451545.5) / 36525.0;
        double time2 = time * time;
        double dr = PI / 180.0;
        double meanLongitude = 280.46645 + 36000.76983 * time + 0.0003032 * time2;
        double meanAnomaly = 357.52910 + 35999.05030 * time - 0.0001559 * time2
                - 0.00000048 * time * time2;
        double equation = (1.914600 - 0.004817 * time - 0.000014 * time2)
                * Math.sin(dr * meanAnomaly)
                + (0.019993 - 0.000101 * time) * Math.sin(2 * dr * meanAnomaly)
                + 0.000290 * Math.sin(3 * dr * meanAnomaly);
        double longitude = meanLongitude + equation;
        longitude *= dr;
        longitude -= 2 * PI * Math.floor(longitude / (2 * PI));
        return longitude;
    }

    private static int getSunLongitude(int dayNumber, double timeZone) {
        return (int) Math.floor(sunLongitude(dayNumber - 0.5 - timeZone / 24.0) / PI * 6);
    }

    private static int getLunarMonth11(int year, double timeZone) {
        long off = jdFromDate(31, 12, year) - 2415021L;
        int k = (int) Math.floor(off / SYNODIC_MONTH);
        int month = getNewMoonDay(k, timeZone);
        int sunLongitude = getSunLongitude(month, timeZone);
        if (sunLongitude >= 9) {
            month = getNewMoonDay(k - 1, timeZone);
        }
        return month;
    }

    private static int getLeapMonthOffset(int a11, double timeZone) {
        int k = (int) Math.floor(0.5 + (a11 - 2415021.076998695) / SYNODIC_MONTH);
        int last = 0;
        int i = 1;
        int arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone);
        do {
            last = arc;
            i++;
            arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone);
        } while (arc != last && i < 14);
        return i - 1;
    }

    static final class LunarDate {
        final int day;
        final int month;
        final int year;
        final boolean leap;

        LunarDate(int day, int month, int year, boolean leap) {
            this.day = day;
            this.month = month;
            this.year = year;
            this.leap = leap;
        }
    }
}
