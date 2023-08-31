package io.tapdata.connector.csv;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OffStandardFilter {

    //    private static String patternStr = "\"([^\"\t]+)\t*(\"\"([^\"]+)\"\")*\"";
    private final String lineExpression;

    public OffStandardFilter(String lineExpression) {
        this.lineExpression = lineExpression;
    }

    public String[] filter(String lineStr) {
        try {
            List<String> list = new ArrayList<>();
            Pattern pattern = Pattern.compile(lineExpression);
            Matcher matcher = pattern.matcher(lineStr);
            while (matcher.find()) {
                list.add(matcher.group(1));
            }
            return list.toArray(new String[0]);
        } catch (Exception e) {
            return null;
        }
    }

//    public static void main(String[] args) {
//        String s = "\"ADDR_ID\t\"\"ALL_FULL_ADDR\"\"\t\"\"APPLYNO_QC\"\"\t\"\"APPLYNO_QR\"\"\t\"\"BLOOD_NO\"\"\t\"\"BLOODTYPE\"\"\t\"\"BLOODTYPE_BZ\"\"\t\"\"BLOODTYPE_BZ_CN\"\"\t\"\"BLOODTYPE_CN\"\"\t\"\"BORN_CARD_NO\"\"\t\"\"CZRKHHNEW\"\"\t\"\"CZRKJWHDM\"\"\t\"\"CZRKJWHMC\"\"\t\"\"CZRKPCSDM\"\"\t\"\"CZRKPCSJGDM\"\"\t\"\"CZRKPCSMC\"\"\t\"\"CZRKSSSSXQ\"\"\t\"\"CZRKXJGAJGJGDM\"\"\t\"\"CZRKXJGAJGMC\"\"\t\"\"CZRKXZJDDM\"\"\t\"\"CZRKXZJDMC\"\"\t\"\"CZRKZRQDM\"\"\t\"\"CZRKZRQMC\"\"\t\"\"CZRKZZJLXDM\"\"\t\"\"CZRKZZJLXMC\"\"\t\"\"CZRKZZMLPH\"\"\t\"\"CZRKZZMLPXZ\"\"\t\"\"DATE_OF_DEATH\"\"\t\"\"DEATH_NO\"\"\t\"\"DOB\"\"\t\"\"DOB_TIME\"\"\t\"\"DSBM\"\"\t\"\"DY_KIND\"\"\t\"\"EDU_DEGREE\"\"\t\"\"EDU_DEGREE_BZ\"\"\t\"\"EDU_DEGREE_BZ_CN\"\"\t\"\"EDU_DEGREE_CN\"\"\t\"\"ENLISTED\"\"\t\"\"ENLISTED_CN\"\"\t\"\"FA_CARD_NO\"\"\t\"\"FA_CARD_TYPE\"\"\t\"\"FA_NAME\"\"\t\"\"FA_PERSON_ID\"\"\t\"\"FA_PID\"\"\t\"\"FA_WWM\"\"\t\"\"FA_WWX\"\"\t\"\"FROM_ADDR\"\"\t\"\"FROM_COUNTRY_ID\"\"\t\"\"FROM_ORG_ID\"\"\t\"\"FROM_PLACE_ID\"\"\t\"\"FROM_REGION_ID\"\"\t\"\"GENDER\"\"\t\"\"GENDER_BZ\"\"\t\"\"GENDER_BZ_CN\"\"\t\"\"GENDER_CN\"\"\t\"\"GURARDIAN_1\"\"\t\"\"GURARDIAN_1_CARD_NO\"\"\t\"\"GURARDIAN_1_CARD_TYPE\"\"\t\"\"GURARDIAN_1_ID\"\"\t\"\"GURARDIAN_1_PID\"\"\t\"\"GURARDIAN_1_TEL\"\"\t\"\"GURARDIAN_1_WWM\"\"\t\"\"GURARDIAN_1_WWX\"\"\t\"\"GURARDIAN_2\"\"\t\"\"GURARDIAN_2_CARD_NO\"\"\t\"\"GURARDIAN_2_CARD_TYPE\"\"\t\"\"GURARDIAN_2_ID\"\"\t\"\"GURARDIAN_2_PID\"\"\t\"\"GURARDIAN_2_TEL\"\"\t\"\"GURARDIAN_2_WWM\"\"\t\"\"GURARDIAN_2_WWX\"\"\t\"\"HEIGHT\"\"\t\"\"HK_KIND\"\"\t\"\"HU_ADDR\"\"\t\"\"HU_ID\"\"\t\"\"HU_ID_NEW\"\"\t\"\"HU_KIND_CODE\"\"\t\"\"HU_MASTER_ID\"\"\t\"\"HU_MASTER_NAME\"\"\t\"\"HU_MASTER_PID\"\"\t\"\"HYLB\"\"\t\"\"IN_APP_NAME\"\"\t\"\"IN_APP_PID\"\"\t\"\"IN_APP_RELATION\"\"\t\"\"IN_APP_TEL\"\"\t\"\"IN_CATEGORY\"\"\t\"\"IN_CATEGORY_CODE\"\"\t\"\"INCITY_BDYY\"\"\t\"\"INCITY_BDYY_CODE\"\"\t\"\"INCITY_CITY\"\"\t\"\"INCITY_COUNTRY\"\"\t\"\"INCITY_DATE\"\"\t\"\"INCITY_DETAIL\"\"\t\"\"IN_KIND_CODE\"\"\t\"\"IN_QY_NO\"\"\t\"\"IN_SCOPE_CODE\"\"\t\"\"INTOWN_BDYY\"\"\t\"\"INTOWN_BDYY_CODE\"\"\t\"\"IN_ZQ_NO\"\"\t\"\"JJQX_FLAG\"\"\t\"\"LW_GXSJ\"\"\t\"\"LW_ZJBM\"\"\t\"\"JNDJ_FLAG\"\"\t\"\"JOB\"\"\t\"\"JOB_TYPE\"\"\t\"\"JYJZWN_FLAG\"\"\t\"\"LH_KIND\"\"\t\"\"LWGXTIME01\"\"\t\"\"LWGXTIME02\"\"\t\"\"MA_CARD_NO\"\"\t\"\"MA_CARD_TYPE\"\"\t\"\"MA_NAME\"\"\t\"\"MA_PERSON_ID\"\"\t\"\"MA_PID\"\"\t\"\"MARRIAGE\"\"\t\"\"MARRIAGE_BZ\"\"\t\"\"MARRIAGE_BZ_CN\"\"\t\"\"MARRIAGE_CN\"\"\t\"\"MASTER_RELATION\"\"\t\"\"MASTER_RELATION_CN\"\"\t\"\"MA_WWM\"\"\t\"\"MA_WWX\"\"\t\"\"META_ADDR_ID\"\"\t\"\"NAME\"\"\t\"\"NAME_M\"\"\t\"\"NAME_PY\"\"\t\"\"NAME_X\"\"\t\"\"NATAL_CARD_DATE\"\"\t\"\"NATAL_COUNTRY\"\"\t\"\"NATAL_PLACE\"\"\t\"\"NATAL_XIANG\"\"\t\"\"NATION\"\"\t\"\"NATION_BZ\"\"\t\"\"NATION_BZ_CN\"\"\t\"\"NATION_CN\"\"\t\"\"NATIVE_COUNTRY\"\"\t\"\"NATIVE_COUNTRY_BZ\"\"\t\"\"NATIVE_COUNTRY_BZ_CN\"\"\t\"\"NATIVE_COUNTRY_CN\"\"\t\"\"NATIVE_PLACE\"\"\t\"\"NATIVE_PLACE_BZ\"\"\t\"\"NATIVE_PLACE_BZ_CN\"\"\t\"\"NATIVE_PLACE_CN\"\"\t\"\"NATIVE_XIANG\"\"\t\"\"NCJDZZYXBYS_FLAG\"\"\t\"\"NID\"\"\t\"\"NOW_ADDR\"\"\t\"\"NOW_ADDR_ID\"\"\t\"\"NOW_SIGN\"\"\t\"\"OLD_PID_END_DATE\"\"\t\"\"OLD_PID_ISSUE_DATE\"\"\t\"\"OLD_PID_ISSUE_UNIT_NAME\"\"\t\"\"OLD_PID_USEFUL_LIFE\"\"\t\"\"ORG_ID\"\"\t\"\"OUT_APP_NAME\"\"\t\"\"OUT_APP_PID\"\"\t\"\"OUT_APP_RELATION\"\"\t\"\"OUT_APP_TEL\"\"\t\"\"OUT_CATEGORY\"\"\t\"\"OUT_CATEGORY_CODE\"\"\t\"\"OUT_KIND_CODE\"\"\t\"\"OUT_QY_NO\"\"\t\"\"OUT_SCOPE_CODE\"\"\t\"\"OUT_ZQ_NO\"\"\t\"\"PCARD_ISSUE_DATE\"\"\t\"\"PCARD_ISSUE_UNIT_NAME\"\"\t\"\"PENDING_CARD\"\"\t\"\"PENDING_CARD_NO\"\"\t\"\"PENDING_CAUSE\"\"\t\"\"PENDING_KIND\"\"\t\"\"PERSON_ID\"\"\t\"\"PHOTO_ID\"\"\t\"\"PID\"\"\t\"\"PID_ADDR\"\"\t\"\"PID_APPLY_REASON\"\"\t\"\"PID_END_DATE\"\"\t\"\"PID_INFO\"\"\t\"\"PID_ISSUE_DATE\"\"\t\"\"PID_ISSUE_UNIT_NAME\"\"\t\"\"PID_REC_DATE\"\"\t\"\"PID_REC_REASON\"\"\t\"\"PID_USEFUL_LIFE\"\"\t\"\"PO_CARD_NO\"\"\t\"\"PO_CARD_TYPE\"\"\t\"\"PO_NAME\"\"\t\"\"PO_PERSON_ID\"\"\t\"\"PO_PID\"\"\t\"\"PO_WWM\"\"\t\"\"PO_WWX\"\"\t\"\"PRE_DY_KIND\"\"\t\"\"PRE_HK_KIND\"\"\t\"\"PRE_HU_ID\"\"\t\"\"PRE_LH_KIND\"\"\t\"\"PRE_PERSON_ID\"\"\t\"\"QUXCUN_ID\"\"\t\"\"RELIGION\"\"\t\"\"RELIGION_CN\"\"\t\"\"RHFL\"\"\t\"\"SCOL1\"\"\t\"\"SCOL2\"\"\t\"\"SCOL3\"\"\t\"\"SCOL4\"\"\t\"\"SCOL5\"\"\t\"\"SCOL6\"\"\t\"\"SCOL7\"\"\t\"\"SCOL8\"\"\t\"\"SCONTACT\"\"\t\"\"SECURITY_LEVEL\"\"\t\"\"SERVICE_FOR\"\"\t\"\"SERVICE_FOR_ID\"\"\t\"\"SMEMO\"\"\t\"\"SOTHER_ADDR\"\"\t\"\"SOTHER_ADDR_ID\"\"\t\"\"SY_CARD_NO\"\"\t\"\"TO_ADDR\"\"\t\"\"TO_COUNTRY_ID\"\"\t\"\"TO_DY_KIND\"\"\t\"\"TO_ORG_ID\"\"\t\"\"TO_PLACE_ID\"\"\t\"\"TO_REGION_ID\"\"\t\"\"USED_NAME\"\"\t\"\"USED_NAME_ALL\"\"\t\"\"WARDSHIP_1\"\"\t\"\"WARDSHIP_2\"\"\t\"\"WHEN_IN\"\"\t\"\"WHEN_IN_LOGGED\"\"\t\"\"WHEN_LOGGED\"\"\t\"\"WHEN_OPERATED\"\"\t\"\"WHEN_OUT\"\"\t\"\"WHEN_OUT_LOGGED\"\"\t\"\"WHO_IN_LOGGED\"\"\t\"\"WHO_IN_LOGGED_NAME\"\"\t\"\"WHO_IN_UNIT\"\"\t\"\"WHO_IN_UNIT_NAME\"\"\t\"\"WHO_OPERATED\"\"\t\"\"WHO_OUT_LOGGED\"\"\t\"\"WHO_OUT_LOGGED_NAME\"\"\t\"\"WHO_OUT_UNIT\"\"\t\"\"WHO_OUT_UNIT_NAME\"\"\t\"\"WHO_UNIT_OPERATED\"\"\t\"\"YWCZ\"\"\t\"\"YWDM\"\"\t\"\"YWID\"\"\t\"\"ZRCUN_ID\"\"\t\"\"ZXBZ\"\"\t\"\"ZXLB\"\"\t\"\"ZYJSZC_FLAG\"\"\t\"\"ZZZK\"\"\t\"\"LW_RKSJ\"\"\t\"\"LW_SCBZ\"\"\t\"\"LW_SCSJ\"\"\t\"\"LW_CWDM\"\"\t\"\"YSCBJ\"\"\t\"\"SJGSDDM\"\"\t\"\"SJGSDMC\"\"\t\"\"SJLYBMDM\"\"\t\"\"SJLYBMMC\"\"\t\"\"MY_CREATE_TIME\"\"\t\"\"MY_ID_HASH\"\"\t\"\"MY_VALUE_MD5\"\"\t\"\"MY_UPDATE_MD5\"\"\"";
//        String s2 = "\"******************\t\"\"福建省闽清县梅城镇\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"0\"\"\t\"\"0\"\"\t\"\"不明\"\"\t\"\"不明\"\"\t\"\"null\"\"\t\"\"*******\"\"\t\"\"*******\"\"\t\"\"梅城镇城北委\"\"\t\"\"**\"\"\t\"\"*******\"\"\t\"\"****8*派出所\"\"\t\"\"*******\"\"\t\"\"*******\"\"\t\"\"闽清县公安局\"\"\t\"\"*******\"\"\t\"\"梅城镇\"\"\t\"\"*******\"\"\t\"\"城北警务网格\"\"\t\"\"*******\"\"\t\"\"梅城镇上龙洲\"\"\t\"\"32号\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"1954-07-24 00:00:00\"\"\t\"\"000000\"\"\t\"\"*******\"\"\t\"\"null\"\"\t\"\"80\"\"\t\"\"80\"\"\t\"\"小学教育\"\"\t\"\"小学\"\"\t\"\"0\"\"\t\"\"未服兵役\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"已故\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"上莲上莲村\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"2\"\"\t\"\"2\"\"\t\"\"女\"\"\t\"\"女\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"157\"\"\t\"\"自理口粮\"\"\t\"\"null\"\"\t\"\"*******\"\"\t\"\"452001212\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"久居\"\"\t\"\"000\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"10\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"2022-08-27 00:56:55\"\"\t\"\"d46fd68aebff496c9a1b8c72e29c2e63\"\"\t\"\"null\"\"\t\"\"没有职业\"\"\t\"\"975\"\"\t\"\"null\"\"\t\"\"常口\"\"\t\"\"2018-08-18 11:43:52\"\"\t\"\"2022-08-14 12:56:32\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"已故\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"20\"\"\t\"\"null\"\"\t\"\"已婚\"\"\t\"\"已婚\"\"\t\"\"02\"\"\t\"\"户主\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"张巧花\"\"\t\"\"巧花\"\"\t\"\"ZaN^;qiao;hua^;\"\"\t\"\"张\"\"\t\"\"null\"\"\t\"\"156\"\"\t\"\"*******\"\"\t\"\"null\"\"\t\"\"01\"\"\t\"\"01\"\"\t\"\"汉\"\"\t\"\"汉\"\"\t\"\"156\"\"\t\"\"CHN\"\"\t\"\"中国\"\"\t\"\"中国\"\"\t\"\"*******\"\"\t\"\"*******\"\"\t\"\"福建省****\"\"\t\"\"福建省****\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"*******\"\"\t\"\"\"\"\t\"\"null\"\"\t\"\"0\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"*******\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"征地\"\"\t\"\"361\"\"\t\"\"221\"\"\t\"\"null\"\"\t\"\"00\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"*******\"\"\t\"\"null\"\"\t\"\"\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"常口\"\"\t\"\"null\"\"\t\"\"*******\"\"\t\"\"00\"\"\t\"\"无宗教信仰\"\"\t\"\"1\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"19\"\"\t\"\"没有服务处所\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"*******\"\"\t\"\"*******\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"1954-07-24 06:00:00\"\"\t\"\"1999-06-09 16:29:23\"\"\t\"\"1999-06-09 15:11:14\"\"\t\"\"2022-07-23 00:51:07\"\"\t\"\"2001-08-28 12:46:25\"\"\t\"\"2001-08-28 12:46:25\"\"\t\"\"*******\"\"\t\"\"数据转换\"\"\t\"\"*******\"\"\t\"\"闽清县公安局\"\"\t\"\"null\"\"\t\"\"*******\"\"\t\"\"梅城\"\"\t\"\"*******\"\"\t\"\"**********派出所\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"1\"\"\t\"\"null\"\"\t\"\"null\"\"\t\"\"0\"\"\t\"\"2022-08-27 00:56:55\"\"\t\"\"0\"\"\t\"\"null\"\"\t\"\"0\"\"\t\"\"null\"\"\t\"\"*******\"\"\t\"\"福建省厅\"\"\t\"\"*******\"\"\t\"\"福建省*********总队\"\"\t\"\"2022-09-01 10:40:06\"\"\t\"\"140aa1019ec8a63b82c18d4a8a41a910\"\"\t\"\"8391eb746e53152ee54e4495abcf21ad\"\"\t\"\"813f2cdc7988c81d4d1c1978b20d3613\"\"\"";
//        Pattern p = Pattern.compile(patternStr);
//        Matcher m = p.matcher(s);
//
//        while(m.find()) {
//            System.out.println(m.group(1));
//        }
//    }
}