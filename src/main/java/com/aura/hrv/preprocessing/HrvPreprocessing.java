package com.aura.hrv.preprocessing;

import com.aura.hrv.util.InterpolationUtils;
import com.aura.hrv.util.MathUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * HRV 预处理模块，对应 Python 的 hrvanalysis/preprocessing.py。
 *
 * <p>提供以下核心功能：
 * <ul>
 *   <li>{@link #removeOutliers} - 剔除超出生理范围的 RR 间期（替换为 NaN）</li>
 *   <li>{@link #removeEctopicBeats} - 剔除异位心搏（替换为 NaN）</li>
 *   <li>{@link #interpolateNanValues} - 对 NaN 值进行线性插值</li>
 *   <li>{@link #getNnIntervals} - 完整预处理流水线，返回 NN 间期</li>
 *   <li>{@link #isValidSample} - 验证样本质量</li>
 * </ul>
 */
public class HrvPreprocessing {

    // ==================== remove_outliers ====================

    /**
     * 将超出生理范围的 RR 间期替换为 NaN（对应 Python remove_outliers）。
     *
     * <p>保留范围：[low_rri, high_rri]（对应心率 30~200 BPM）。
     * rri=2000ms => 30 BPM；rri=300ms => 200 BPM。
     *
     * @param rr_intervals RR 间期列表（ms）
     * @param verbose      是否打印被剔除的异常值信息
     * @param low_rri      最小合法 RR 间期（ms），默认 300
     * @param high_rri     最大合法 RR 间期（ms），默认 2000
     * @return 剔除异常值后的列表（异常值替换为 NaN）
     */
    public static List<Double> removeOutliers(List<Double> rr_intervals,
                                               boolean verbose,
                                               int low_rri,
                                               int high_rri) {
        List<Double> cleaned = new ArrayList<>(rr_intervals.size());
        List<Double> outlierValues = new ArrayList<>();

        for (Double rri : rr_intervals) {
            // 对应 Python：rri if high_rri >= rri >= low_rri else np.nan
            if (rri != null && !Double.isNaN(rri) && rri >= low_rri && rri <= high_rri) {
                cleaned.add(rri);
            } else {
                cleaned.add(Double.NaN);
                if (verbose && rri != null && !Double.isNaN(rri)) {
                    outlierValues.add(rri);
                }
            }
        }

        if (verbose) {
            long nanCount = cleaned.stream().filter(v -> v == null || Double.isNaN(v)).count();
            if (nanCount == 0) {
                System.out.println(nanCount + " outlier(s) have been deleted.");
            } else {
                System.out.println(nanCount + " outlier(s) have been deleted.");
                System.out.println("The outlier(s) value(s) are : " + outlierValues);
            }
        }

        return cleaned;
    }

    /**
     * 使用默认参数调用 removeOutliers（verbose=true, low_rri=300, high_rri=2000）。
     *
     * @param rr_intervals RR 间期列表
     * @return 清洗后的列表
     */
    public static List<Double> removeOutliers(List<Double> rr_intervals) {
        return removeOutliers(rr_intervals, true, 300, 2000);
    }

    // ==================== remove_ectopic_beats ====================

    /**
     * 剔除异位心搏，将异常 RR 间期替换为 NaN（对应 Python remove_ectopic_beats）。
     *
     * <p>支持以下剔除规则（通过 method 参数选择）：
     * <ul>
     *   <li>malik：|RRi - RRi+1| &lt;= 0.2 * RRi</li>
     *   <li>kamath：非对称判断，0.325 / 0.245 边界</li>
     *   <li>karlsson：与前后均值偏差超过阈值则异位</li>
     *   <li>acar：与前 9 个 NN 均值偏差超过阈值则异位</li>
     *   <li>custom：自定义百分比阈值</li>
     * </ul>
     *
     * @param rr_intervals         RR 间期列表（可含 NaN）
     * @param method               剔除规则名称
     * @param custom_removing_rule 自定义规则百分比（仅 custom 和 karlsson 使用，默认 0.2）
     * @param verbose              是否打印剔除信息
     * @return 剔除异位心搏后的列表（异常值替换为 NaN）
     */
    public static List<Double> removeEctopicBeats(List<Double> rr_intervals,
                                                   String method,
                                                   double custom_removing_rule,
                                                   boolean verbose) {
        EctopicBeatRule rule = EctopicBeatRule.fromString(method);
        List<Double> nn_intervals;
        int outlierCount;

        if (rule == EctopicBeatRule.KARLSSON) {
            // Karlsson 规则：与前后均值比较
            Object[] res = removeOutlierKarlsson(rr_intervals, custom_removing_rule);
            nn_intervals = (List<Double>) res[0];
            outlierCount = (int) res[1];

        } else if (rule == EctopicBeatRule.ACAR) {
            // Acar 规则：与前 9 个 NN 均值比较
            Object[] res = removeOutlierAcar(rr_intervals, custom_removing_rule);
            nn_intervals = (List<Double>) res[0];
            outlierCount = (int) res[1];

        } else {
            // Malik / Kamath / Custom：逐对比较相邻 RR 间期
            nn_intervals = new ArrayList<>();
            outlierCount = 0;
            boolean previousOutlier = false;

            // 固定第一个元素直接保留
            nn_intervals.add(rr_intervals.get(0));

            for (int i = 0; i < rr_intervals.size() - 1; i++) {
                // 若上一个元素被标记为异位，则跳过本次检查，直接保留下一个
                if (previousOutlier) {
                    nn_intervals.add(rr_intervals.get(i + 1));
                    previousOutlier = false;
                    continue;
                }

                double rr = rr_intervals.get(i);
                double nextRr = rr_intervals.get(i + 1);

                if (isRrIntervalWithinBounds(rr, nextRr, rule, custom_removing_rule)) {
                    nn_intervals.add(nextRr);
                } else {
                    // 当前区间超出规则边界：将下一个标记为 NaN
                    nn_intervals.add(Double.NaN);
                    outlierCount++;
                    previousOutlier = true;
                }
            }
        }

        if (verbose) {
            System.out.println(outlierCount + " ectopic beat(s) have been deleted with " + method + " rule.");
        }

        return nn_intervals;
    }

    /**
     * 使用默认参数调用 removeEctopicBeats（method="malik", custom_removing_rule=0.2, verbose=true）。
     *
     * @param rr_intervals RR 间期列表
     * @return 清洗后的列表
     */
    public static List<Double> removeEctopicBeats(List<Double> rr_intervals) {
        return removeEctopicBeats(rr_intervals, "malik", 0.2, true);
    }

    /**
     * 判断 RR 间期是否在指定规则的合法范围内（对应 Python is_rr_interval_within_bounds）。
     *
     * @param rr_interval      当前 RR 间期
     * @param next_rr_interval 下一个 RR 间期
     * @param rule             剔除规则
     * @param custom_rule      自定义规则百分比
     * @return true 若间期合法，false 若为异位心搏
     */
    public static boolean isRrIntervalWithinBounds(double rr_interval,
                                                    double next_rr_interval,
                                                    EctopicBeatRule rule,
                                                    double custom_rule) {
        switch (rule) {
            case MALIK:
                // |RRi - RRi+1| <= 0.2 * RRi
                return Math.abs(rr_interval - next_rr_interval) <= 0.2 * rr_interval;

            case KAMATH:
                // 非对称：0 <= next - curr <= 0.325 * curr 或 0 <= curr - next <= 0.245 * curr
                return (0 <= (next_rr_interval - rr_interval) &&
                        (next_rr_interval - rr_interval) <= 0.325 * rr_interval)
                        ||
                        (0 <= (rr_interval - next_rr_interval) &&
                        (rr_interval - next_rr_interval) <= 0.245 * rr_interval);

            case CUSTOM:
            default:
                // 自定义百分比：|RRi - RRi+1| <= custom_rule * RRi
                return Math.abs(rr_interval - next_rr_interval) <= custom_rule * rr_interval;
        }
    }

    /**
     * Karlsson 规则：当前点与前后两点均值差异超过阈值时标记为异位。
     * 对应 Python _remove_outlier_karlsson。
     *
     * <p>逻辑：mean_prev_next = (RRi + RRi+2) / 2
     * 若 |mean_prev_next - RRi+1| >= removing_rule * mean_prev_next，则 RRi+1 = NaN
     *
     * @param rr_intervals  RR 间期列表
     * @param removing_rule 偏差阈值（默认 0.2）
     * @return Object[]{List<Double> nn_intervals, int outlierCount}
     */
    private static Object[] removeOutlierKarlsson(List<Double> rr_intervals, double removing_rule) {
        List<Double> nn_intervals = new ArrayList<>();
        int outlierCount = 0;

        // 保留第一个元素
        nn_intervals.add(rr_intervals.get(0));

        for (int i = 0; i < rr_intervals.size(); i++) {
            // 在最后倒数第二个位置时，追加最后一个元素后退出循环
            if (i == rr_intervals.size() - 2) {
                nn_intervals.add(rr_intervals.get(i + 1));
                break;
            }
            // 计算前后均值：mean(RRi, RRi+2)
            double meanPrevNext = (rr_intervals.get(i) + rr_intervals.get(i + 2)) / 2.0;
            double current = rr_intervals.get(i + 1);
            // 对应 Python：abs(mean_prev_next_rri - rr_intervals[i+1]) < removing_rule * mean_prev_next_rri
            if (Math.abs(meanPrevNext - current) < removing_rule * meanPrevNext) {
                nn_intervals.add(current);
            } else {
                nn_intervals.add(Double.NaN);
                outlierCount++;
            }
        }

        return new Object[]{nn_intervals, outlierCount};
    }

    /**
     * Acar 规则：与前 9 个 NN 间期的 nanmean 比较，超出阈值则标记为异位。
     * 对应 Python _remove_outlier_acar。
     *
     * <p>前 9 个元素直接保留；从第 10 个开始，计算前 9 个元素 nanmean，
     * 若 |nanmean - RRi| >= custom_rule * nanmean，则 RRi = NaN。
     *
     * @param rr_intervals RR 间期列表
     * @param custom_rule  偏差阈值（默认 0.2）
     * @return Object[]{List<Double> nn_intervals, int outlierCount}
     */
    private static Object[] removeOutlierAcar(List<Double> rr_intervals, double custom_rule) {
        List<Double> nn_intervals = new ArrayList<>();
        int outlierCount = 0;

        for (int i = 0; i < rr_intervals.size(); i++) {
            if (i < 9) {
                // 前 9 个直接保留
                nn_intervals.add(rr_intervals.get(i));
                continue;
            }
            // 取前 9 个 NN 的 nanmean
            List<Double> last9 = nn_intervals.subList(nn_intervals.size() - 9, nn_intervals.size());
            double acarRuleElt = MathUtils.nanMean(last9);
            double rri = rr_intervals.get(i);
            // 对应 Python：abs(acar_rule_elt - rr_interval) < custom_rule * acar_rule_elt
            if (Math.abs(acarRuleElt - rri) < custom_rule * acarRuleElt) {
                nn_intervals.add(rri);
            } else {
                nn_intervals.add(Double.NaN);
                outlierCount++;
            }
        }

        return new Object[]{nn_intervals, outlierCount};
    }

    // ==================== interpolate_nan_values ====================

    /**
     * 对 NaN 值进行线性插值（对应 Python interpolate_nan_values）。
     *
     * <p>模拟 pandas Series.interpolate(method="linear", limit_area=None, limit_direction="forward") 的行为：
     * <ol>
     *   <li>若首元素为 NaN，先用第一个非 NaN 值填充开头连续 NaN</li>
     *   <li>对中间 NaN 进行线性插值</li>
     *   <li>结尾 NaN 在 forward 方向下不填充</li>
     * </ol>
     *
     * @param rr_intervals         RR 间期列表（可含 NaN）
     * @param interpolation_method 插值方法（当前支持 "linear"）
     * @param limit_area           NaN 填充范围限制（null/"inside"/"outside"）
     * @param limit_direction      填充方向（"forward"/"backward"/"both"）
     * @param limit                最大连续填充数量，null 为无限制
     * @return 插值后的列表
     */
    public static List<Double> interpolateNanValues(List<Double> rr_intervals,
                                                     String interpolation_method,
                                                     String limit_area,
                                                     String limit_direction,
                                                     Integer limit) {
        return InterpolationUtils.interpolateNanValues(rr_intervals, interpolation_method,
                limit_area, limit_direction, limit);
    }

    /**
     * 使用默认参数调用 interpolateNanValues（method="linear", limit_area=null, limit_direction="forward"）。
     *
     * @param rr_intervals RR 间期列表
     * @return 插值后的列表
     */
    public static List<Double> interpolateNanValues(List<Double> rr_intervals) {
        return InterpolationUtils.interpolateNanValues(rr_intervals);
    }

    // ==================== get_nn_intervals ====================

    /**
     * 完整 NN 间期预处理流水线（对应 Python get_nn_intervals）。
     *
     * <p>执行流程：
     * <ol>
     *   <li>removeOutliers：剔除超出生理范围的 RR 间期</li>
     *   <li>interpolateNanValues：对 NaN 进行线性插值</li>
     *   <li>removeEctopicBeats：剔除异位心搏</li>
     *   <li>interpolateNanValues：对 NaN 再次进行线性插值</li>
     * </ol>
     *
     * @param rr_intervals                 原始 RR 间期列表
     * @param low_rri                      最小合法 RR（ms），默认 300
     * @param high_rri                     最大合法 RR（ms），默认 2000
     * @param limit_area                   插值 limit_area 参数
     * @param limit_direction              插值方向参数
     * @param interpolation_method         插值方法
     * @param ectopic_beats_removal_method 异位心搏剔除规则名称
     * @param verbose                      是否打印信息
     * @return 完整清洗并插值后的 NN 间期列表
     */
    public static List<Double> getNnIntervals(List<Double> rr_intervals,
                                               int low_rri,
                                               int high_rri,
                                               String limit_area,
                                               String limit_direction,
                                               String interpolation_method,
                                               String ectopic_beats_removal_method,
                                               boolean verbose) {
        // 步骤1：剔除离群值
        List<Double> rr_intervals_cleaned = removeOutliers(rr_intervals, verbose, low_rri, high_rri);

        // 步骤2：插值 NaN
        List<Double> interpolated_rr = interpolateNanValues(rr_intervals_cleaned,
                interpolation_method, limit_area, limit_direction, null);

        // 步骤3：剔除异位心搏
        List<Double> nn_intervals = removeEctopicBeats(interpolated_rr,
                ectopic_beats_removal_method, 0.2, verbose);

        // 步骤4：再次插值
        List<Double> interpolated_nn = interpolateNanValues(nn_intervals,
                interpolation_method, limit_area, limit_direction, null);

        return interpolated_nn;
    }

    /**
     * 使用默认参数调用 getNnIntervals。
     * 默认：low_rri=300, high_rri=2000, interpolation_method="linear",
     * ectopic_beats_removal_method="kamath", verbose=true。
     *
     * @param rr_intervals 原始 RR 间期列表
     * @return 清洗后的 NN 间期列表
     */
    public static List<Double> getNnIntervals(List<Double> rr_intervals) {
        return getNnIntervals(rr_intervals, 300, 2000, null, "forward",
                "linear", "kamath", true);
    }

    // ==================== is_valid_sample ====================

    /**
     * 验证 NN 间期样本是否满足 HRV 分析的质量要求（对应 Python is_valid_sample）。
     *
     * <p>验证条件：
     * <ol>
     *   <li>异常值比例不超过 removing_rule（默认 4%）</li>
     *   <li>样本长度不少于 240 个心搏（Nyquist 准则）</li>
     * </ol>
     *
     * @param nn_intervals  NN 间期列表
     * @param outlier_count 已剔除的异常值数量
     * @param removing_rule 最大可接受异常比例（默认 0.04 = 4%）
     * @return true 若样本质量合格，false 否则
     */
    public static boolean isValidSample(List<Double> nn_intervals,
                                         int outlier_count,
                                         double removing_rule) {
        boolean result = true;
        if ((double) outlier_count / nn_intervals.size() > removing_rule) {
            System.out.println("Too much outlier for analyses ! You should discard the sample.");
            result = false;
        }
        if (nn_intervals.size() < 240) {
            System.out.println("Not enough Heart beat for Nyquist criteria !");
            result = false;
        }
        return result;
    }

    /**
     * 使用默认 removing_rule=0.04 调用 isValidSample。
     *
     * @param nn_intervals  NN 间期列表
     * @param outlier_count 已剔除的异常值数量
     * @return true 若样本合格
     */
    public static boolean isValidSample(List<Double> nn_intervals, int outlier_count) {
        return isValidSample(nn_intervals, outlier_count, 0.04);
    }
}
