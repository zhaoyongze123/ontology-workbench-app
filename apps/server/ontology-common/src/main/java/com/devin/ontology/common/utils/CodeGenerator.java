package com.devin.ontology.common.utils;

/**
 * 字符串随机生成.
 *
 * @author joey
 */
public class CodeGenerator {

    /**
     * 创建指定数量的随机字符串.
     *
     * @param numberFlag 是否是数字
     * @param length 长度
     * @return 指定数量的随机字符串
     */
    public static String random(final int length, final boolean numberFlag) {
        StringBuilder retStr;
        String strTable = numberFlag ? "1234567890" : "1234567890abcdefghijkmnpqrstuvwxyz";
        int len = strTable.length();
        boolean bDone = true;
        do {
            retStr = new StringBuilder();
            int count = 0;
            for (int i = 0; i < length; i++) {
                double dblR = Math.random() * len;
                int intR = (int) Math.floor(dblR);
                char c = strTable.charAt(intR);
                if ('0' <= c && c <= '9') {
                    count++;
                }
                retStr.append(strTable.charAt(intR));
            }
            if (count >= 2) {
                bDone = false;
            }
        } while (bDone);
        return retStr.toString();
    }

}


