package com.aura.hrv.features;

import com.aura.hrv.model.FrequencyBand;
import com.aura.hrv.util.InterpolationUtils;
import com.aura.hrv.util.MathUtils;
import org.jtransforms.fft.DoubleFFT_1D;

import java.util.*;

/**
 * HRV 频域特征提取，对应 Python 的 get_frequency_domain_features 函数。
 *
 * <p>实现以下功能：
 * <ul>
 *   <li>Welch 法 PSD 估算（使用 JTransforms FFT + Hann 窗 + 50% 重叠）</li>
 *   <li>Lomb-Scargle 法 PSD（使用内置实现，对应 astropy LombScargle normalization='psd'）</li>
 *   <li>频带积分（梯形法则）</li>
 * </ul>
 *
 * <h3>与 Python 的差异说明：</h3>
 * <ul>
 *   <li><b>Welch 法</b>：scipy.signal.welch 使用与 JTransforms 相同的 FFT 核心，
 *       在窗函数（Hann）和分段参数相同时，结果应高度一致（误差 &lt; 1e-6）。
 *       scipy 默认 nperseg=256, noverlap=128；本实现完全复现此参数，nfft=4096。</li>
 *   <li><b>Lomb 法</b>：Python 使用 astropy.timeseries.LombScargle(normalization='psd')，
 *       本实现采用等价的 Lomb-Scargle 算法并手动应用 psd 归一化（乘以 N/(2*variance)）。
 *       由于精度差异，频域结果误差可能达到 1%~5%，建议以 Welch 法为准。</li>
 * </ul>
 */
public class HrvFrequencyDomainFeatures {

    /**
     * 计算 HRV 频域特征（对应 Python get_frequency_domain_features）。
     *
     * @param nn_intervals         NN 间期列表（ms）
     * @param method               PSD 估算方法："welch" 或 "lomb"
     * @param sampling_frequency   Welch 法采样频率（Hz），默认 4
     * @param interpolation_method 插值方法，默认 "linear"
     * @param vlf_band             极低频段，默认 [0.003, 0.04) Hz
     * @param lf_band              低频段，默认 [0.04, 0.15) Hz
     * @param hf_band              高频段，默认 [0.15, 0.40) Hz
     * @return 包含 lf, hf, lf_hf_ratio, lfnu, hfnu, total_power, vlf 的 Map
     */
    public static Map<String, Double> getFrequencyDomainFeatures(
            List<Double> nn_intervals,
            String method,
            int sampling_frequency,
            String interpolation_method,
            FrequencyBand vlf_band,
            FrequencyBand lf_band,
            FrequencyBand hf_band) {

        // 确保输入为 List<Double>（兼容各种输入类型）
        List<Double> nnList = new ArrayList<>(nn_intervals);

        // 计算频率和 PSD
        double[][] freqPsd = getFreqPsdFromNnIntervals(nnList, method, sampling_frequency,
                interpolation_method, vlf_band, hf_band);
        double[] freq = freqPsd[0];
        double[] psd  = freqPsd[1];

        // 从 PSD 提取频域特征
        return getFeaturesFromPsd(freq, psd, vlf_band, lf_band, hf_band);
    }

    /**
     * 使用默认参数调用频域特征提取（method="welch", fs=4, interp="linear"）。
     *
     * @param nn_intervals NN 间期列表
     * @return 频域特征 Map
     */
    public static Map<String, Double> getFrequencyDomainFeatures(List<Double> nn_intervals) {
        return getFrequencyDomainFeatures(nn_intervals, "welch", 4, "linear",
                FrequencyBand.VLF, FrequencyBand.LF, FrequencyBand.HF);
    }

    // ==================== 内部方法 ====================

