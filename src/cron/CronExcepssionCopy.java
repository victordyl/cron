package cron;

import java.text.ParseException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.lang.Character.getNumericValue;

/**
 * cron表达式复制
 *
 * @author 邓应来
 */
public class CronExcepssionCopy {

    /**
     * 表达式第1位-秒
     */
    private static final int SECOND = 0;
    /**
     * 表达式第2位-分
     */
    private static final int  MINUTE = 1;
    /**
     * 表达式第3位-时
     */
    private static final int HOUR = 2;
    /**
     * 表达式第4位-日
     */
    private static final int DAY_OF_MONTH = 3;
    /**
     * 表达式第5位-月
     */
    private static final int MONTH = 4;
    /**
     * 表达式第6位-周
     */
    private static final int DAY_OF_WEEK = 5;
    /**
     * 表达式第7位-年
     */
    private static final int YEAR = 6;
    /**
     * 通配符 ？表示不确定得时刻
     */
    protected static final int NO_SPEC_INT = 98;
    /**
     * 通配符 * 表示任意时刻
     */
    protected static final int ALL_SPEC_INT = 99;
    /**
     * 不定时刻
     */
    protected static final Integer NO_SPEC = 98;
    /**
     * 任意时刻
     */
    protected static final Integer ALL_SPEC = 99;


    /**
     * 传入的cron表达式，不可变
     */
    private final String cronExcepssion;
    /**
     * 存放秒的集合
     */
    private TreeSet<Integer> seconds = new TreeSet<>();
    /**
     * 存放分的集合
     */
    private TreeSet<Integer> minutes = new TreeSet<>();
    /**
     * 存放时的集合
     */
    private TreeSet<Integer> hours = new TreeSet<>();
    /**
     * 存放天的集合
     */
    private TreeSet<Integer> dayOfMonths = new TreeSet<>();
    /**
     * 存放月的集合
     */
    private TreeSet<Integer> months = new TreeSet<>();
    /**
     * 存放周的集合
     */
    private TreeSet<Integer> dayOfWeeks = new TreeSet<>();
    /**
     * 存放年的集合
     */
    private TreeSet<Integer> years = new TreeSet<>();
    /**
     * 是否是一周的最后一天
     */
    protected  boolean lastdayOfWeek = false;
    protected  int nthdayOfWeek = 0;
    /**
     * 是否是某鱼的最后一天
     */
    protected  boolean lastdayOfMonth = false;
    /**
     * 从最后一天开始的偏移量
     */
    protected transient int lastdayOffset = 0;
    /**
     * 最近的工作日
     */
    protected boolean nearestWeekday = false;
    /**
     * 最大的年份，当前年+100
     */
    public static final int MAX_YEAR = Calendar.getInstance().get(Calendar.YEAR) + 100;

    /**
     * 时区
     */
    private TimeZone timeZone = null;

    /**
     * 构造函数，必须传入合法的cron表达式
     * @param cronExcepssion
     */
    public CronExcepssionCopy(String cronExcepssion) throws ParseException {
        if (cronExcepssion == null || "".equals(cronExcepssion)){
            throw new IllegalArgumentException("cron表达式不能为空");
        }
        this.cronExcepssion = cronExcepssion;
        //构建表达式对象（包含校验）
        buildCronException(this.cronExcepssion);
    }

    private void buildCronException(String cronExcepssion) throws ParseException {
        //解析cron表达式时，当前指针所在下标，初始在0位
        int index = SECOND ;
        //使用Java标记器解析cron表达式,标记器指定解析空格
        StringTokenizer fToken = new StringTokenizer(cronExcepssion, " ");
        //循环处理一级表达式的值
        while (fToken.hasMoreTokens() && index <= YEAR){
            String fFokenOn = fToken.nextToken();

            //判断每月的最后一天是否正确 错误示例： 3，4，L 3,4L
            if (index == DAY_OF_MONTH && fFokenOn.contains("L") && fFokenOn.length() > 1 ){
                throw new ParseException("不支持L与其他天数一起出现",-1);
            }
            //判断每周的最后一天是否正确
            if (index == DAY_OF_WEEK && fFokenOn.contains("L") && fFokenOn.length() > 1 ){
                throw new ParseException("不支持L与其他天数一起出现",-1);
            }

            //再次解析token ,按逗号解析
            StringTokenizer tToken = new StringTokenizer(fFokenOn, ",");
            while (tToken.hasMoreTokens()){
                String tTokenOn = tToken.nextToken();
                //消费表达式值
                storeException(0, tTokenOn,index);
            }
            //一级表达式移动到下一位
            index ++ ;
        }

        //判断表达式长度
        if (index <= DAY_OF_WEEK){
            throw new ParseException("cron表达式长度不足",-1);
        }

        //处理：如果未传年 ，则消费一个任意年
         if (index == YEAR){
            storeException(0, "*",YEAR);
        }

        //日位置和周位置不能同时精确，必须有一位是？
        //严格cron是这样的，但是不严格的cron，两个都可以没有？，而使用*代替
        boolean dayOfMSpec = dayOfMonths.contains(NO_SPEC);
        boolean dayOfWSpec = dayOfWeeks.contains(NO_SPEC);
        // true false || false true 真  true表示有？ false表示没有
        // true true || false false
        // 1 0 || 0 1
        // 0 0 || 1 1
        if (!(dayOfMSpec ^ dayOfWSpec)){
            throw new ParseException("不支持同时指定几号和星期几两个参数",-1);
        }
    }

