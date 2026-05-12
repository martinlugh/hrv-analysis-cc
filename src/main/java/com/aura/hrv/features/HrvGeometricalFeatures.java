package com.aura.hrv.features;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HRV 几何特征提取，对应 Python 的 get_geometrical_features 函数。
 *
 * <p>实现 triangular_index（三角指数），tinn 暂未实现（与 Python 保持一致，返回 null）。
 */
public class HrvGeometricalFeatures {

    /**
     * 计算 HRV 几何特征（对应 Python get_geometrical_features）。
     *
     * <p>triangular_index 算法：
     * <ol>
     *   <li>建立 bins=range(300, 2000, 8) 的直方图（对应 NumPy histogram bins 参数为 range 对象）</li>
     *   <li>triangular_index = len(nn_intervals) / max(histogram_counts)</li>
     * </ol>
     *
     * <p>注：Python 中 numpy.histogram(nn_intervals, bins=range(300, 2000, 8))
     * 使用边界 [300, 308, 316, ..., 1992, 2000]，共 212 个 bin（213 个边界）。
     * 每个 bin 区间为 [left, right)，最后一个 bin 为 [1992, 2000]（闭区间）。
     *
     * @param nn_intervals NN 间期列表（ms）
     * @return 包含 triangular_index 和 tinn 的 Map
     */
    public static Map<String, Object> getGeometricalFeatures(List<Double> nn_intervals) {
        // 构建直方图：bins = range(300, 2000, 8)，即 [300, 308, ..., 1992, 2000]
        // 共 (2000 - 300) / 8 = 212 个 bin
        int binStart = 300;
        int binEnd   = 2000;
        int binStep  = 8;
        int numBins  = (binEnd - binStart) / binStep; // 212

        int[] histCounts = new int[numBins];

        for (Double nn : nn_intervals) {
            if (nn == null || Double.isNaN(nn)) continue;
            // 对应 numpy.histogram 的分 bin 逻辑：
            // bin_index = floor((nn - binStart) / binStep)
            // 对最后一个 bin，numpy 包含右边界
            if (nn < binStart || nn > binEnd) continue;
            if (nn == binEnd) {
                // 最后一个 bin 包含右边界
                histCounts[numBins - 1]++;
            } else {
                int idx = (int) Math.floor((nn - binStart) / binStep);
                if (idx >= 0 && idx < numBins) {
                    histCounts[idx]++;
                }
            }
        }

        // 找直方图最大值
        int maxCount = 0;
        for (int c : histCounts) {
            if (c > maxCount) maxCount = c;
        }

        // triangular_index = len(nn_intervals) / max(histogram)
        double triangular_index = (double) nn_intervals.size() / maxCount;

        // tinn：尚未实现（与 Python 保持一致）
        Object tinn = null;

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("triangular_index", triangular_index);
        features.put("tinn", tinn);

        return features;
    }
}
