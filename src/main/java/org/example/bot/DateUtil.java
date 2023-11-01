package org.example.bot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;

public class DateUtil {
    public static Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, days);
        return cal.getTime();
    }

    public static Date addMinutes(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.MINUTE, days);
        return cal.getTime();
    }

    public static boolean isWithinTradingHours(LocalTime currentTime) {

        if (LocalDate.now().getDayOfWeek() == DayOfWeek.SATURDAY || LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }

        LocalTime tradingStart = LocalTime.of(2, 0);
        LocalTime tradingEnd = LocalTime.of(20, 0);

        return !currentTime.isBefore(tradingStart) && !currentTime.isAfter(tradingEnd);
    }
}