    //消费表达式值

    /**
     * 消费表达式值
     *
     * @param pos
     * @param token 表达式的值
     * @param index 表达式处于cron表达式7位中的具体位置 0-6
     * @return
     */
    private int storeException(int pos , String token, int index) throws ParseException {

        //定义循环间隔数值 即 3/5中的5
        int inc = 0;
        //跳过空格
        //理论上，这每一个token值都是一个符号，或者如1-3这种范围数字，不会有空格，这里是处理一下脏数据
        int i = 0;
        for (; i < token.length() && (token.charAt(i) == ' ' || token.charAt(i) == '\t') ; i++) {
        }
        if (i > token.length()){
            return i;
        }

        char c = token.charAt(i);
        // 解析英文月份，暂时不分析英文的
        if ((c >= 'A') && (c <= 'Z') && (!token.equals("L")) && (!token.equals("LW")) && (!token.matches("^L-[0-9]*[W]?"))){
            String sub = token.substring(i, i + 3);
            int sval = -1;
            int eval = -1;
            if (index == MONTH){
                sval = getMonthNumber(sub) + 1;
            }
        }

        //遇到了通配符?
        if (c == '?'){
            i++;
            //todo 这里不应该+1的，否则 ?1这种非法情况是判断不出来的
            if ((i+1) <  token.length() && (token.charAt(i) != ' ' ) && token.charAt(i) != '\t'){
                throw new ParseException("通配符?后面的参数非法：" + token.charAt(i) ,-1);
            }
            // 通配符？如果既不在周也不在日的位置上就会报错
            if (index != DAY_OF_MONTH && index != DAY_OF_WEEK){
                throw new ParseException("通配符?只能出现在日和周的位置上",-1);
            }
            //判断日和周的通配符是否都包含有?
            if (index == DAY_OF_WEEK && !lastdayOfMonth){
                Integer val = dayOfMonths.last();
                if (val == NO_SPEC_INT){
                    throw new ParseException("周和日的通配符不能都是?",-1);
                }
            }
            //将通配符？转成数字存入集合当中
            addToSet(NO_SPEC_INT,-1,0,index);
            return i;

        }

        //遇到了通配符 *或者 /
        if(c == '*' || c == '/'){
            if (c == '*' && (i + 1) >= token.length()){
                addToSet(ALL_SPEC_INT,-1,inc,index);
                return i+1;
            }else if (c == '/' && ((i+1) >= token.length() || token.charAt(i+1) == ' ' || token.charAt(i) == '\t')){
                throw new ParseException("/后面必须跟整数",-1);
            }else if (c == '*'){
                //此处只有 i < token.length()的时候（即*后面还有字符，如*123）才会出现,此时需要跳过这个*
                i++;
            }

            c = token.charAt(i);
            if (c =='/'){
                //跳到后面的字符
                i++;
                if (i >= token.length()){
                    throw new ParseException("/后面必须跟整数",-1);
                }

                //截取/后面的数值
                inc = getNumericValue(token, i);

                i++;
                //如果间隔的时间是两位数，则下标向后再跳一次
                if (inc > 10){
                    i++;
                }
                // 检查循环间隔值是否合法
                checkIncrementRange(inc, index, i);

            }else {
                inc = 1;
            }

            addToSet(ALL_SPEC_INT,-1,inc,index);
            return i;
        }else if (c == 'L'){
            // L表示每月或者每周的最后一天
            i++;
            if (index == DAY_OF_MONTH){
                lastdayOfMonth = true;
            }
            if (index == DAY_OF_WEEK){
                addToSet(7,7,0,index);
            }

            if (index == DAY_OF_MONTH && i < token.length()){
                //这种情况就是L-3这样子，表示从最后一天向开始的第三天区间
                c = token.charAt(i);
                if (c == '-'){
                    //找到字符-后面的连续数字，以及连续数字后一位的下标
                    ValueSet vs = getValue(0, token, i+1);
                   lastdayOffset = vs.value;
                   if (lastdayOffset > 30){
                       throw new ParseException("从最后一天开始的偏移量不不能大于30",i);
                   }
                   i = vs.pos;
                }
                //这种情况就是 LW
                if (token.length() > i){
                    c = token.charAt(i);
                    if (c =='W'){
                        nearestWeekday = true;
                        i++;
                    }
                }
            }
            return i;
        }else if (c >= '0' && c <= '9'){
            //此处表示该位置上符号是0-9的数字
            int val = Integer.parseInt(String.valueOf(c));
            i++;
            if (i >= token.length()){
                addToSet(val,-1,-1,index);
            }else {
                c = token.charAt(i);
                if (c >= '0' && c <= '9') {
                    ValueSet vs = getValue(val, token, i);
                    val = vs.value;
                    i = checkNext(i, token, val, index);
                    return i;
                }
            }
        }else {
            throw new ParseException("Unexpected character: " + c, i);
        }
        return i;
    }

