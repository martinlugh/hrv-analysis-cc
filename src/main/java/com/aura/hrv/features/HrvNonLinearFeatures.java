package com.aura.hrv.features;

import com.aura.hrv.util.MathUtils;

import java.util.*;

/**
 * HRV 非线性特征提取，对应 Python 的以下函数：
 * <ul>
 *   <li>{@link #getPoincarePlotFeatures} - Poincaré 散点图特征（SD1, SD2, ratio）</li>
 *   <li>{@link #getCsiCviFeatures} - 心脏交感/副交感指数（CSI, CVI, Modified_csi）</li>
 *   <li>{@link #getSampen} - 样本熵（Sample Entropy）</li>
 * </ul>
 */
public class HrvNonLinearFeatures {

    // ==================== get_poincare_plot_features ====================

    /**
     * 计算 Poincaré 散点图特征（对应 Python get_poincare_plot_features）。
     *
     * <p>公式：
     * <pre>
     *   diff_nn = diff(nn_intervals)
     *   sd1 = sqrt(nanstd(diff_nn, ddof=1)^2 * 0.5)
     *   sd2 = sqrt(2 * nanstd(nn_intervals, ddof=1)^2 - 0.5 * nanstd(diff_nn, ddof=1)^2)
     *   ratio_sd2_sd1 = sd2 / sd1
     * </pre>
     *
     * @param nn_intervals NN 间期列表（ms）
     * @return 包含 sd1, sd2, ratio_sd2_sd1 的 Map
     */
    public static Map<String, Double> getPoincarePlotFeatures(List<Double> nn_intervals) {
        double[] diff_nn = MathUtils.diff(nn_intervals);

        // sd1：差值序列标准差（ddof=1）乘以 sqrt(0.5)
        double stdDiff = MathUtils.nanStd(MathUtils.toList(diff_nn), 1);
        double sd1 = Math.sqrt(stdDiff * stdDiff * 0.5);

        // sd2：基于 NN 序列整体标准差（ddof=1）
        double stdNn = MathUtils.nanStd(nn_intervals, 1);
        double sd2Val = 2.0 * stdNn * stdNn - 0.5 * stdDiff * stdDiff;
        // 保护性处理：避免负数导致 NaN（理论上不应出现）
        double sd2 = sd2Val >= 0 ? Math.sqrt(sd2Val) : Double.NaN;

        double ratio_sd2_sd1 = sd2 / sd1;

        Map<String, Double> features = new LinkedHashMap<>();
        features.put("sd1",           sd1);
        features.put("sd2",           sd2);
        features.put("ratio_sd2_sd1", ratio_sd2_sd1);

        return features;
    }

    // ==================== get_csi_cvi_features ====================

    /**
     * 计算 CSI/CVI 非线性特征（对应 Python get_csi_cvi_features）。
     *
     * <p>公式：
     * <pre>
     *   T = 4 * sd1（Poincaré 云横轴宽度）
     *   L = 4 * sd2（Poincaré 云纵轴长度）
     *   csi = L / T
     *   cvi = log10(L * T)
     *   Modified_csi = L^2 / T
     * </pre>
     *
     * @param nn_intervals NN 间期列表（ms）
     * @return 包含 csi, cvi, Modified_csi 的 Map
     */
    public static Map<String, Double> getCsiCviFeatures(List<Double> nn_intervals) {
        Map<String, Double> poincare = getPoincarePlotFeatures(nn_intervals);
        double sd1 = poincare.get("sd1");
        double sd2 = poincare.get("sd2");

        double T = 4.0 * sd1;
        double L = 4.0 * sd2;

        double csi          = L / T;
        double cvi          = Math.log10(L * T);
        double modified_csi = (L * L) / T;

        Map<String, Double> features = new LinkedHashMap<>();
        features.put("csi",          csi);
        features.put("cvi",          cvi);
        features.put("Modified_csi", modified_csi);

        return features;
    }

    // ==================== get_sampen ====================

