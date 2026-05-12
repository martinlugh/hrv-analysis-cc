package com.aura.hrv.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 插值工具类，提供对 NaN 值的线性插值，模拟 pandas Series.interpolate() 的默认行为。
 *
 * <p>pandas 默认参数：method="linear", limit_area=None, limit_direction="forward"
 * 线性插值：在两个非 NaN 锚点之间按比例填充中间 NaN 值。
 * 开头连续 NaN 由第一个非 NaN 值向前填充（forward fill）。
 * 结尾连续 NaN 不填充（与 pandas limit_area=None + limit_direction="forward" 一致）。
 */
public class InterpolationUtils {

    /**
     * 对 NaN 值进行线性插值，模拟 pandas Series.interpolate(method="linear") 行为。
     *
     * <p>处理流程：
     * <ol>
     *   <li>若首元素为 NaN，用第一个非 NaN 值填充开头连续 NaN（等价于 Python 中 rr_intervals[0:start_idx] = [rr_intervals[start_idx]] * start_idx）</li>
     *   <li>线性插值填充中间 NaN</li>
     *   <li>结尾 NaN 不处理（与 pandas forward 方向一致）</li>
     * </ol>
     *
     * @param rr_intervals 输入 RR 间期列表（可含 NaN）
     * @return 插值后的列表
     */
    public static List<Double> interpolateNanValues(List<Double> rr_intervals) {
        return interpolateNanValues(rr_intervals, "linear", null, "forward", null);
    }

    /**
     * 对 NaN 值进行插值，支持指定参数（当前仅实现 linear 方法）。
     *
     * @param rr_intervals         输入 RR 间期列表（可含 NaN）
     * @param interpolation_method 插值方法（当前支持 "linear"）
     * @param limit_area           限制区域（null / "inside" / "outside"）
     * @param limit_direction      填充方向（"forward" / "backward" / "both"）
     * @param limit                最大连续填充数量，null 表示不限制
     * @return 插值后的列表
     */
    public static List<Double> interpolateNanValues(List<Double> rr_intervals,
                                                     String interpolation_method,
                                                     String limit_area,
                                                     String limit_direction,
                                                     Integer limit) {
        if (rr_intervals == null || rr_intervals.isEmpty()) {
            return new ArrayList<>(rr_intervals);
        }

        // 复制列表以免修改原数据
        List<Double> result = new ArrayList<>(rr_intervals);

        // 第一步：如果首元素为 NaN，用第一个非 NaN 值填充开头所有 NaN
        // 对应 Python 代码：rr_intervals[0:start_idx] = [rr_intervals[start_idx]] * start_idx
        if (isNaN(result.get(0))) {
            int startIdx = 0;
            while (startIdx < result.size() && isNaN(result.get(startIdx))) {
                startIdx++;
            }
            if (startIdx < result.size()) {
                double fillValue = result.get(startIdx);
                for (int i = 0; i < startIdx; i++) {
                    result.set(i, fillValue);
                }
            }
            // 若全为 NaN，直接返回
            else {
                return result;
            }
        }

        // 第二步：线性插值（处理中间和尾部 NaN）
        // pandas linear 插值：在两个非 NaN 锚点之间线性填充
        // limit_direction="forward" 时，尾部 NaN 不填充（因为没有右侧锚点）
        linearInterpolate(result, limit_area, limit_direction, limit);

        return result;
    }