    /**
     * 查看token中的后面的值
     * @param pos
     * @param token
     * @param val
     * @param index
     * @return
     * @throws ParseException
     */
    private int checkNext(int pos, String token, int val, int index) throws ParseException {

        //定义范围数据的右区间
        int end = -1;
        int i = pos;

        if (i >= token.length()) {
            addToSet(val, end, -1, index);
            return i;
        }

        char c = token.charAt(pos);

        // 这里处理的是周位置上面的 3L这种值
        if (c == 'L') {
            if (index == DAY_OF_WEEK) {
                if(val < 1 || val > 7)
                    throw new ParseException("周位置上的数字只能为1-7", -1);
                lastdayOfWeek = true;
            } else {
                throw new ParseException("'L' option is not valid here. (pos=" + i + ")", i);
            }
            TreeSet<Integer> set = getSet(index);
            set.add(val);
            i++;
            return i;
        }

        //这里处理的是日位置上的3W这种值
        if (c == 'W') {
            if (index == DAY_OF_MONTH) {
                nearestWeekday = true;
            } else {
                throw new ParseException("'W' option is not valid here. (pos=" + i + ")", i);
            }
            if(val > 31)
                throw new ParseException("The 'W' option does not make sense with values larger than 31 (max number of days in a month)", i);
            TreeSet<Integer> set = getSet(index);
            set.add(val);
            i++;
            return i;
        }

        //这里处理的是周位置上面的 3#这种值
        if (c == '#') {
            if (index != DAY_OF_WEEK) {
                throw new ParseException("'#' option is not valid here. (pos=" + i + ")", i);
            }
            i++;
            try {
                nthdayOfWeek = Integer.parseInt(token.substring(i));
                if (nthdayOfWeek < 1 || nthdayOfWeek > 5) {
                    throw new Exception();
                }
            } catch (Exception e) {
                throw new ParseException("A numeric value between 1 and 5 must follow the '#' option", i);
            }

            TreeSet<Integer> set = getSet(index);
            set.add(val);
            i++;
            return i;
        }

        //这里就是正常处理 3-4这种区间值了 3-23
        if (c == '-') {
            i++;
            c = token.charAt(i);
            int v = Integer.parseInt(String.valueOf(c));
            end = v;
            i++;
            //这是3-5这种值
            if (i >= token.length()) {
                addToSet(val, end, 1, index);
                return i;
            }
            c = token.charAt(i);
            //这是3-24这种值
            if (c >= '0' && c <= '9') {
                ValueSet vs = getValue(v, token , i);
                end = vs.value;
                i = vs.pos;
            }
            //这是3-24/3这种值，这种cron表达式就有点过于复杂了
            if (i < token.length() && ((c = token.charAt(i)) == '/')) {
                i++;
                c = token.charAt(i);
                int v2 = Integer.parseInt(String.valueOf(c));
                i++;
                if (i >= token.length()) {
                    addToSet(val, end, v2, index);
                    return i;
                }
                c = token.charAt(i);
                //这里表示3-11/12这种两位数结尾的值
                if (c >= '0' && c <= '9') {
                    ValueSet vs = getValue(v2, token, i);
                    int v3 = vs.value;
                    addToSet(val, end, v3, index);
                    i = vs.pos;
                    return i;
                } else {
                    //这里表示3-11/1abc这种字母结尾的脏数据
                    addToSet(val, end, v2, index);
                    return i;
                }
            } else {
                addToSet(val, end, 1, index);
                return i;
            }
        }

        //这里是处理 3/4这种循环值
        if (c == '/') {
            if ((i + 1) >= token.length() || token.charAt(i + 1) == ' ' || token.charAt(i + 1) == '\t') {
                throw new ParseException("'/' 后面必须跟一个整数", i);
            }

            i++;
            c = token.charAt(i);
            int v2 = Integer.parseInt(String.valueOf(c));
            i++;
            if (i >= token.length()) {
                checkIncrementRange(v2, index, i);
                addToSet(val, end, v2, index);
                return i;
            }
            c = token.charAt(i);
            if (c >= '0' && c <= '9') {
                ValueSet vs = getValue(v2, token, i);
                int v3 = vs.value;
                checkIncrementRange(v3, index, i);
                addToSet(val, end, v3, index);
                i = vs.pos;
                return i;
            } else {
                throw new ParseException("Unexpected character '" + c + "' after '/'", i);
            }
        }

        addToSet(val, end, 0, index);
        i++;
        return i;


    }

