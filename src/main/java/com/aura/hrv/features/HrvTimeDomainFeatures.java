package com.aura.hrv.features;

import com.aura.hrv.util.MathUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HRV 时域特征提取，对应 Python 的 get_time_domain_features 函数。
 *
 * <p>所有统计量均使用 nan-aware 方法，与 Python numpy.nan* 函数对齐。
 */
public class HrvTimeDomainFeatures {

    /**
     * 计算 HRV 时域特征（对应 Python get_time_domain_features）。
     *
     * <p>输出 Map 的 key 与 Python 完全一致：
     * mean_nni, sdnn, sdsd, nni_50, pnni_50, nni_20, pnni_20, rmssd,
     * median_nni, range_nni, cvsd, cvnni, mean_hr, max_hr, min_hr, std_hr。
     *
     * @param nn_intervals    NN 间期列表（ms）
     * @param pnni_as_percent 是否按"百分比修正"计算 pnni：
     *                        true  => length_int = len - 1（Python 默认）
     *                        false => length_int = len
     * @return 包含 16 个时域特征的 Map
     */
    public static Map<String, Double> getTimeDomainFeatures(List<Double> nn_intervals,
                                                             boolean pnni_as_percent) {
        // 计算相邻 NN 差值：diff_nni[i] = nn[i+1] - nn[i]
        double[] diff_nni = MathUtils.diff(nn_intervals);

        // 分母：pnni_as_percent=true 时用 len-1，否则用 len（与 Python 逻辑一致）
        int length_int = pnni_as_percent ? nn_intervals.size() - 1 : nn_intervals.size();

        // ---- 基本统计量 ----
        double mean_nni    = MathUtils.nanMean(nn_intervals);
        double median_nni  = MathUtils.nanMedian(nn_intervals);
        double range_nni   = MathUtils.nanMax(nn_intervals) - MathUtils.nanMin(nn_intervals);

        // sdsd：相邻差值的标准差（ddof=0，对应 Python numpy.nanstd 默认）
        double sdsd = MathUtils.nanStd(MathUtils.toList(diff_nni), 0);

        // rmssd：相邻差值平方均值的平方根（对应 Python sqrt(nanmean(diff^2))）
        double sumSq = 0.0;
        int countValid = 0;
        for (double d : diff_nni) {
            if (!Double.isNaN(d)) {
                sumSq += d * d;
                countValid++;
            }
        }
        double rmssd = countValid > 0 ? Math.sqrt(sumSq / countValid) : Double.NaN;

        // nni_50 / pnni_50：|diff| > 50ms 的计数和百分比
        int nni_50 = MathUtils.countAbsGreaterThan(diff_nni, 50);
        double pnni_50 = 100.0 * nni_50 / length_int;

        // nni_20 / pnni_20：|diff| > 20ms 的计数和百分比
        int nni_20 = MathUtils.countAbsGreaterThan(diff_nni, 20);
        double pnni_20 = 100.0 * nni_20 / length_int;

        // cvsd：rmssd / mean_nni（变异系数）
        double cvsd = rmssd / mean_nni;

        // sdnn：NN 间期标准差（ddof=1，无偏估计，对应 Python numpy.nanstd(nn, ddof=1)）
        double sdnn = MathUtils.nanStd(nn_intervals, 1);

        // cvnni：sdnn / mean_nni
        double cvnni = sdnn / mean_nni;

        // ---- 心率等效特征 ----
        // heart_rate_list = 60000 / nn_intervals（逐元素）
        List<Double> heart_rate_list = new ArrayList<>(nn_intervals.size());
        for (Double nn : nn_intervals) {
            if (nn == null || Double.isNaN(nn)) {
                heart_rate_list.add(Double.NaN);
            } else {
                heart_rate_list.add(60000.0 / nn);
            }
        }
        double mean_hr = MathUtils.nanMean(heart_rate_list);
        double min_hr  = MathUtils.nanMin(heart_rate_list);
        double max_hr  = MathUtils.nanMax(heart_rate_list);
        // std_hr：ddof=0，与 Python numpy.nanstd 默认一致
        double std_hr  = MathUtils.nanStd(heart_rate_list, 0);

        // ---- 构建结果 Map（key 必须与 Python 完全一致）----
        Map<String, Double> features = new LinkedHashMap<>();
        features.put("mean_nni",   mean_nni);
        features.put("sdnn",       sdnn);
        features.put("sdsd",       sdsd);
        features.put("nni_50",     (double) nni_50);
        features.put("pnni_50",    pnni_50);
        features.put("nni_20",     (double) nni_20);
        features.put("pnni_20",    pnni_20);
        features.put("rmssd",      rmssd);
        features.put("median_nni", median_nni);
        features.put("range_nni",  range_nni);
        features.put("cvsd",       cvsd);
        features.put("cvnni",      cvnni);
        features.put("mean_hr",    mean_hr);
        features.put("max_hr",     max_hr);
        features.put("min_hr",     min_hr);
        features.put("std_hr",     std_hr);

        return features;
    }

    /**
     * 使用默认参数（pnni_as_percent=true）调用时域特征提取。
     *
     * @param nn_intervals NN 间期列表
     * @return 时域特征 Map
     */
    public static Map<String, Double> getTimeDomainFeatures(List<Double> nn_intervals) {
        return getTimeDomainFeatures(nn_intervals, true);
    }
}