    /**
     * 线性插值核心逻辑，在两个非 NaN 锚点之间线性填充 NaN。
     *
     * @param result          待插值列表（in-place 修改）
     * @param limit_area      限制区域
     * @param limit_direction 填充方向
     * @param limit           最大连续填充数量
     */
    private static void linearInterpolate(List<Double> result,
                                           String limit_area,
                                           String limit_direction,
                                           Integer limit) {
        int n = result.size();
        int i = 0;
        while (i < n) {
            if (isNaN(result.get(i))) {
                // 找到 NaN 段的起始和结束
                int nanStart = i;
                while (i < n && isNaN(result.get(i))) {
                    i++;
                }
                int nanEnd = i; // nanEnd 是第一个非 NaN 的位置（或 n）

                // 左锚点（nanStart - 1），右锚点（nanEnd）
                boolean hasLeft = nanStart > 0 && !isNaN(result.get(nanStart - 1));
                boolean hasRight = nanEnd < n && !isNaN(result.get(nanEnd));

                // 根据 limit_area 决定是否填充
                boolean shouldFill;
                if ("inside".equals(limit_area)) {
                    shouldFill = hasLeft && hasRight;
                } else if ("outside".equals(limit_area)) {
                    shouldFill = !hasLeft || !hasRight;
                } else {
                    // null：都填充（前提是有右锚点，对应 forward 方向）
                    shouldFill = true;
                }

                if (shouldFill && hasLeft && hasRight) {
                    // 两端均有锚点：线性插值
                    double leftVal = result.get(nanStart - 1);
                    double rightVal = result.get(nanEnd);
                    int gapLen = nanEnd - nanStart;
                    int filled = 0;
                    for (int j = nanStart; j < nanEnd; j++) {
                        if (limit != null && filled >= limit) break;
                        double fraction = (double)(j - nanStart + 1) / (gapLen + 1);
                        result.set(j, leftVal + fraction * (rightVal - leftVal));
                        filled++;
                    }
                } else if (shouldFill && hasLeft && !hasRight) {
                    // 尾部 NaN：limit_direction="forward" 时不填充（pandas 默认行为）
                    // limit_direction="both" 或 "backward" 时才填充
                    if ("both".equals(limit_direction) || "backward".equals(limit_direction)) {
                        // 用左侧值 forward fill（pandas 对尾部 NaN 的 forward fill 行为）
                        // 实际上 pandas linear 对尾部不填，此处保留原值
                    }
                    // "forward" 方向：尾部 NaN 保持 NaN，不处理
                } else if (shouldFill && !hasLeft && hasRight) {
                    // 头部 NaN：已在前置步骤处理，这里通常不会到达
                    double rightVal = result.get(nanEnd);
                    int filled = 0;
                    for (int j = nanStart; j < nanEnd; j++) {
                        if (limit != null && filled >= limit) break;
                        result.set(j, rightVal);
                        filled++;
                    }
                }
                // hasLeft=false && hasRight=false：全 NaN，不处理
            } else {
                i++;
            }
        }
    }

    /**
     * 在均匀采样时间戳序列上对 NN 间期进行线性插值。
     * 对应 Python：scipy.interpolate.interp1d(x=timestamp_list, y=nn_intervals, kind="linear")
     *
     * @param timestamps       原始不均匀时间戳（秒）
     * @param nn_intervals     对应的 NN 间期值
     * @param newTimestamps    目标均匀时间戳序列
     * @return 在 newTimestamps 处插值得到的 NN 间期数组
     */
    public static double[] linearInterp1d(double[] timestamps, double[] nn_intervals,
                                           double[] newTimestamps) {
        double[] result = new double[newTimestamps.length];
        int n = timestamps.length;
        for (int k = 0; k < newTimestamps.length; k++) {
            double t = newTimestamps[k];
            // 找到 t 在 timestamps 中的插入位置
            if (t <= timestamps[0]) {
                result[k] = nn_intervals[0];
                continue;
            }
            if (t >= timestamps[n - 1]) {
                result[k] = nn_intervals[n - 1];
                continue;
            }
            // 二分查找左锚点
            int lo = 0, hi = n - 1;
            while (lo < hi - 1) {
                int mid = (lo + hi) / 2;
                if (timestamps[mid] <= t) lo = mid;
                else hi = mid;
            }
            double t0 = timestamps[lo];
            double t1 = timestamps[hi];
            double y0 = nn_intervals[lo];
            double y1 = nn_intervals[hi];
            result[k] = y0 + (y1 - y0) * (t - t0) / (t1 - t0);
        }
        return result;
    }

    /**
     * 辅助方法：判断一个 Double 是否为 NaN 或 null。
     *
     * @param v 输入值
     * @return true 若为 NaN 或 null
     */
    private static boolean isNaN(Double v) {
        return v == null || Double.isNaN(v);
    }
}