    /**
     * 根据cron表达式位置获取对应的集合
     * @param index
     * @return
     */
    TreeSet<Integer> getSet(int index) {
        switch (index) {
            case SECOND:
                return seconds;
            case MINUTE:
                return minutes;
            case HOUR:
                return hours;
            case DAY_OF_MONTH:
                return dayOfMonths;
            case MONTH:
                return months;
            case DAY_OF_WEEK:
                return dayOfWeeks;
            case YEAR:
                return years;
            default:
                return null;
        }
    }

    /**
     * 找到字符串token中从第i位开始连续的数字以及后一位下标
     * 如token = 345,i=0,v=0,则value=345,pos(指针)=4,指针移动到空位置
     * 如token = 345wa,i=0,v=0,则value=345,pos(指针)=3，指针移动到第一个非连续数字位置
     * 如token = wa345,i=0,v=0,则value=0,pos(指针)=0，指针在第一个非连续数字位置
     * @param i
     * @param token
     * @param v 给定的默认初始值
     * @return
     */
    private  ValueSet getValue(int v, String token, int i) {
        char c = token.charAt(i);
        StringBuilder s1 = new StringBuilder(String.valueOf(v));
        while (c >= '0' && c <= '9') {
            s1.append(c);
            i++;
            if (i >= token.length()) {
                break;
            }
            c = token.charAt(i);
        }
        ValueSet val = new ValueSet();

        val.pos = (i < token.length()) ? i : i + 1;
        val.value = Integer.parseInt(s1.toString());
        return val;
    }

    /**
     * 检查循环间隔值是否有效
     * @param inc
     * @param index
     * @param i
     */
    private void checkIncrementRange(int inc, int index, int i) throws ParseException {
        if (inc > 59 && (index == SECOND || index == MINUTE)){
            throw new ParseException("秒或者分的值不能大于59",i);
        }else if (inc > 23 && index == HOUR){
            throw new ParseException("小时不能大于23",i);
        }else if (inc > 31 && index == DAY_OF_MONTH){
            throw new ParseException("天数不能大于31",i);
        }else if (inc > 12 && index == MONTH){
            throw new ParseException("月份不能大于12",i);
        }else if (inc > 7 && index == DAY_OF_WEEK){
            throw new ParseException("星期不能大于7",i);
        }

    }

    /**
     * 从指定位置开始截取字符串中的数字直到有结束符号的位置
     * 如35，当前i=0,则返回的是数值35
     * @param token
     * @param i 起始截取位置
     * @return
     */
    private static int getNumericValue(String token, int i) {
        //移动到下一个空白的区域
        int endOfVal = findNextWhiteSpace(token, i);
        String val = token.substring(i, endOfVal);
        return Integer.valueOf(val);
    }


    /**
     * 找到下一次空的区域
     * 如 1 34，当前i=0,则跳到i=1的位置
     * @param token
     * @param i
     * @return
     */
    private static int findNextWhiteSpace(String token,int i){
        for (;i<token.length() && (token.charAt(i) != ' ' || token.charAt(i) != '\t');i++){
        }
        return i;

    }

