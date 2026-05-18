package com.aura.hrv.preprocessing;

/**
 * 异位心搏剔除规则枚举，对应 Python 中的字符串常量。
 *
 * <ul>
 *   <li>MALIK   - Malik 规则（1994），检查相邻 RR 间期差是否超过前者的 20%</li>
 *   <li>KAMATH  - Kamath &amp; Fallen 规则，非对称判断标准</li>
 *   <li>KARLSSON - Karlsson 规则，与前后均值比较</li>
 *   <li>ACAR    - Acar 规则，与前 9 个均值比较</li>
 *   <li>CUSTOM  - 自定义规则，由 customRemovingRule 参数指定百分比</li>
 * </ul>
 */
public enum EctopicBeatRule {

    /** Malik 规则：|RRi - RRi+1| <= 0.2 * RRi */
    MALIK("malik"),

    /** Kamath 规则：非对称边界 0.325 / 0.245 */
    KAMATH("kamath"),

    /** Karlsson 规则：与前后均值偏差超过阈值则异位 */
    KARLSSON("karlsson"),

    /** Acar 规则：与前 9 个 NN 均值比较 */
    ACAR("acar"),

    /** 自定义规则：|RRi - RRi+1| <= custom_removing_rule * RRi */
    CUSTOM("custom");

    /** 对应 Python 中的字符串名称 */
    private final String methodName;

    EctopicBeatRule(String methodName) {
        this.methodName = methodName;
    }

    /**
     * 获取规则对应的 Python 方法名字符串。
     *
     * @return 方法名字符串
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * 通过方法名字符串查找对应枚举值（大小写不敏感）。
     *
     * @param name 方法名
     * @return 对应的枚举值
     * @throws IllegalArgumentException 若名称不合法
     */
    public static EctopicBeatRule fromString(String name) {
        for (EctopicBeatRule rule : values()) {
            if (rule.methodName.equalsIgnoreCase(name)) {
                return rule;
            }
        }
        throw new IllegalArgumentException(
                "不合法的异位心搏剔除方法：" + name +
                "。请选择 malik、kamath、karlsson、acar 或 custom。");
    }
}