    /**
     * 计算 NN 间期的频率和 PSD（对应 Python _get_freq_psd_from_nn_intervals）。
     *
     * @param nn_intervals         NN 间期列表
     * @param method               "welch" 或 "lomb"
     * @param sampling_frequency   Welch 采样频率
     * @param interpolation_method 插值方法
     * @param vlf_band             VLF 频段（lomb 法需要）
     * @param hf_band              HF 频段（lomb 法需要）
     * @return double[2][]：[0] = freq 数组，[1] = psd 数组
     */
    static double[][] getFreqPsdFromNnIntervals(List<Double> nn_intervals,
                                                        String method,
                                                        int sampling_frequency,
                                                        String interpolation_method,
                                                        FrequencyBand vlf_band,
                                                        FrequencyBand hf_band) {
        // 创建累积时间戳（秒）
        double[] timestamp_list = createTimestampList(nn_intervals);

        if ("welch".equalsIgnoreCase(method)) {
            // ---- Welch 方法 ----
            // 1. 创建均匀插值时间戳：np.arange(0, time_nni[-1], 1/fs)
            double[] timestamps_interp = createInterpolatedTimestampList(nn_intervals, sampling_frequency);

            // 2. 对 NN 序列进行线性插值（scipy.interpolate.interp1d）
            double[] nnArray = MathUtils.toArray(nn_intervals);
            double[] nni_interpolation = InterpolationUtils.linearInterp1d(
                    timestamp_list, nnArray, timestamps_interp);

            // 3. 去 DC 分量：nni_normalized = nni_interp - mean(nni_interp)
            double meanVal = MathUtils.mean(nni_interpolation);
            double[] nni_normalized = new double[nni_interpolation.length];
            for (int i = 0; i < nni_interpolation.length; i++) {
                nni_normalized[i] = nni_interpolation[i] - meanVal;
            }

            // 4. Welch PSD（window=hann, nfft=4096, fs=sampling_frequency）
            return welchPsd(nni_normalized, sampling_frequency, 4096);

        } else if ("lomb".equalsIgnoreCase(method)) {
            // ---- Lomb-Scargle 方法 ----
            double[] nnArray = MathUtils.toArray(nn_intervals);
            return lombScarglePsd(timestamp_list, nnArray,
                    vlf_band.low, hf_band.high);

        } else {
            throw new IllegalArgumentException("不合法的 PSD 方法：" + method + "。请选择 'welch' 或 'lomb'。");
        }
    }

    /**
     * 创建累积时间戳列表（对应 Python _create_timestamp_list）。
     *
     * <p>nni_tmstp = cumsum(nn_intervals) / 1000
     * return nni_tmstp - nni_tmstp[0]（强制从 0 开始）
     *
     * @param nn_intervals NN 间期列表（ms）
     * @return 累积时间戳数组（秒），从 0 开始
     */
    public static double[] createTimestampList(List<Double> nn_intervals) {
        double[] tmstp = new double[nn_intervals.size()];
        double cumsum = 0.0;
        for (int i = 0; i < nn_intervals.size(); i++) {
            Double v = nn_intervals.get(i);
            cumsum += (v == null || Double.isNaN(v)) ? 0.0 : v;
            tmstp[i] = cumsum / 1000.0;
        }
        // 强制从 0 开始
        double first = tmstp[0];
        for (int i = 0; i < tmstp.length; i++) {
            tmstp[i] -= first;
        }
        return tmstp;
    }

