package com.aura.hrv.features;

import com.aura.hrv.model.FrequencyBand;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HRV 全量分析门面类（Facade）。
 *
 * <p>一次调用即可获得所有 HRV 特征 + 呼吸频率，无需分别调用各子模块。
 * 内部按固定顺序调用所有已有模块，不包含任何新算法逻辑。
 *
 * <p><b>各子模块调用顺序：</b>
 * <ol>
 *   <li>{@link HrvTimeDomainFeatures#getTimeDomainFeatures}      — 16 个时域特征</li>
 *   <li>{@link HrvGeometricalFeatures#getGeometricalFeatures}    — 几何特征</li>
 *   <li>{@link HrvFrequencyDomainFeatures#getFrequencyDomainFeatures} — 频域特征</li>
 *   <li>{@link HrvNonLinearFeatures#getPoincarePlotFeatures}     — Poincaré 特征</li>
 *   <li>{@link HrvNonLinearFeatures#getCsiCviFeatures}           — CSI/CVI 特征</li>
 *   <li>{@link HrvNonLinearFeatures#getSampen}                   — 样本熵</li>
 *   <li>{@link HrvBreathingRate#getBreathingRate}                — 呼吸频率（自动包含）</li>
 * </ol>
 *
 * <p><b>注意：</b>本类不修改任何已有文件，不引入任何新算法。
 * 所有输出 key 与各子模块完全一致，可直接互换使用。
 */
public class HrvFullAnalysis {

    /**
     * 使用默认参数对 NN 间期执行全量 HRV 分析。
     *
     * <p>默认参数：
     * <ul>
     *   <li>pnni_as_percent = true</li>
     *   <li>频域 method = "welch", sampling_frequency = 4, interpolation_method = "linear"</li>
     *   <li>vlf_band = [0.003, 0.04), lf_band = [0.04, 0.15), hf_band = [0.15, 0.40)</li>
     *   <li>呼吸频段 = [0.1, 0.4] Hz</li>
     * </ul>
     *
     * @param nn_intervals 预处理后的 NN 间期列表（ms）
     * @return 所有 HRV 特征 + 呼吸频率的合并 Map，key 与各子模块完全一致
     */
    public static Map<String, Object> analyze(List<Double> nn_intervals) {
        return analyze(nn_intervals, true, "welch", 4, "linear",
                FrequencyBand.VLF, FrequencyBand.LF, FrequencyBand.HF,
                0.1, 0.4);
    }

    /**
     * 全量 HRV 分析（支持自定义参数）。
     *
     * @param nn_intervals         预处理后的 NN 间期列表（ms）
     * @param pnni_as_percent      是否用 len-1 作 pnni 分母（默认 true）
     * @param method               频域 PSD 方法："welch" 或 "lomb"
     * @param sampling_frequency   Welch 采样频率（Hz），默认 4
     * @param interpolation_method 频域插值方法，默认 "linear"
     * @param vlf_band             VLF 频段
     * @param lf_band              LF 频段
     * @param hf_band              HF 频段
     * @param breathingFreqLow     呼吸频段下限（Hz），默认 0.1
     * @param breathingFreqHigh    呼吸频段上限（Hz），默认 0.4
     * @return 所有 HRV 特征 + 呼吸频率的合并 Map
     */
    public static Map<String, Object> analyze(List<Double> nn_intervals,
                                               boolean pnni_as_percent,
                                               String method,
                                               int sampling_frequency,
                                               String interpolation_method,
                                               FrequencyBand vlf_band,
                                               FrequencyBand lf_band,
                                               FrequencyBand hf_band,
                                               double breathingFreqLow,
                                               double breathingFreqHigh) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 时域特征（16 个）
        Map<String, Double> td = HrvTimeDomainFeatures.getTimeDomainFeatures(
                nn_intervals, pnni_as_percent);
        result.putAll(td);

        // 2. 几何特征
        Map<String, Object> geo = HrvGeometricalFeatures.getGeometricalFeatures(nn_intervals);
        result.putAll(geo);

        // 3. 频域特征（Welch / Lomb）
        Map<String, Double> freq = HrvFrequencyDomainFeatures.getFrequencyDomainFeatures(
                nn_intervals, method, sampling_frequency, interpolation_method,
                vlf_band, lf_band, hf_band);
        result.putAll(freq);

        // 4. Poincaré 散点图特征
        Map<String, Double> poincare = HrvNonLinearFeatures.getPoincarePlotFeatures(nn_intervals);
        result.putAll(poincare);

        // 5. CSI/CVI 特征
        Map<String, Double> csiCvi = HrvNonLinearFeatures.getCsiCviFeatures(nn_intervals);
        result.putAll(csiCvi);

        // 6. 样本熵
        Map<String, Double> sampen = HrvNonLinearFeatures.getSampen(nn_intervals);
        result.putAll(sampen);

        // 7. 呼吸频率（自动包含，无需单独调用）
        Map<String, Double> br = HrvBreathingRate.getBreathingRate(
                nn_intervals, sampling_frequency, breathingFreqLow, breathingFreqHigh);
        result.putAll(br);

        return result;
    }
}
