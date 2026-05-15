package com.aura.hrv.features;

import com.aura.hrv.util.MathUtils;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 呼吸频率估算模块（HeartPy 算法完整实现）。
 *
 * <h3>完整算法流程（严格对应 HeartPy 内部逻辑）：</h3>
 * <pre>
 * Step 1  输入 RR interval（ms）
 * Step 2  构造累积时间轴：rr_x = cumsum(rr_list) / 1000  → 秒
 *         强制从 0 开始：rr_x = rr_x - rr_x[0]
 * Step 3  三次样条插值（cubic spline）：
 *         在均匀时间网格 t = [0, 1/fs, 2/fs, ...) 上重采样
 *         得到等间隔信号 rr_interp
 * Step 4  去 DC 分量：rr_normalized = rr_interp - mean(rr_interp)
 * Step 5  Welch PSD：freqs, psd = welch(rr_normalized, fs, window='hann', nfft=4096)
 * Step 6  呼吸频段掩膜：mask = (freqs >= 0.1) &amp; (freqs &lt;= 0.4)
 * Step 7  找主峰：breathingrate = freqs[argmax(psd[mask])]   （单位：Hz）
 * Step 8  换算：breathingratePerMinute = breathingrate × 60
 * </pre>
 *
 * <h3>与上一版本的差异：</h3>
 * <ul>
 *   <li>插值方式从线性插值改为<b>三次样条（cubic spline）</b>，对应 HeartPy/scipy.interpolate.CubicSpline</li>
 *   <li>完整在本文件内实现全流程（Step 1~8），不依赖 HrvFrequencyDomainFeatures 的参数传递</li>
 *   <li>复用包内 {@link HrvFrequencyDomainFeatures#welchPsd} 执行 FFT，不重复实现</li>
 * </ul>
 *
 * <p><b>注意</b>：本文件为独立新增功能，不修改任何已有 Java 文件。
 */
public class HrvBreathingRate {

    /** 呼吸频段下限（Hz）：对应 Python mask = (freqs >= 0.1) */
    private static final double BREATHING_FREQ_LOW  = 0.1;

    /** 呼吸频段上限（Hz）：对应 Python mask = (freqs <= 0.4) */
    private static final double BREATHING_FREQ_HIGH = 0.4;

    /** 默认重采样频率（Hz），与 HrvFrequencyDomainFeatures 保持一致 */
    private static final int DEFAULT_SAMPLING_FREQUENCY = 4;

    /** Welch FFT 点数，与 HrvFrequencyDomainFeatures 保持一致 */
    private static final int NFFT = 4096;

    // ==================== 公开 API ====================

    /**
     * 估算呼吸频率（默认参数：fs=4Hz，呼吸频段 0.1~0.4 Hz，三次样条插值）。
     *
     * @param nn_intervals 预处理后的 NN 间期列表（ms），NaN 会自动过滤
     * @return Map，包含：
     *         <ul>
     *           <li>{@code breathing_rate_hz} — 呼吸频率（Hz）</li>
     *           <li>{@code breathing_rate_per_minute} — 呼吸频率（次/分钟）</li>
     *         </ul>
     */
    public static Map<String, Double> getBreathingRate(List<Double> nn_intervals) {
        return getBreathingRate(nn_intervals, DEFAULT_SAMPLING_FREQUENCY,
                BREATHING_FREQ_LOW, BREATHING_FREQ_HIGH);
    }

    /**
     * 估算呼吸频率（自定义采样频率和频段）。
     *
     * @param nn_intervals       预处理后的 NN 间期列表（ms）
     * @param sampling_frequency Welch 重采样频率（Hz），通常取 4
     * @param freqLow            呼吸频段下限（Hz），通常取 0.1
     * @param freqHigh           呼吸频段上限（Hz），通常取 0.4
     * @return 包含 breathing_rate_hz 和 breathing_rate_per_minute 的 Map
     */
    public static Map<String, Double> getBreathingRate(List<Double> nn_intervals,
                                                        int sampling_frequency,
                                                        double freqLow,
                                                        double freqHigh) {
        // ---- Step 1：过滤 NaN，保证样条插值的输入干净 ----
        List<Double> cleanNn = new ArrayList<>();
        for (Double v : nn_intervals) {
            if (v != null && !Double.isNaN(v)) {
                cleanNn.add(v);
            }
        }
        if (cleanNn.size() < 3) {
            // 样条插值至少需要 3 个点
            Map<String, Double> r = new LinkedHashMap<>();
            r.put("breathing_rate_hz",         Double.NaN);
            r.put("breathing_rate_per_minute", Double.NaN);
            return r;
        }

        // ---- Step 2：构造累积时间轴（秒），强制从 0 开始 ----
        // 对应 Python：rr_x = cumsum(rr_list) / 1000; rr_x -= rr_x[0]
        double[] timestamps = buildTimestamps(cleanNn);

        // ---- Step 3：构造均匀插值时间网格 ----
        // 对应 Python：np.arange(0, rr_x[-1], 1/fs)
        double endTime = timestamps[timestamps.length - 1];
        double step    = 1.0 / sampling_frequency;
        int n          = (int) Math.ceil(endTime / step);
        double[] interpTimestamps = new double[n];
        for (int i = 0; i < n; i++) {
            interpTimestamps[i] = i * step;
        }

        // ---- Step 4：三次样条插值（cubic spline）----
        // 对应 HeartPy / scipy.interpolate.CubicSpline
        double[] nnArray   = MathUtils.toArray(cleanNn);
        double[] rr_interp = cubicSplineInterp(timestamps, nnArray, interpTimestamps);

        // ---- Step 5：去 DC 分量 ----
        // 对应 Python：nni_normalized = nni_interp - mean(nni_interp)
        double mean = MathUtils.mean(rr_interp);
        double[] rr_normalized = new double[rr_interp.length];
        for (int i = 0; i < rr_interp.length; i++) {
            rr_normalized[i] = rr_interp[i] - mean;
        }

        // ---- Step 6：Welch PSD（复用已有实现，window=hann, nfft=4096）----
        // 对应 Python：scipy.signal.welch(rr_normalized, fs=sampling_frequency, window='hann', nfft=4096)
        double[][] freqPsd = HrvFrequencyDomainFeatures.welchPsd(rr_normalized, sampling_frequency, NFFT);
        double[] freqs = freqPsd[0];
        double[] psd   = freqPsd[1];

        // ---- Step 7：呼吸频段掩膜 + argmax ----
        // 对应 Python：
        //   mask = (freqs >= 0.1) & (freqs <= 0.4)
        //   breathingrate = freqs[np.argmax(psd[mask])]
        double maxPsd          = Double.NEGATIVE_INFINITY;
        double breathingRateHz = Double.NaN;
        for (int i = 0; i < freqs.length; i++) {
            if (freqs[i] >= freqLow && freqs[i] <= freqHigh) {
                if (psd[i] > maxPsd) {
                    maxPsd          = psd[i];
                    breathingRateHz = freqs[i];
                }
            }
        }

        // ---- Step 8：换算为次/分钟 ----
        // 对应 Python：breathingratePerMinute = breathingrate * 60
        double breathingRatePerMinute = Double.isNaN(breathingRateHz)
                ? Double.NaN
                : breathingRateHz * 60.0;

        Map<String, Double> result = new LinkedHashMap<>();
        result.put("breathing_rate_hz",         breathingRateHz);
        result.put("breathing_rate_per_minute", breathingRatePerMinute);
        return result;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 构造累积时间轴（秒），从 0 开始。
     * 对应 Python：rr_x = cumsum(rr_list) / 1000; rr_x -= rr_x[0]
     *
     * @param nn 干净的 NN 间期列表（无 NaN）
     * @return 时间戳数组（秒）
     */
    private static double[] buildTimestamps(List<Double> nn) {
        double[] t = new double[nn.size()];
        double cumsum = 0.0;
        for (int i = 0; i < nn.size(); i++) {
            cumsum += nn.get(i);
            t[i] = cumsum / 1000.0;
        }
        // 强制从 0 开始
        double first = t[0];
        for (int i = 0; i < t.length; i++) {
            t[i] -= first;
        }
        return t;
    }

    /**
     * 三次样条插值（cubic spline），在 newX 处求值。
     * 使用 Apache Commons Math SplineInterpolator，等价于 scipy.interpolate.CubicSpline
     * 和 HeartPy 内部 scipy.interpolate.interp1d(kind='cubic')。
     *
     * @param x    原始时间戳（严格递增，无重复）
     * @param y    对应的 NN 间期值
     * @param newX 目标均匀时间网格
     * @return 在 newX 处的插值结果
     */
    private static double[] cubicSplineInterp(double[] x, double[] y, double[] newX) {
        SplineInterpolator interpolator = new SplineInterpolator();
        PolynomialSplineFunction spline = interpolator.interpolate(x, y);

        double xMin = x[0];
        double xMax = x[x.length - 1];

        double[] result = new double[newX.length];
        for (int i = 0; i < newX.length; i++) {
            double t = newX[i];
            // 边界截断（等价于 scipy 的 extrapolation 默认行为：clamp）
            if (t < xMin) t = xMin;
            if (t > xMax) t = xMax;
            result[i] = spline.value(t);
        }
        return result;
    }
}
