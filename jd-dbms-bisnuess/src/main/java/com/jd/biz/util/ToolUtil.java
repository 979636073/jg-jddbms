package com.jd.biz.util;

public class ToolUtil {


    /**
     * 自定义排序 先按数字再按字母
     * @param o1 数据
     * @param o2 数据
     * @return
     */
    public static int numOrLetterSort(String o1, String o2) {
        int i = 0; int j = 0;
        while (i < o1.length() && j < o2.length()) {
            char c1 = o1.charAt(i);
            char c2 = o2.charAt(i);
            if (Character.isDigit(c1) && Character.isDigit(c2)) {
                // 比较数字部分
                int num1 = extractNumber(o1, i);
                int num2 = extractNumber(o2, j);
                int numCompare = Integer.compare(num1, num2);
                if (numCompare != 0) {
                    return numCompare;
                }
                // 跳过数字部分
                i += String.valueOf(num1).length();
                j += String.valueOf(num2).length();
            } else if (Character.isDigit(c1)) {
                return 1; // 数字大于字母
            } else if (Character.isDigit(c2)) {
                return -1; //字母小于数字
            } else {
                int compare = Character.compare(c1, c2);
                if (compare != 0) {
                    return compare;
                }
                i++;
                j++;
            }
        }
        return Integer.compare(o1.length(), o2.length());
    }

    public static int extractNumber(String s, int start) {
        StringBuilder stringBuilder = new StringBuilder();
        while (start < s.length() && Character.isDigit(s.charAt(start))) {
            stringBuilder.append(s.charAt(start));
            start++;
        }
        return Integer.parseInt(stringBuilder.toString());
    }
}
