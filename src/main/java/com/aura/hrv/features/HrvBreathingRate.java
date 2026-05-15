package com.aura.hrv.features;

import com.aura.hrv.model.FrequencyBand;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 呼吸频率估算模块。
 *
 * <p>算法来源（Python 参考实现）：
 * <pre>
 *   freqs, psd = welch(rr_interp)
 *   mask = (freqs >= 0.1) &amp; (freqs &lt;= 0.4)
 *   breathing_freqs = freqs[mask]
 *   breathing_psd   = psd[mask]
 *   breathingrate   = breathing_freqs[np.argmax(breathing_psd)]   # Hz
 *   breathingratePerMinute = breathingrate * 60
 * </pre>
 *
 * <p>原理：呼吸活动在 HRV 的功率谱中表现为 0.1~0.4 Hz 区间内的功率峰值（RSA，
 * 呼吸性窦性心律不齐）。在该频段内找到 PSD 最大值对应的频率，即为估算的呼吸频率。
 *
 * <p><b>调用方式</b>：直接传入 NN 间期列表，内部复用
 * {@link HrvFrequencyDomainFeatures#getFreqPsdFromNnIntervals} 计算 Welch PSD，
 * 不重复执行插值或 FFT。
 *
 * <p><b>注意</b>：本文件为新增功能，不修改任何已有 Java 文件。
 */
public class HrvBreathingRate {

    /**
     * 呼吸频率估算的默认频段下限（Hz）。
     * 对应 Python：freqs >= 0.1
     */
    private static final double BREATHING_FREQ_LOW  = 0.1;

    /**
     * 呼吸频率估算的默认频段上限（Hz）。
     * 对应 Python：freqs <= 0.4
     */
    private static final double BREATHING_FREQ_HIGH = 0.4;

    /**
     * 估算呼吸频率（使用默认参数：Welch 法，采样率 4 Hz，呼吸频段 0.1~0.4 Hz）。
     *
     * @param nn_intervals NN 间期列表（ms），应为经过预处理的干净序列
     * @return 包含以下两个键的 Map：
     *         <ul>
     *           <li>{@code breathing_rate_hz} — 呼吸频率（Hz）</li>
     *           <li>{@code breathing_rate_per_minute} — 呼吸频率（次/分钟）</li>
     *         </ul>
     */
    public static Map<String, Double> getBreathingRate(List<Double> nn_intervals) {
        return getBreathingRate(nn_intervals, 4, "linear", BREATHING_FREQ_LOW, BREATHING_FREQ_HIGH);
    }

    /**
     * 估算呼吸频率（支持自定义参数）。
     *
     * <p>步骤：
     * <ol>
     *   <li>调用 {@link HrvFrequencyDomainFeatures#getFreqPsdFromNnIntervals} 获取 Welch PSD
     *       （复用已有实现，不重复计算）</li>
     *   <li>在 [{@code freqLow}, {@code freqHigh}] 范围内筛选频率点
     *       （对应 Python mask = (freqs >= 0.1) &amp; (freqs &lt;= 0.4)）</li>
     *   <li>找到该范围内 PSD 最大值对应的频率（argmax）</li>
     *   <li>乘以 60 转换为次/分钟</li>
     * </ol>
     *
     * @param nn_intervals         NN 间期列表（ms）
     * @param sampling_frequency   Welch 采样频率（Hz），默认 4
     * @param interpolation_method 插值方法，默认 "linear"
     * @param freqLow              呼吸频段下限（Hz），默认 0.1
     * @param freqHigh             呼吸频段上限（Hz），默认 0.4
     * @return 包含 breathing_rate_hz 和 breathing_rate_per_minute 的 Map
     */
    public static Map<String, Double> getBreathingRate(List<Double> nn_intervals,
                                                        int sampling_frequency,
                                                        String interpolation_method,
                                                        double freqLow,
                                                        double freqHigh) {
        // 复用已有 Welch PSD 计算（不重复实现，不修改原文件）
        // vlf_band / hf_band 仅影响 lomb 方法，welch 方法中只用于 lomb 分支，此处传入覆盖全范围
        double[][] freqPsd = HrvFrequencyDomainFeatures.getFreqPsdFromNnIntervals(
                nn_intervals,
                "welch",
                sampling_frequency,
                interpolation_method,
                new FrequencyBand(freqLow, freqHigh),   // vlf_band（welch 方法不使用）
                new FrequencyBand(freqLow, freqHigh)    // hf_band（welch 方法不使用）
        );

        double[] freqs = freqPsd[0];
        double[] psd   = freqPsd[1];

        // 对应 Python：mask = (freqs >= freqLow) & (freqs <= freqHigh)
        // 在呼吸频段内找 PSD 最大值的频率（argmax）
        double maxPsd          = Double.NEGATIVE_INFINITY;
        double breathingRateHz = Double.NaN;

        for (int i = 0; i < freqs.length; i++) {
            if (freqs[i] >= freqLow && freqs[i] <= freqHigh) {
                if (psd[i] > maxPsd) {
                    maxPsd          = psd[i];
                    breathingRateHz = freqs[i];   // 对应 Python：breathing_freqs[np.argmax(psd)]
                }
            }
        }

        // 对应 Python：breathingratePerMinute = breathingrate * 60
        double breathingRatePerMinute = Double.isNaN(breathingRateHz)
                ? Double.NaN
                : breathingRateHz * 60.0;

        Map<String, Double> result = new LinkedHashMap<>();
        result.put("breathing_rate_hz",         breathingRateHz);
        result.put("breathing_rate_per_minute", breathingRatePerMinute);
        return result;
    }
}