    /**
     * 添加值到对应的集合中
     * @param val 值（起始值） -1表示最小值
     * @param end 结束值 -1表示最大的值
     * @param incr 增量值 0或者-1表示没有增量值，默认增量是1
     * @param index 集合所在位置
     */
    private void addToSet(int val, int end, int incr, int index) throws ParseException {

        TreeSet<Integer> set = getSet(index);

        //校验各个位置上面的值是否合法
        if (index == SECOND || index == MINUTE) {
            if ((val < 0 || val > 59 || end > 59) && (val != ALL_SPEC_INT)) {
                throw new ParseException("Minute and Second values must be between 0 and 59", -1);
            }
        } else if (index == HOUR) {
            if ((val < 0 || val > 23 || end > 23) && (val != ALL_SPEC_INT)) {
                throw new ParseException("Hour values must be between 0 and 23", -1);
            }
        } else if (index == DAY_OF_MONTH) {
            if ((val < 1 || val > 31 || end > 31) && (val != ALL_SPEC_INT) && (val != NO_SPEC_INT)) {
                throw new ParseException("Day of month values must be between 1 and 31", -1);
            }
        } else if (index == MONTH) {
            if ((val < 1 || val > 12 || end > 12) && (val != ALL_SPEC_INT)) {
                throw new ParseException("Month values must be between 1 and 12", -1);
            }
        } else if (index == DAY_OF_WEEK) {
            if ((val == 0 || val > 7 || end > 7) && (val != ALL_SPEC_INT)
                    && (val != NO_SPEC_INT)) {
                throw new ParseException("Day-of-Week values must be between 1 and 7", -1);
            }
        }

        //判断循环间隔值，0、-1表示没有循环间隔，直接将起始值 val添加到集合当中
        if ((incr == 0 || incr == -1) && val != ALL_SPEC_INT) {
            if (val != -1) {
                set.add(val);
            } else {
                //起始值为-1表示不定时刻 *
                set.add(NO_SPEC);
            }

            return;
        }

        //开始值
        int startAt = val;
        //结束值
        int stopAt = end;

        //只有一个*的情况，表示任意时刻
        if (val == ALL_SPEC_INT && incr <= 0) {
            incr = 1;
            set.add(ALL_SPEC); // put in a marker, but also fill values
        }

        if (index == SECOND || index == MINUTE) {
            if (stopAt == -1) {
                stopAt = 59;
            }
            if (startAt == -1 || startAt == ALL_SPEC_INT) {
                startAt = 0;
            }
        } else if (index == HOUR) {
            if (stopAt == -1) {
                stopAt = 23;
            }
            if (startAt == -1 || startAt == ALL_SPEC_INT) {
                startAt = 0;
            }
        } else if (index == DAY_OF_MONTH) {
            if (stopAt == -1) {
                stopAt = 31;
            }
            if (startAt == -1 || startAt == ALL_SPEC_INT) {
                startAt = 1;
            }
        } else if (index == MONTH) {
            if (stopAt == -1) {
                stopAt = 12;
            }
            if (startAt == -1 || startAt == ALL_SPEC_INT) {
                startAt = 1;
            }
        } else if (index == DAY_OF_WEEK) {
            if (stopAt == -1) {
                stopAt = 7;
            }
            if (startAt == -1 || startAt == ALL_SPEC_INT) {
                startAt = 1;
            }
        } else if (index == YEAR) {
            if (stopAt == -1) {
                stopAt = MAX_YEAR;
            }
            if (startAt == -1 || startAt == ALL_SPEC_INT) {
                startAt = 1970;
            }
        }

        int max = -1;
        // 结束时间小于开始时间 如 24-3号范围，获取到对应位置的最大值
        if (stopAt < startAt) {
            switch (index) {
                case       SECOND : max = 60; break;
                case       MINUTE : max = 60; break;
                case         HOUR : max = 24; break;
                case        MONTH : max = 12; break;
                case  DAY_OF_WEEK : max = 7;  break;
                case DAY_OF_MONTH : max = 31; break;
                case         YEAR : throw new IllegalArgumentException("起始年份必须小于终止年份");
                default           : throw new IllegalArgumentException("意外的类型");
            }
            //即给开始时间增加一个周期的值
            stopAt += max;
        }

        //从开始像结束值便利，增江为 incr
        for (int i = startAt; i <= stopAt; i += incr) {
            if (max == -1) {
                // 没有溢出即开始时间小于结束时间 如 3-24
                set.add(i);
            } else {
                // 有溢出的值，取模
                int i2 = i % max;
                // 以1开始的位置不能为0，如果为0 则实际为最大值
                if (i2 == 0 && (index == MONTH || index == DAY_OF_WEEK || index == DAY_OF_MONTH) ) {
                    i2 = max;
                }

                set.add(i2);
            }
        }

    }

    /**
     * 解析英文月份
     * @param s
     * @return
     */
    private int getMonthNumber(String s) {
        return 0;
    }

