package com.fan.lazyday.infrastructure.constants;

/**
 * <p>描述: [类型描述] </p>
 * <p>创建时间: 2024/9/13 </p>
 *
 * @author fanqibu
 * @version v1.0 <br/>
 * 2024/09/13 16:42 fan 创建
 */
@SuppressWarnings("SpellCheckingInspection")
public class OssConstants {
    public static final String ZIP_NAME_SUFFIX = ".zip";
    /**
     * 文件不解压
     */
    public static final String USER_DATA_SUB_FILE_MARK_KEY = "isentry";
    /**
     * revit案例
     */
    public static final String S3_SAMPLES_REVIT_PREFIX = "service/filemanager/samples/revit/";
    /**
     * 模型
     */
    public static final String S3_BIMIN_MODEL_PREFIX = "bimin/service/filemanager/model/";
}
