package com.aura.hrv.util;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HRV 数学工具类，提供与 NumPy nan-aware 函数对齐的统计方法。
 * 所有方法在计算时自动忽略 Double.NaN，对应 Python 的 nanmean / nanstd / nanmedian 等。
 */
public class MathUtils {

    /**
     * 计算列表中非 NaN 元素的均值（对应 numpy.nanmean）。
     *
     * @param values 输入列表
     * @return 均值，若全为 NaN 则返回 NaN
     */
    public static double nanMean(List<Double> values) {
        double sum = 0.0;
        int count = 0;
        for (Double v : values) {
            if (v != null && !Double.isNaN(v)) {
                sum += v;
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    /**
     * 计算数组中非 NaN 元素的均值。
     *
     * @param values 输入数组
     * @return 均值
     */
    public static double nanMean(double[] values) {
        double sum = 0.0;
        int count = 0;
        for (double v : values) {
            if (!Double.isNaN(v)) {
                sum += v;
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    /**
     * 计算列表中非 NaN 元素的标准差（ddof=0，有偏估计，对应 numpy.nanstd 默认行为）。
     *
     * @param values 输入列表
     * @return 标准差
     */
    public static double nanStd(List<Double> values) {
        return nanStd(values, 0);
    }

    /**
     * 计算列表中非 NaN 元素的标准差，支持指定自由度修正量 ddof。
     * ddof=0：有偏估计（除以 n）；ddof=1：无偏估计（除以 n-1）。
     * 对应 numpy.nanstd(x, ddof=ddof)。
     *
     * @param values 输入列表
     * @param ddof   自由度修正量
     * @return 标准差
     */
    public static double nanStd(List<Double> values, int ddof) {
        double mean = nanMean(values);
        if (Double.isNaN(mean)) return Double.NaN;
        double sumSq = 0.0;
        int count = 0;
        for (Double v : values) {
            if (v != null && !Double.isNaN(v)) {
                double diff = v - mean;
                sumSq += diff * diff;
                count++;
            }
        }
        if (count - ddof <= 0) return Double.NaN;
        return Math.sqrt(sumSq / (count - ddof));
    }

    /**
     * 计算数组中非 NaN 元素的标准差，支持 ddof 参数。
     *
     * @param values 输入数组
     * @param ddof   自由度修正量
     * @return 标准差
     */
    public static double nanStd(double[] values, int ddof) {
        double mean = nanMean(values);
        if (Double.isNaN(mean)) return Double.NaN;
        double sumSq = 0.0;
        int count = 0;
        for (double v : values) {
            if (!Double.isNaN(v)) {
                double diff = v - mean;
                sumSq += diff * diff;
                count++;
            }
        }
        if (count - ddof <= 0) return Double.NaN;
        return Math.sqrt(sumSq / (count - ddof));
    }

    /**
     * 计算列表中非 NaN 元素的中位数（对应 numpy.nanmedian）。
     *
     * @param values 输入列表
     * @return 中位数
     */
    public static double nanMedian(List<Double> values) {
        double[] arr = values.stream()
                .filter(v -> v != null && !Double.isNaN(v))
                .mapToDouble(Double::doubleValue)
                .sorted()
                .toArray();
        if (arr.length == 0) return Double.NaN;
        int mid = arr.length / 2;
        // 偶数长度取两中间值均值，与 numpy 一致
        return arr.length % 2 == 0 ? (arr[mid - 1] + arr[mid]) / 2.0 : arr[mid];
    }

    /**
     * 计算列表中非 NaN 元素的最大值（对应 numpy.nanmax）。
     *
     * @param values 输入列表
     * @return 最大值
     */
    public static double nanMax(List<Double> values) {
        return values.stream()
                .filter(v -> v != null && !Double.isNaN(v))
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(Double.NaN);
    }

    /**
     * 计算列表中非 NaN 元素的最小值（对应 numpy.nanmin）。
     *
     * @param values 输入列表
     * @return 最小值
     */
    public static double nanMin(List<Double> values) {
        return values.stream()
                .filter(v -> v != null && !Double.isNaN(v))
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(Double.NaN);
    }

    /**
     * 计算满足条件（绝对值 > threshold）的非 NaN 元素数量（对应 numpy.nansum(abs > threshold)）。
     *
     * @param values    输入数组
     * @param threshold 阈值
     * @return 满足条件的元素数量
     */
    public static int countAbsGreaterThan(double[] values, double threshold) {
        int count = 0;
        for (double v : values) {
            if (!Double.isNaN(v) && Math.abs(v) > threshold) {
                count++;
            }
        }
        return count;
    }

    /**
     * 计算相邻元素差值数组（对应 numpy.diff(x)）。
     * diff[i] = values[i+1] - values[i]，NaN 参与运算仍保留 NaN。
     *
     * @param values 输入列表
     * @return 差值数组，长度比输入小 1
     */
    public static double[] diff(List<Double> values) {
        double[] result = new double[values.size() - 1];
        for (int i = 0; i < result.length; i++) {
            Double a = values.get(i);
            Double b = values.get(i + 1);
            if (a == null || b == null || Double.isNaN(a) || Double.isNaN(b)) {
                result[i] = Double.NaN;
            } else {
                result[i] = b - a;
            }
        }
        return result;
    }

    /**
     * 将 List<Double> 转换为 double 数组，null 值转为 NaN。
     *
     * @param list 输入列表
     * @return double 数组
     */
    public static double[] toArray(List<Double> list) {
        double[] arr = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Double v = list.get(i);
            arr[i] = (v == null) ? Double.NaN : v;
        }
        return arr;
    }

    /**
     * 将 double 数组转为 List<Double>。
     *
     * @param arr 输入数组
     * @return 列表
     */
    public static List<Double> toList(double[] arr) {
        List<Double> list = new java.util.ArrayList<>(arr.length);
        for (double v : arr) {
            list.add(v);
        }
        return list;
    }

    /**
     * 使用复合梯形法则数值积分（对应 numpy.trapz / numpy.trapezoid）。
     * trapz(y, x) = sum( (y[i] + y[i+1]) / 2 * (x[i+1] - x[i]) )
     *
     * @param y y 坐标数组
     * @param x x 坐标数组
     * @return 积分值
     */
    public static double trapz(double[] y, double[] x) {
        if (y.length != x.length || y.length < 2) return 0.0;
        double result = 0.0;
        for (int i = 0; i < y.length - 1; i++) {
            result += (y[i] + y[i + 1]) / 2.0 * (x[i + 1] - x[i]);
        }
        return result;
    }

    /**
     * 计算数组的均值（不处理 NaN，用于内部已清洁数据）。
     *
     * @param values 输入数组
     * @return 均值
     */
    public static double mean(double[] values) {
        if (values.length == 0) return Double.NaN;
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    /**
     * 检查列表中指定索引的值是否为 NaN。
     *
     * @param list 列表
     * @param idx  索引
     * @return true 若为 NaN 或 null
     */
    public static boolean isNaN(List<Double> list, int idx) {
        Double v = list.get(idx);
        return v == null || Double.isNaN(v);
    }
}