    //获取下一个有效日期,改造，使用Java8的新api,计算仅以公历年为基础
    public LocalDateTime getTimeAfter(LocalDateTime afterTime) {
       //当前时间增加1s,留足计算时间,且将纳秒置空不计算秒级以下时间
        LocalDateTime time = afterTime.with(ChronoField.NANO_OF_SECOND, 0).plus(1, ChronoUnit.SECONDS);

        //循环计算，知道计算到了下一次，或者超过了endtime
        boolean gotOne = false;
        while (!gotOne){

            //防止无限循环，如果年份>2999则停止计算
            if(time.getYear() > 2999) {
                return null;
            }

            //获取集合排序元素的临时变量
            SortedSet<Integer> st = null;
            int t = 0;

            int sec = time.getSecond();
            int min = time.getMinute();

            //获取秒
            //获取seconds集合中大于sec的元素
            st = seconds.tailSet(sec);
            if (st != null && st.size() != 0) {
                sec = st.first();
            } else {
                sec = seconds.first();
                //进一位
                min++;
                time = time.with(ChronoField.MINUTE_OF_HOUR,min);
            }
           time = time.with(ChronoField.SECOND_OF_MINUTE,sec);

            int hr = time.getHour();
            t = -1;

            // 获取分钟
            st = minutes.tailSet(min);
            if (st != null && st.size() != 0) {
                t = min;
                min = st.first();
            } else {
                min = minutes.first();
                //进一位
                hr++;
            }
            //前面的秒 已经设置了正确的数值了，感觉不需要再置空，应该是考虑到可能会发生秒溢出了让后将时又进一位的问题
            //注意这里，两个值开始一般不相等，那么就将时间的秒置空，重新设置时间的分为分集合中的第一位，重置时间的时位置，然后跳过本次循环继续参与循环，直到分位置相等为止
            if (min != t) {
                time = time.with(ChronoField.SECOND_OF_MINUTE, 0)
                        .with(ChronoField.MINUTE_OF_HOUR,min)
                        .with(ChronoField.HOUR_OF_DAY,hr);
                continue;
            }
            //循环至知道相等位置
            time = time.with(ChronoField.MINUTE_OF_HOUR, min);

            //hr = cl.get(Calendar.HOUR_OF_DAY);
            int day = time.getDayOfMonth();
            t = -1;

            // 获取时
            st = hours.tailSet(hr);
            if (st != null && st.size() != 0) {
                t = hr;
                hr = st.first();
            } else {
                hr = hours.first();
                day++;
            }
            if (hr != t) {
                time = time.with(ChronoField.SECOND_OF_MINUTE,0)
                        .with(ChronoField.MINUTE_OF_HOUR,0)
                        .with(ChronoField.HOUR_OF_DAY,hr)
                        .with(ChronoField.DAY_OF_MONTH,day);
                //todo 这里要注意夏令时问题？
                continue;
            }
            time = time.with(ChronoField.HOUR_OF_DAY,hr);

           // day = cl.get(Calendar.DAY_OF_MONTH);
            int mon = time.getMonthValue();
           // int mon = cl.get(Calendar.MONTH) + 1;
            // '+ 1' because calendar is 0-based for this field, and we are
            // 1-based
            t = -1;
            int tmon = mon;

            // 获取天
            //判断以下日和周位置上是否有一个？通配符
            boolean dayOfMSpec = !dayOfMonths.contains(NO_SPEC);
            boolean dayOfWSpec = !dayOfWeeks.contains(NO_SPEC);
            // 日无周有？，需要敲定日的数值
            if (dayOfMSpec && !dayOfWSpec) {
                st = dayOfMonths.tailSet(day);
                //是最后一天
                if (lastdayOfMonth) {
                    //非近期工作日
                    if(!nearestWeekday) {
                        t = day;
                        day = getLastDayOfMonth(mon, time.getYear());
                        // 这里只是为了满足一个需求，即要从最后一天像前推移几天
                        day -= lastdayOffset;
                        if(t > day) {
                            // 如果有偏移
                            mon++;
                            if(mon > 12) {
                                mon = 1;
                                tmon = 3333; // ensure test of mon != tmon further below fails
                                time = time.plus(1,ChronoUnit.YEARS);
                            }
                            day = 1;
                        }
                    } else {
                        //这里需要得到最近一个工作日
                        t = day;
                        day = getLastDayOfMonth(mon, time.getYear());
                        day -= lastdayOffset;

                        int ldom = getLastDayOfMonth(mon, time.getYear());
                        int dow = time.getDayOfWeek().getValue();

                        if(dow == DayOfWeek.SATURDAY.getValue() && day == 1) {
                            day += 2;
                        } else if(dow == DayOfWeek.SATURDAY.getValue()) {
                            day -= 1;
                        } else if(dow == DayOfWeek.SUNDAY.getValue() && day == ldom) {
                            day -= 2;
                        } else if(dow == DayOfWeek.SUNDAY.getValue()) {
                            day += 1;
                        }

                        LocalDateTime time2 = LocalDateTime.of(time.getYear(), time.getMonthValue(), day, hr, min, sec);
                        //如果得到的时间在传入的时间之前，则日期和月份需要增加
                        if (time2.isBefore(time)){
                            day = 1;
                            mon++;
                        }
                    }
                } else if(nearestWeekday) {
                    t = day;
                    day = dayOfMonths.first();

                    int ldom = getLastDayOfMonth(mon, time.getYear());
                    int dow = time.getDayOfWeek().getValue();

                    if(dow == DayOfWeek.SATURDAY.getValue() && day == 1) {
                        day += 2;
                    } else if(dow == DayOfWeek.SATURDAY.getValue()) {
                        day -= 1;
                    } else if(dow == DayOfWeek.SUNDAY.getValue() && day == ldom) {
                        day -= 2;
                    } else if(dow == DayOfWeek.SUNDAY.getValue()) {
                        day += 1;
                    }

                    LocalDateTime time2 = LocalDateTime.of(time.getYear(), time.getMonthValue(), day, hr, min, sec);
                    if (time2.isBefore(time)){
                        day = dayOfMonths.first();
                        mon++;
                    }
                } else if (st != null && st.size() != 0) {
                    //
                    //当前的日值在cron表达式之前，即未到时间
                    t = day;
                    day = st.first();
                    // make sure we don't over-run a short month, such as february
                    int lastDay = getLastDayOfMonth(mon, time.getYear());
                    if (day > lastDay) {
                        //日值超过了，进一个月
                        day = dayOfMonths.first();
                        mon++;
                    }
                } else {
                    //当前日值在调度时间之后，过期了，到下一个周期时间
                    day = dayOfMonths.first();
                    mon++;
                }

                // todo 这种方式有极致问题
                if (day != t || mon != tmon) {
                    //这里未得到cron表达式中的值（可能有进位），跳过当前循环再次计算
                    time = LocalDateTime.of(time.getYear(), mon, day, 0, 0, 0);
                    continue;
                }
            } else if (dayOfWSpec && !dayOfMSpec) {
                //周无日有？通配符，根据周确定日
                if (lastdayOfWeek) {
                    int dow = dayOfWeeks.first();
                    int cDow = time.getDayOfWeek().getValue();
                    int daysToAdd = 0;
                    if (cDow < dow) {
                        daysToAdd = dow - cDow;
                    }
                    if (cDow > dow) {
                        daysToAdd = dow + (7 - cDow);
                    }

                    int lDay = getLastDayOfMonth(mon, time.getYear());

                    if (day + daysToAdd > lDay) { // did we already miss the
                        time = LocalDateTime.of(time.getYear(), time.getMonthValue(), 1, 0, 0, 0);
                        continue;
                    }

                    // find date of last occurrence of this day in this month...
                    while ((day + daysToAdd + 7) <= lDay) {
                        daysToAdd += 7;
                    }

                    day += daysToAdd;

                    if (daysToAdd > 0) {
                        time = LocalDateTime.of(time.getYear(), time.getMonthValue(), day, 0, 0, 0);
                        // '- 1' here because we are not promoting the month
                        continue;
                    }

                } else if (nthdayOfWeek != 0) {
                    // are we looking for the Nth XXX day in the month?
                    int dow = dayOfWeeks.first(); // desired
                    // d-o-w
                    int cDow = time.getDayOfWeek().getValue(); // current d-o-w
                    int daysToAdd = 0;
                    if (cDow < dow) {
                        daysToAdd = dow - cDow;
                    } else if (cDow > dow) {
                        daysToAdd = dow + (7 - cDow);
                    }

                    boolean dayShifted = false;
                    if (daysToAdd > 0) {
                        dayShifted = true;
                    }

                    day += daysToAdd;
                    int weekOfMonth = day / 7;
                    if (day % 7 > 0) {
                        weekOfMonth++;
                    }

                    daysToAdd = (nthdayOfWeek - weekOfMonth) * 7;
                    day += daysToAdd;
                    if (daysToAdd < 0 || day > getLastDayOfMonth(mon, time.getYear())) {
                        // no '- 1' here because we are promoting the month
                        time = LocalDateTime.of(time.getYear(), time.getMonthValue(), 1, 0, 0, 0);
                        continue;
                    } else if (daysToAdd > 0 || dayShifted) {
                        time = LocalDateTime.of(time.getYear(), time.getMonthValue(), day, 0, 0, 0);
                        // '- 1' here because we are NOT promoting the month
                        continue;
                    }
                } else {
                    //终于到了正常的环节了
                    int cDow = time.getDayOfWeek().getValue(); // current d-o-w
                    int dow = dayOfWeeks.first(); // desired
                    // d-o-w
                    st = dayOfWeeks.tailSet(cDow);
                    if (st != null && st.size() > 0) {
                        dow = st.first();
                    }

                    int daysToAdd = 0;
                    if (cDow < dow) {
                        daysToAdd = dow - cDow;
                    }
                    if (cDow > dow) {
                        daysToAdd = dow + (7 - cDow);
                    }

                    int lDay = getLastDayOfMonth(mon, time.getYear());

                    if (day + daysToAdd > lDay) {
                        //日值超过了，我们把日志减小再来一次
                        time = LocalDateTime.of(time.getYear(), time.getMonthValue(), 1, 0, 0, 0);
                        // no '- 1' here because we are promoting the month
                        continue;
                    } else if (daysToAdd > 0) { // are we swithing days?
                        //日值没超过，我们把日值增加再来一次，直到增加的天数为0（即日和周完全符合）
                        time = LocalDateTime.of(time.getYear(), time.getMonthValue(), day + daysToAdd, 0, 0, 0);
                        continue;
                    }
                }
            } else { 
                throw new UnsupportedOperationException(
                        "Support for specifying both a day-of-week AND a day-of-month parameter is not implemented.");
            }
            time = time.with(ChronoField.DAY_OF_MONTH,day);
            mon = time.getMonthValue();
            int year = time.getYear();
            t = -1;

            if (year > MAX_YEAR) {
                return null;
            }

            //获取月份
            st = months.tailSet(mon);
            if (st != null && st.size() != 0) {
                t = mon;
                mon = st.first();
            } else {
                mon = months.first();
                year++;
            }
            if (mon != t) {
                time = LocalDateTime.of(year, mon, 1, 0, 0, 0);
                continue;
            }
            time = time.with(ChronoField.MONTH_OF_YEAR,mon);
            year = time.getYear();
            t = -1;

            // 获取年
            st = years.tailSet(year);
            if (st != null && st.size() != 0) {
                t = year;
                year = st.first();
            } else {
                //如果年超过了直接返回空
                return null;
            }

            if (year != t) {
                time = LocalDateTime.of(year, 0, 1, 0, 0, 0);
                continue;
            }
            time = time.with(ChronoField.YEAR,year);

            gotOne = true;

        }
        return time;
    }

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public TimeZone getTimeZone() {
        if(timeZone == null){
            return TimeZone.getDefault();
        }
        return timeZone;
    }