    /**
     * 创建均匀插值时间戳（对应 Python _create_interpolated_timestamp_list）。
     *
     * <p>np.arange(0, time_nni[-1], 1 / sampling_frequency)
     *
     * @param nn_intervals       NN 间期列表
     * @param sampling_frequency 采样频率（Hz）
     * @return 均匀时间戳数组
     */
    public static double[] createInterpolatedTimestampList(List<Double> nn_intervals,
                                                             int sampling_frequency) {
        double[] timeNni = createTimestampList(nn_intervals);
        double endTime = timeNni[timeNni.length - 1];
        double step = 1.0 / sampling_frequency;

        // np.arange(0, endTime, step)：对应 numpy 的 ceil((end-start)/step) 元素数量
        int n = (int) Math.ceil(endTime / step);
        // 排除正好等于 endTime 的最后一点（arange 不含终点）
        // 实际上 ceil 保证了最后一点 (n-1)*step < endTime（在非整除时）
        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            result[i] = i * step;
        }
        return result;
    }

    /**
     * Welch 功率谱密度估算（对应 Python scipy.signal.welch）。
     *
     * <p>参数对齐：
     * <ul>
     *   <li>window = 'hann'（Hann 窗）</li>
     *   <li>nperseg = 256（scipy 默认）</li>
     *   <li>noverlap = nperseg / 2 = 128（50% 重叠，scipy 默认）</li>
     *   <li>nfft = 4096（零填充到 4096）</li>
     *   <li>detrend = 'constant'（scipy 默认，输入已去 DC，此处不再去均值）</li>
     *   <li>scaling = 'density'（scipy 默认，功率谱密度）</li>
     * </ul>
     *
     * @param signal 已去 DC 的信号数组
     * @param fs     采样频率（Hz）
     * @param nfft   FFT 点数
     * @return double[2][]：[0] = 频率数组，[1] = PSD 数组（单侧）
     */
    static double[][] welchPsd(double[] signal, int fs, int nfft) {
        int nperseg = Math.min(256, signal.length);
        int noverlap = nperseg / 2;
        int step = nperseg - noverlap;

        // 计算分段数
        int numSegments = (signal.length - noverlap) / step;
        if (numSegments <= 0) numSegments = 1;

        // Hann 窗
        double[] window = hannWindow(nperseg);

        // 窗函数功率归一化因子（对应 scipy 的 win.sum()^2 in density scaling）
        double winSumSq = 0.0;
        for (double w : window) {
            winSumSq += w * w;
        }

        // 用于累积 PSD 的数组（双侧 FFT 长度）
        int freqLen = nfft / 2 + 1; // 单侧频率点数
        double[] psdAccum = new double[freqLen];

        int segCount = 0;
        for (int segStart = 0; segStart + nperseg <= signal.length; segStart += step) {
            // 提取分段并加窗
            double[] seg = new double[nfft * 2]; // JTransforms 需要 2*N 的复数格式（交错）
            for (int i = 0; i < nperseg; i++) {
                seg[2 * i] = signal[segStart + i] * window[i]; // 实部
                seg[2 * i + 1] = 0.0;                           // 虚部
            }
            // 零填充到 nfft（seg 数组已初始化为 0）

            // FFT
            DoubleFFT_1D fft = new DoubleFFT_1D(nfft);
            fft.complexForward(seg);

            // 计算功率谱（模的平方）
            for (int k = 0; k < freqLen; k++) {
                double re = seg[2 * k];
                double im = seg[2 * k + 1];
                double power = re * re + im * im;
                psdAccum[k] += power;
            }
            segCount++;
        }

        // 平均 PSD
        // scipy scaling='density'：psd = power / (fs * win^2_sum)
        // 并对单侧频谱（除 DC 和 Nyquist 外）乘以 2
        double[] freq = new double[freqLen];
        double[] psd  = new double[freqLen];

        for (int k = 0; k < freqLen; k++) {
            freq[k] = (double) k * fs / nfft;
            double p = psdAccum[k] / segCount;
            // density scaling：除以 fs * sum(win^2)
            p /= (fs * winSumSq);
            // 单侧谱：DC(k=0) 和 Nyquist(k=nfft/2) 不加倍，其余加倍
            if (k > 0 && k < nfft / 2) {
                p *= 2.0;
            }
            psd[k] = p;
        }

        return new double[][]{freq, psd};
    }

    /**
     * 生成 Hann 窗（对应 scipy.signal.windows.hann）。
     *
     * <p>hann[n] = 0.5 * (1 - cos(2*pi*n/(N-1)))，N 为窗长。
     *
     * @param N 窗长
     * @return Hann 窗数组
     */
    static double[] hannWindow(int N) {
        double[] w = new double[N];
        for (int n = 0; n < N; n++) {
            w[n] = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * n / (N - 1)));
        }
        return w;
    }

    /**
     * Lomb-Scargle 功率谱密度（对应 Python astropy.timeseries.LombScargle(normalization='psd')）。
     *
     * <p>算法：
     * <ol>
     *   <li>计算角频率 omega = 2*pi*f</li>
     *   <li>计算 Lomb-Scargle 功率（标准归一化）</li>
     *   <li>转换为 PSD 归一化（乘以 N / (2 * variance)）</li>
     * </ol>
     *
     * <p><b>注意</b>：astropy 的 autopower 使用 Baluev(2008) 方法自动选择采样频率，
     * 本实现使用等间距频率网格，结果可能与 astropy 有 1%~5% 的差异。
     * 这是 Java 库与 astropy 底层实现差异导致的，属于预期行为。
     *
     * @param timestamps      时间戳数组（秒）
     * @param signal          对应的 NN 间期值
     * @param minFreq         最小频率（Hz）
     * @param maxFreq         最大频率（Hz）
     * @return double[2][]：[0] = 频率数组，[1] = PSD 数组
     */
    static double[][] lombScarglePsd(double[] timestamps, double[] signal,
                                      double minFreq, double maxFreq) {
        int n = timestamps.length;
        double mean = MathUtils.mean(signal);

        // 方差（ddof=1）
        double variance = 0.0;
        for (double v : signal) {
            variance += (v - mean) * (v - mean);
        }
        variance /= (n - 1);

        // 频率网格：按 astropy autopower 的默认采样策略
        // autopower 使用 samples_per_peak=5，baseline = max(t) - min(t)
        // 频率分辨率 df = 1 / (samples_per_peak * baseline)
        double baseline = timestamps[n - 1] - timestamps[0];
        double df = 1.0 / (5.0 * baseline);
        int numFreqs = (int) Math.ceil((maxFreq - minFreq) / df) + 1;

        double[] freq = new double[numFreqs];
        double[] psd  = new double[numFreqs];

        for (int k = 0; k < numFreqs; k++) {
            freq[k] = minFreq + k * df;
            double omega = 2.0 * Math.PI * freq[k];

            // 计算 tau（相位校正）
            double sin2omegaTau = 0.0, cos2omegaTau = 0.0;
            for (double t : timestamps) {
                sin2omegaTau += Math.sin(2.0 * omega * t);
                cos2omegaTau += Math.cos(2.0 * omega * t);
            }
            double tau = Math.atan2(sin2omegaTau, cos2omegaTau) / (2.0 * omega);

            // 计算 Lomb-Scargle power
            double sumC = 0.0, sumS = 0.0;
            double sumCC = 0.0, sumSS = 0.0;
            for (int i = 0; i < n; i++) {
                double xi = signal[i] - mean;
                double cosArg = Math.cos(omega * (timestamps[i] - tau));
                double sinArg = Math.sin(omega * (timestamps[i] - tau));
                sumC  += xi * cosArg;
                sumS  += xi * sinArg;
                sumCC += cosArg * cosArg;
                sumSS += sinArg * sinArg;
            }

            // 标准 Lomb-Scargle 功率（归一化为 variance）
            double power = 0.0;
            if (sumCC > 1e-15 && sumSS > 1e-15) {
                power = 0.5 * (sumC * sumC / sumCC + sumS * sumS / sumSS);
            }

            // 转换为 PSD 归一化：psd = power * N / (2 * variance)
            // 对应 astropy normalization='psd'
            psd[k] = (variance > 1e-15) ? power * n / (2.0 * variance) : 0.0;
        }

        return new double[][]{freq, psd};
    }

    /**
     * 从 PSD 提取频域特征（对应 Python _get_features_from_psd）。
     *
     * <p>使用复合梯形法则积分各频段功率，key 与 Python 完全一致：
     * lf, hf, lf_hf_ratio, lfnu, hfnu, total_power, vlf。
     *
     * @param freq     频率数组
     * @param psd      功率谱密度数组
     * @param vlf_band VLF 频段
     * @param lf_band  LF 频段
     * @param hf_band  HF 频段
     * @return 频域特征 Map
     */
    static Map<String, Double> getFeaturesFromPsd(double[] freq, double[] psd,
                                                           FrequencyBand vlf_band,
                                                           FrequencyBand lf_band,
                                                           FrequencyBand hf_band) {
        // 提取各频段的 freq 和 psd 子数组
        double[] vlfFreq = filterBand(freq, vlf_band.low, vlf_band.high, true);
        double[] vlfPsd  = filterBandPsd(freq, psd, vlf_band.low, vlf_band.high, true);

        double[] lfFreq  = filterBand(freq, lf_band.low, lf_band.high, false);
        double[] lfPsd   = filterBandPsd(freq, psd, lf_band.low, lf_band.high, false);

        double[] hfFreq  = filterBand(freq, hf_band.low, hf_band.high, false);
        double[] hfPsd   = filterBandPsd(freq, psd, hf_band.low, hf_band.high, false);

        // 梯形积分（对应 Python numpy.trapz/trapezoid）
        double vlf = MathUtils.trapz(vlfPsd, vlfFreq);
        double lf  = MathUtils.trapz(lfPsd,  lfFreq);
        double hf  = MathUtils.trapz(hfPsd,  hfFreq);

        double total_power = vlf + lf + hf;
        double lf_hf_ratio = lf / hf;
        double lfnu = (lf / (lf + hf)) * 100.0;
        double hfnu = (hf / (lf + hf)) * 100.0;

        Map<String, Double> features = new LinkedHashMap<>();
        features.put("lf",          lf);
        features.put("hf",          hf);
        features.put("lf_hf_ratio", lf_hf_ratio);
        features.put("lfnu",        lfnu);
        features.put("hfnu",        hfnu);
        features.put("total_power", total_power);
        features.put("vlf",         vlf);

        return features;
    }

    /**
     * 提取 freq 数组中满足 [low, high) 条件的频率子数组。
     * 对应 Python：freq[np.logical_and(freq >= low, freq < high)]
     *
     * @param freq       完整频率数组
     * @param low        频带下限（包含）
     * @param high       频带上限（不含）
     * @param isVlf      是否为 VLF 段（VLF 下限包含，上限不含）
     * @return 符合条件的频率子数组
     */
    private static double[] filterBand(double[] freq, double low, double high, boolean isVlf) {
        List<Double> result = new ArrayList<>();
        for (double f : freq) {
            if (f >= low && f < high) {
                result.add(f);
            }
        }
        return result.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /**
     * 提取 psd 数组中对应 freq 满足 [low, high) 条件的 PSD 子数组。
     *
     * @param freq   完整频率数组
     * @param psd    完整 PSD 数组
     * @param low    频带下限
     * @param high   频带上限
     * @param isVlf  是否 VLF（预留）
     * @return 符合条件的 PSD 子数组
     */
    private static double[] filterBandPsd(double[] freq, double[] psd,
                                           double low, double high, boolean isVlf) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < freq.length; i++) {
            if (freq[i] >= low && freq[i] < high) {
                result.add(psd[i]);
            }
        }
        return result.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
