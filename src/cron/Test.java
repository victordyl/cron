package cron;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;

/**
 * 测试
 *
 * @author 邓应来
 */
public class Test {
    public static void main(String[] args) throws ParseException {
        for (int i = 0; i < 1000; i++) {
            if ((i*100  % 4) == 0){
                System.out.println(i*100);
            }
        }
    }
}