    /**
     * 计算 NN 间期的样本熵（对应 Python nolds.sampen(nn_intervals, emb_dim=2)）。
     *
     * <p>Sample Entropy（SampEn）算法（Richman &amp; Moorman, 2000）：
     * <ol>
     *   <li>容差 r = 0.2 * std(nn_intervals)（nolds 默认）</li>
     *   <li>嵌入维数 m = 2（emb_dim=2，对应 nolds 参数）</li>
     *   <li>统计 m 维和 m+1 维模板匹配数 A 和 B</li>
     *   <li>SampEn = -ln(A / B)</li>
     * </ol>
     *
     * <p><b>与 nolds 的对齐策略</b>：
     * <ul>
     *   <li>nolds 使用 r = tolerance * std(x)，默认 tolerance=None 时自动设为 0.2 * std(x)</li>
     *   <li>模板匹配使用切比雪夫距离（max norm），与 nolds 一致</li>
     *   <li>排除自匹配（i != j），与 nolds 一致</li>
     *   <li>NaN 过滤：先过滤 NaN，再计算标准差和匹配，与 nolds 行为一致</li>
     * </ul>
     *
     * @param nn_intervals NN 间期列表（ms）
     * @return 包含 sampen 键的 Map
     */
    public static Map<String, Double> getSampen(List<Double> nn_intervals) {
        return getSampen(nn_intervals, 2);
    }

    /**
     * 计算样本熵，支持指定嵌入维数。
     *
     * @param nn_intervals NN 间期列表
     * @param emb_dim      嵌入维数（默认 2，对应 nolds.sampen 参数）
     * @return 包含 sampen 键的 Map
     */
    public static Map<String, Double> getSampen(List<Double> nn_intervals, int emb_dim) {
        // 过滤 NaN，与 nolds 对 NaN 的处理一致
        double[] x = nn_intervals.stream()
                .filter(v -> v != null && !Double.isNaN(v))
                .mapToDouble(Double::doubleValue)
                .toArray();

        int n = x.length;
        if (n < emb_dim + 2) {
            Map<String, Double> r = new LinkedHashMap<>();
            r.put("sampen", Double.NaN);
            return r;
        }

        // 容差 r = 0.2 * std(x, ddof=1)，对应 nolds 默认行为
        double std = MathUtils.nanStd(MathUtils.toList(x), 1);
        // nolds 中实际使用 std(x) 即 ddof=0，但其实现等价为 tolerance * std
        // 精确对齐：nolds.sampen 的 tolerance 参数最终乘以 std(x, ddof=0)
        double stdDdof0 = MathUtils.nanStd(MathUtils.toList(x), 0);
        double r = 0.2 * stdDdof0;

        // 计算 m 维和 m+1 维模板匹配数
        // B = 模板长度为 m 的匹配对数
        // A = 模板长度为 m+1 的匹配对数
        long B = 0; // m 维匹配
        long A = 0; // m+1 维匹配

        int m = emb_dim;

        for (int i = 0; i < n - m; i++) {
            for (int j = i + 1; j < n - m; j++) {
                // 检查 m 维切比雪夫距离是否 <= r
                boolean matchM = chebyshevMatch(x, i, j, m, r);
                if (matchM) {
                    B++;
                    // 检查 m+1 维是否也匹配
                    if (Math.abs(x[i + m] - x[j + m]) <= r) {
                        A++;
                    }
                }
            }
        }

        double sampen;
        if (B == 0) {
            // 无匹配时返回 0 或 NaN（与 nolds 行为：返回 0.0）
            sampen = 0.0;
        } else {
            sampen = -Math.log((double) A / B);
        }

        Map<String, Double> features = new LinkedHashMap<>();
        features.put("sampen", sampen);
        return features;
    }

    /**
     * 检查两个 m 维模板向量之间的切比雪夫距离是否 &lt;= r。
     * 切比雪夫距离 = max(|x[i+k] - x[j+k]|) for k in [0, m)
     *
     * @param x 信号数组
     * @param i 模板 1 起始索引
     * @param j 模板 2 起始索引
     * @param m 模板维数
     * @param r 容差
     * @return true 若切比雪夫距离 &lt;= r
     */
    private static boolean chebyshevMatch(double[] x, int i, int j, int m, double r) {
        for (int k = 0; k < m; k++) {
            if (Math.abs(x[i + k] - x[j + k]) > r) {
                return false;
            }
        }
        return true;
    }
}