    /**
     * 获取到每月的最后一天
     * @param monthNum
     * @param year
     * @return
     */
    protected int getLastDayOfMonth(int monthNum, int year) {

        switch (monthNum) {
            case 1:
                return 31;
            case 2:
                return (isLeapYear(year)) ? 29 : 28;
            case 3:
                return 31;
            case 4:
                return 30;
            case 5:
                return 31;
            case 6:
                return 30;
            case 7:
                return 31;
            case 8:
                return 31;
            case 9:
                return 30;
            case 10:
                return 31;
            case 11:
                return 30;
            case 12:
                return 31;
            default:
                throw new IllegalArgumentException("Illegal month number: "
                        + monthNum);
        }
    }

    /**
     * 判断闰年
     * 能够被4整除且不是整百的为闰年，是整百且能被400整除的是闰年
     * 能整除4的都是闰年
     * @param year
     * @return
     */
    protected boolean isLeapYear(int year) {
        return ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0));
    }

    public static void main(String[] args) throws ParseException {
        String cron = "1 3 5 31 * ?";
        CronExcepssionCopy cronExcepssion = new CronExcepssionCopy(cron);
        LocalDateTime time = LocalDateTime.now();
        DateTimeFormatter dft = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss");
        for (int i = 0; i < 10; i++) {
            LocalDateTime timeAfter = cronExcepssion.getTimeAfter(time);
            String format = dft.format(timeAfter);
            System.out.println("第" + i +"次：" + format);
            time = timeAfter.plusSeconds(1);
        }


        // 问题1：0/5无法解析
        //问题2：时间总是进一位的问题
    }

    class ValueSet {
        public int value;

        public int pos;
    }
}
